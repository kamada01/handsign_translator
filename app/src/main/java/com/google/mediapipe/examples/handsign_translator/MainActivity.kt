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

import android.app.ProgressDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.google.mediapipe.examples.handsign_translator.databinding.ActivityMainBinding
import java.io.IOException
import com.google.android.material.button.MaterialButton
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.checkerframework.checker.nullness.qual.NonNull
import java.util.ArrayList
import java.util.Locale

class MainActivity : AppCompatActivity(){
    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var mainViewModel: MainViewModel
    private lateinit var resultTextView: TextView

    private lateinit var translatedText: TextView
    private lateinit var srcLanguageBtn: MaterialButton
    private lateinit var desLanguageBtn: MaterialButton
    private lateinit var translatebtn: MaterialButton
    private lateinit var translatorOptions: TranslatorOptions
    private lateinit var translator: Translator
    private lateinit var progressDialog: ProgressDialog
    private lateinit var langArrayList: ArrayList<ModelLanguage>
    private var srcLangCode: String = "en"
    private var srcLangTitle: String = "English"
    private var desLangCode: String = "kn"
    private var desLangTitle: String = "Korean"

    private lateinit var startButton: Button
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
            if (SharedState.buttonState == 0) {
                SharedState.buttonState = 1
            } else if (SharedState.buttonState == 1) {
                SharedState.buttonState = 2
            } else if (SharedState.buttonState == 2) {
                SharedState.buttonState = 0
            }
        }

        translatedText = findViewById(R.id.translatedText)
        srcLanguageBtn = findViewById(R.id.srcLangBtn)
        desLanguageBtn = findViewById(R.id.desLangBtn)
        translatebtn = findViewById(R.id.translatebtn)

        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please Wait")
        progressDialog.setCanceledOnTouchOutside(false)
        loadAvailableLang()

        srcLanguageBtn.setOnClickListener(){
            srcLangChoose()
        }

        desLanguageBtn.setOnClickListener(){
            desLangChoose()
        }

        translatebtn.setOnClickListener(){
            validateData()
        }

    }

    private var srcLangText: String = "";
    private fun validateData() {
        srcLangText = resultTextView.text.toString().trim()

        if (srcLangText.isEmpty()){
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
        } else {
            startTranslations()
        }
    }

    private fun startTranslations() {
        progressDialog.setMessage("Processing lang model...")
        progressDialog.show()

        translatorOptions = TranslatorOptions.Builder()
            .setSourceLanguage(srcLangCode)
            .setTargetLanguage(desLangCode)
            .build()

        translator = Translation.getClient(translatorOptions)

        var downloadConditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                progressDialog.setMessage("Translating...")
                translator.translate(srcLangText)
                    .addOnSuccessListener {s ->
                        translatedText.text = s
                        progressDialog.dismiss()
                    }
                    .addOnFailureListener{e ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to translate", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener{e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to read", Toast.LENGTH_SHORT).show()
            }
    }

    private fun srcLangChoose(){

        var popupMenu = PopupMenu(this, srcLanguageBtn);

        var i = 0
        while(i < langArrayList.size) {
            popupMenu.menu.add(Menu.NONE, i, i, langArrayList[i].langTitle)
            i++
        }

        popupMenu.show()

        popupMenu.setOnMenuItemClickListener {item ->
            val position = item.itemId

            srcLangCode = langArrayList.get(position).langCode
            srcLangTitle = langArrayList.get(position).langTitle

            srcLanguageBtn.setText(srcLangTitle)

            Log.d("source", "srcLangChoose: "+srcLangCode)
            false
        }
    }

    private fun desLangChoose(){

        var popupMenu = PopupMenu(this, desLanguageBtn);

        var i = 0
        while(i < langArrayList.size) {
            popupMenu.menu.add(Menu.NONE, i, i, langArrayList[i].langTitle)
            i++
        }

        popupMenu.show()

        popupMenu.setOnMenuItemClickListener {item ->
            val position = item.itemId

            desLangCode = langArrayList.get(position).langCode
            desLangTitle = langArrayList.get(position).langTitle

            desLanguageBtn.setText(desLangTitle)

            Log.d("Des", "desLangChoose: "+desLangCode)

            false
        }
    }

    private fun loadAvailableLang() {
        langArrayList = ArrayList<ModelLanguage>();

        val langCodeList = TranslateLanguage.getAllLanguages();

        for(langCode in langCodeList){
            val locale = Locale(langCode)

            val langTitle = if(locale.getDisplayCountry().isNotEmpty()){
                locale.getDisplayCountry()
            } else {
                langCode
            }

            val modelLanguage = ModelLanguage(langCode, langTitle);
            langArrayList.add(modelLanguage);

            Log.d("Here", "loadAvailableLang: " + langCode)
            Log.d("Here", "loadAvailableLang: "+ langTitle)

        }
    }

}
