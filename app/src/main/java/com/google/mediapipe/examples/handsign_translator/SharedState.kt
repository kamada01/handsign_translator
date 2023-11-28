package com.google.mediapipe.examples.handsign_translator

import android.widget.TextView
import androidx.lifecycle.ViewModel

class SharedState : ViewModel() {
    public var state = false
    public var resultsTextView : TextView? = null;
}