package com.google.mediapipe.examples.handsign_translator

class Node (val word: String){
    val children: HashMap<Int,Node> = hashMapOf()
}