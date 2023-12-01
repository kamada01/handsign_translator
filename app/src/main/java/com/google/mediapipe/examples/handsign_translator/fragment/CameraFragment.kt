/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.handsign_translator.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.handsign_translator.HandLandmarkerHelper
import com.google.mediapipe.examples.handsign_translator.MainActivity
import com.google.mediapipe.examples.handsign_translator.MainViewModel
import com.google.mediapipe.examples.handsign_translator.R
import com.google.mediapipe.examples.handsign_translator.SharedState
import com.google.mediapipe.examples.handsign_translator.SpellChecker
import com.google.mediapipe.examples.handsign_translator.Word
import com.google.mediapipe.examples.handsign_translator.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener{

    companion object {
        private const val TAG = "Hand Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private var mainActivity : MainActivity? = null
    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onStart() {
        super.onStart()
        mainActivity = activity as MainActivity
    }
    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the HandLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (handLandmarkerHelper.isClose()) {
                handLandmarkerHelper.setupHandLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::handLandmarkerHelper.isInitialized) {
            viewModel.setMaxHands(handLandmarkerHelper.maxNumHands)
            viewModel.setMinHandDetectionConfidence(handLandmarkerHelper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(handLandmarkerHelper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(handLandmarkerHelper.minHandPresenceConfidence)
            viewModel.setDelegate(handLandmarkerHelper.currentDelegate)

            // Close the HandLandmarkerHelper and release resources
            backgroundExecutor.execute { handLandmarkerHelper.clearHandLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainActivity = activity as MainActivity
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the HandLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                maxNumHands = viewModel.currentMaxHands,
                currentDelegate = viewModel.currentDelegate,
                handLandmarkerHelperListener = this,
            )
        }
    }


    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectHand(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after hand have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView

    private fun generatePermutations(word: String, pairings: Map<Char, List<Char>>): () -> List<String> {
        val permutations = mutableListOf<String>()

        fun permute(current: String, index: Int) {
            if (index == word.length) {
                permutations.add(current)
                return
            }
            val currentChar = word[index]
            val options = pairings[currentChar] ?: listOf(currentChar)

            for (option in options) {
                permute(current + option, index + 1)
            }
        }

        return { permute("", 0); permutations }
    }

    private var text = ""
    private fun getWord(prediction : String): Pair<String,Boolean> {
        val suggestion = mainActivity?.spellChecker?.suggest(prediction,1)

        return if (suggestion.isNullOrEmpty()) {
            Pair(prediction,false)
        } else {
            Pair(suggestion[0],true)
        }
    }
    private fun numberOfChanges(reference: String, new: String): Int {
        var counter = 0
        for (i in reference.indices) {
            if ( i < new.length) {
                if (reference[i] != new[i]) {
                    counter += 1
                }
            } else {
                counter += 1
            }
        }
        return counter
    }
//    var stored : MutableList<String> = mutableListOf()
//    private var startProcessingTime = SystemClock.elapsedRealtime()
    // denotes common confused
    private val pairings = mapOf(
        'm' to listOf('m', 'n'),
        'n' to listOf('m', 'n'),
        'e' to listOf('e', 'o'),
        'o' to listOf('e', 'o'),
        'v' to listOf('v', 'k'),
        'k' to listOf('v', 'k'),
        'k' to listOf('h', 'k'),
        'h' to listOf('h', 'k'),
    )
    private var haveWord = false
    override fun onResults( resultBundle: HandLandmarkerHelper.ResultBundle ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                val resultsTextView = activity?.findViewById<TextView>(R.id.resultTextView)
                val startButton = activity?.findViewById<Button>(R.id.start_button)
                if (SharedState.buttonState == 1) {
                    if (text == "") {
                        text += resultBundle.gestures
                    } else if(text[text.length - 1].toString() != resultBundle.gestures) {
                        text += resultBundle.gestures
                    }
                    resultsTextView?.text = text
                    startButton?.text = "Stop"
                } else if (SharedState.buttonState == 0){
                    text = ""
                    haveWord = false
                    resultsTextView?.text = "Press Button"
                    startButton?.text = "Translate"
                } else if (SharedState.buttonState == 2){
                    if (!haveWord) {
                        var correctedWord = text
                        if (text.length <= 7) {
                            val permutations = generatePermutations(text, pairings)()
                            val listOfWords : MutableList<Word> = mutableListOf()
                            for (permutation in permutations) {
                                var (word, valid) = getWord(permutation)
                                if (valid) {
                                    if (permutation == word) {
                                        listOfWords.add(Word(word, 0))
                                    } else {
                                        listOfWords.add(Word(word,numberOfChanges(text,word)))
                                    }
                                }
                            }
                            if (listOfWords.size > 0) {
                                listOfWords.sortBy { it.counter }
                                correctedWord = listOfWords[0].word
                            }
                        }
                        resultsTextView?.text = correctedWord
                        startButton?.text = "Again"
                        haveWord = true
                    }
                }
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == HandLandmarkerHelper.GPU_ERROR) {
            }
        }
    }
}
