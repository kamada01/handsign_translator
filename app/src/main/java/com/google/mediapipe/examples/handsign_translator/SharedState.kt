package com.google.mediapipe.examples.handsign_translator

import android.view.textservice.SpellCheckerSession
import android.view.textservice.TextInfo
import android.widget.TextView
import androidx.lifecycle.ViewModel


object SharedState {
    public var buttonState = 0
    // 0 unpressed
    // 1 pressed
    // 2 stop translation
}