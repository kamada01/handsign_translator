package com.google.mediapipe.examples.handsign_translator

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import androidx.viewpager2.widget.ViewPager2

class IntroActivity : AppCompatActivity() {

    private lateinit var screenPager: ViewPager2
    private lateinit var btnNext: Button
    private var position: Int = 0
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // check if app launched before
        if (restorePrefData()) {
            val mainActivity = Intent(applicationContext, MainActivity::class.java)
            startActivity(mainActivity)

            finish();
        }
        setContentView(R.layout.activity_intro)

        var mList = ArrayList<ScreenItem>()
        mList.add(ScreenItem("Capture Sign Gestures", "", R.drawable.camera))
        mList.add(ScreenItem("Translate into other languages", "", R.drawable.translator))

        screenPager=findViewById(R.id.screen_viewpager)
        val introViewPagerAdapter = IntroViewPagerAdapter(this, mList)
        screenPager.adapter = introViewPagerAdapter

        btnNext = findViewById(R.id.next_btn)

        btnNext.setOnClickListener(){
            position = screenPager.currentItem
            if (position < mList.size) {
                position ++
                screenPager.setCurrentItem(position)
            }
            if (position == mList.size - 1) {
                loadLastScreen()
            }
        }

        btnStart = findViewById(R.id.getStartedbtn)

        btnStart.setOnClickListener(){

            val mainActivity = Intent(applicationContext, MainActivity::class.java)
            startActivity(mainActivity)

            savePrefsData()
            finish()

        }
    }

    private fun restorePrefData(): Boolean {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        val isIntroActivityOpenedBefore = pref.getBoolean("isIntroOpened", false)
        return isIntroActivityOpenedBefore
    }

    private fun savePrefsData() {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean("isIntroOpened", true)
        editor.apply()

    }

    private fun loadLastScreen() {
        btnNext.visibility = View.INVISIBLE
        btnStart.visibility = View.VISIBLE
    }

}