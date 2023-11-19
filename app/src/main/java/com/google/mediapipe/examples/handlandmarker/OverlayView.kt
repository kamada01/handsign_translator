package com.google.mediapipe.examples.handlandmarker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
//    var textView: TextView = findViewById(R.id.textView)
    private fun createORTSession( ortEnvironment: OrtEnvironment ) : OrtSession {
        val modelBytes = resources.openRawResource( R.raw.sklearn_model ).readBytes()
        return ortEnvironment.createSession( modelBytes )
    }
    val ortEnvironment = OrtEnvironment.getEnvironment()
    val ortSession = createORTSession( ortEnvironment )
    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }
    fun clamp(coordinate:Float): Float {
        if (coordinate < 0) return 0.0F
        if (coordinate > 1) return 1.0F
        return coordinate
    }
    fun runPrediction(inputs: List<Float>, ortSession: OrtSession, ortEnvironment: OrtEnvironment): String {
        // Ensure that exactly 42 floats are passed
        if (inputs.size != 42) {
            throw IllegalArgumentException("Expected 42 inputs, but got ${inputs.size}")
        }

        // Get the name of the input node
        val inputName = ortSession.inputNames.iterator().next()

        // Create input tensor with floatBufferInputs of shape (1, 42)
        val floatBufferInputs = FloatBuffer.wrap(inputs.toFloatArray())
        val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBufferInputs, longArrayOf(1, 42))

        // Run the model and handle output
        val outputTensor: Array<String>
        inputTensor.use { tensor ->
            val results = ortSession.run(mapOf(inputName to tensor))
            outputTensor = results[0].value as Array<String>
        }
        return outputTensor[0]
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { handLandmarkerResult ->
            for (landmark in handLandmarkerResult.landmarks()) {
                var hand_coordinates = ArrayList<Float>()
                for (normalizedLandmark in landmark) {
                    var normalized_x = clamp(normalizedLandmark.x())
                    var normalized_y = clamp(normalizedLandmark.y())
                    hand_coordinates.add(normalized_x)
                    hand_coordinates.add(normalized_y)
                    canvas.drawPoint(
                        normalized_x * imageWidth * scaleFactor,
                        normalized_y * imageHeight * scaleFactor,
                        pointPaint
                    )
                }
                var prediction = runPrediction(hand_coordinates,ortSession,ortEnvironment)
                Log.d("Logging",prediction)
                HandLandmarker.HAND_CONNECTIONS.forEach {
                    canvas.drawLine(
                        handLandmarkerResult.landmarks().get(0).get(it!!.start())
                            .x() * imageWidth * scaleFactor,
                        handLandmarkerResult.landmarks().get(0).get(it.start())
                            .y() * imageHeight * scaleFactor,
                        handLandmarkerResult.landmarks().get(0).get(it.end())
                            .x() * imageWidth * scaleFactor,
                        handLandmarkerResult.landmarks().get(0).get(it.end())
                            .y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }
            }
        }
    }

    fun setResults(
        handLandmarkerResults: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}