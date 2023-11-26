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
package com.google.mediapipe.examples.handsign_translator

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.examples.handsign_translator.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer


class HandLandmarkerHelper(
    private val gestureModelPath: String = "model25.tflite",
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val handLandmarkerHelperListener: LandmarkerListener? = null,

) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Hand Landmarker will not change, a lazy val would be preferable.
    private var handLandmarker: HandLandmarker? = null

    private var gestureClassifier: Interpreter? = null


    init {
        setupHandLandmarker()
        initializeGestureModel()
    }

    private fun initializeGestureModel(){

        try {
            // Create an Interpreter instance
            // var interpreter: Interpreter? = null
            gestureClassifier = Interpreter(loadModelFile(gestureModelPath))
        } catch (e: Exception) {
            // Handle exceptions during model instantiation
            Log.e("ModelInfo", "Error initializing the model: ${e.message}")
        }
    }

    // Load the TensorFlow Lite model file from the assets folder
    private fun loadModelFile(modelPath: String): ByteBuffer {
        try {
            // Open the model file from the assets folder
            val inputStream = context.assets.open(modelPath)
            val modelBytes = inputStream.readBytes()

            // Create a direct ByteBuffer and copy the model bytes into it
            val buffer = ByteBuffer.allocateDirect(modelBytes.size)
            buffer.order(ByteOrder.nativeOrder())
            buffer.put(modelBytes)
            buffer.rewind()

            return buffer
        } catch (e: Exception) {
            throw RuntimeException("Error reading TensorFlow Lite model file: ${e.message}")
        }
    }

    private val jointAngles = listOf(listOf(0,1,2), listOf(1,2,3), listOf(2,3,4), listOf(1,0,5),
        listOf(0,5,6), listOf(5,6,7), listOf(6,7,8), listOf(6,5,9),
        listOf(5,9,10), listOf(0,9,10), listOf(9,10,11), listOf(10,11,12),
        listOf(10,9,13), listOf(9,13,14), listOf(0,13,14), listOf(13,14,15),
        listOf(14,15,16), listOf(14,13,17), listOf(13,17,18), listOf(0,17,18),
        listOf(17,18,19), listOf(18,19,20))

    private val seqLength = 10

    private val alphabet = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z')

    private val tenframes: MutableList<MutableList<Double>> =  mutableListOf(mutableListOf())

    private fun convertDoubleListToFloat(originalList: MutableList<MutableList<Double>>): MutableList<MutableList<Float>> {
        val floatList: MutableList<MutableList<Float>> = mutableListOf()

        for (innerList in originalList) {
            val floatInnerList = innerList.map { it.toFloat() }.toMutableList()
            floatList.add(floatInnerList)
        }

        return floatList
    }

    fun listToString(list: List<FloatArray>): String {
        val stringBuilder = StringBuilder("[\n")

        for (floatArray in list) {
            stringBuilder.append("  [")
            for (i in floatArray.indices) {
                stringBuilder.append(floatArray[i])
                if (i < floatArray.size - 1) {
                    stringBuilder.append(", ")
                }
            }
            stringBuilder.append("]\n")
        }

        stringBuilder.append("]")

        return stringBuilder.toString()
    }

    fun convertToTensorBuffer(inputArray: Array<Array<Array<Float>>>): TensorBuffer {
        // Flatten the nested array
        val flattenedArray = inputArray.flatMap { it.flatMap { it.asIterable() } }.toFloatArray()
        //Log.d("Print arary", arrayToString(inputArray))

        // Create a ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(flattenedArray.size * 4) // 4 bytes for each float
        byteBuffer.order(ByteOrder.nativeOrder())

        // Put the flattened array into the ByteBuffer
        for (floatValue in flattenedArray) {
            byteBuffer.putFloat(floatValue)
        }

        // Create a TensorBuffer with the expected shape [1, 10, 85] and data type FLOAT32
        val shape = intArrayOf(1, 10, 85)
        val dataType = DataType.FLOAT32
        val tensorBuffer = TensorBuffer.createFixedSize(shape, dataType)

        // Copy data from the ByteBuffer to the TensorBuffer
        tensorBuffer.loadBuffer(byteBuffer)

        return tensorBuffer
    }


    fun arrayToString(array: Array<FloatArray>): String {
        val stringBuilder = StringBuilder("[\n")

        for (floatArray in array) {
            stringBuilder.append("  [")
            for (i in floatArray.indices) {
                stringBuilder.append(floatArray[i])
                if (i < floatArray.size - 1) {
                    stringBuilder.append(", ")
                }
            }
            stringBuilder.append("]\n")
        }

        stringBuilder.append("]")

        return stringBuilder.toString()
    }

    private fun classifyGestures(handLandmarks: MutableList<MutableList<NormalizedLandmark>>): String {
        val startProcessingTime = SystemClock.elapsedRealtime()
        val inputTensor = gestureClassifier!!.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val inputDataType = inputTensor.dataType()

        var predictedAlphabet: String = ""

        // Print the input tensor information to logcat
        //Log.d("ModelInfo", "Input Tensor Shape: ${inputShape.contentToString()}")
        //Log.d("ModelInfo", "Input Tensor Data Type: $inputDataType")

        // landmarks coordinates for each frame
        // [[0.x, 0.y, 0.z], [1.x, 1.y, 1.z]...]
        val coordinateList = mutableListOf<DoubleArray>()
        //Log.d(TAG, handLandmarks.size.toString())

        for (landmark in handLandmarks){
            for (normalizedLandmark in landmark){
                val landmarkCoordinates = doubleArrayOf(
                    normalizedLandmark.x().toDouble(),
                    normalizedLandmark.y().toDouble(),
                    normalizedLandmark.z().toDouble())
                coordinateList.add(landmarkCoordinates)
            }
        }

        val coordinateFlattened = coordinateList.flatMap { it.toList() }.toMutableList()

        // store calculated angles for all joint sets
        val angles = mutableListOf<Double>()

        // compute joint angales and store in angles
        if (coordinateList.size == 21) {
            for (joint in jointAngles) {
                val a = doubleArrayOf(coordinateList[joint[0]][0], coordinateList[joint[0]][1])
                val b = doubleArrayOf(coordinateList[joint[1]][0], coordinateList[joint[1]][1])
                val c = doubleArrayOf(coordinateList[joint[2]][0], coordinateList[joint[2]][1])

                val radians =
                    atan2(c[1] - b[1], c[0] - b[0]) - atan2(a[1] - b[1], a[0] - b[0])
                var angle = abs(radians * 180.0 / Math.PI)

                if (angle > 180.0) {
                    angle = 360 - angle
                }
                angles.add(angle)
            }
        }

        // combine joint coordinates with angles in single frame
        val oneframe = mutableListOf<Double>()

        oneframe.addAll(coordinateFlattened)
        oneframe.addAll(angles)

        if (oneframe.size == 85) {
            tenframes.add(oneframe)
        }


        if (tenframes.size < seqLength){
            return ""
        } else if (tenframes.size == seqLength) {

            val tenframeList: List<List<Float>> = convertDoubleListToFloat(tenframes).map { it.toList() }
            val inputArray = listOf(tenframeList)

            val nestedArray: Array<Array<Array<Float>>> =
                inputArray.map { outerList ->
                    outerList.map { innerList ->
                        innerList.toTypedArray()
                    }.toTypedArray()
                }.toTypedArray()

            if (nestedArray[0][0].size == 85) {
                val outputArray = Array(1) { FloatArray(26) }
                val tensorBuffer = convertToTensorBuffer(nestedArray)

                try {
                    val shape1 = tensorBuffer.shape
                    //Log.d("tensorbuffer", "Tensor Shape ${shape1.contentToString()}")
                    val datatype1 = tensorBuffer.dataType
                    //Log.d("tensorbuffer", "Tensor Data Type: $datatype1")
                    gestureClassifier?.run(tensorBuffer.buffer, outputArray)

                    var maxIndex = 0
                    val probDist = outputArray[0]
                    var maxValue = 0f
                    for (i in probDist.indices) {
                        if (probDist[i] > maxValue) {
                            maxValue = probDist[i]
                            maxIndex = i
                        }
                    }

                    val predictedClassIndex = maxIndex
                    predictedAlphabet = alphabet[predictedClassIndex].toString()
                    Log.d("Predicted Class", predictedAlphabet)

                } catch (e:Exception) {
                    Log.e("ClassificationError", "Error during classification: ${e.message}")
                }
            }
        }

        tenframes.clear()

        val endProcessingTime = SystemClock.elapsedRealtime()
        //Log.d(TAG, "Processing time: ${endProcessingTime - startProcessingTime} ms")

        return predictedAlphabet
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    // Return running status of HandLandmarkerHelper
    fun isClose(): Boolean {
        return handLandmarker == null
    }

    // Initialize the Hand landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupHandLandmarker() {
        // Set general hand landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        // Check if runningMode is consistent with handLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (handLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "handLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Hand Landmarker.
            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setNumHands(maxNumHands)
                    .setRunningMode(runningMode)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            handLandmarker =
                HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to HandlandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()
        val startProcessingTime = SystemClock.elapsedRealtime()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)

        val endProcessingTime = SystemClock.elapsedRealtime()
        //Log.d(TAG, "Total processing time: ${endProcessingTime - startProcessingTime} ms")
    }

    // Run hand hand landmark using MediaPipe Hand Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        handLandmarker?.detectAsync(mpImage, frameTime)
        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }

    // Return the landmark result to this HandLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        handLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                classifyGestures(result.landmarks())
            )
        )
    }

    // Return errors thrown during detection to this HandLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        handLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val gestures: String
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}