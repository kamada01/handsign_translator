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

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.google.mediapipe.examples.handsign_translator.databinding.ActivityMainBinding
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var mainViewModel: MainViewModel
    private lateinit var resultTextView: TextView
    private lateinit var startButton: Button
    private val sharedState: SharedState by viewModels()
    var spellChecker: SpellChecker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        resultTextView = findViewById(R.id.resultTextView)

        try {
            val inputStream = this.assets.open("words.txt")
            val bufferedReader = inputStream.bufferedReader()
            spellChecker = SpellChecker.loadFromReader(bufferedReader).build()
        } catch (e: Exception) {
            throw IOException("Error reading word txt: ${e}")
        }

        startButton = findViewById(R.id.start_button)
        startButton.text = "Translate"
        startButton.setOnClickListener() {
            sharedState.start = !sharedState.start
        }

    }



}
