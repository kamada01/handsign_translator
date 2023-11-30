package com.google.mediapipe.examples.handsign_translator

class Word (val word: String, val counter: Int){
    override fun toString(): String {
        return this.word
    }
}