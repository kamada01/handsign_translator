package com.google.mediapipe.examples.handsign_translator

import java.io.BufferedReader
import java.io.FileReader

class SpellChecker private constructor(private val words : Collection<String> ,
                                       distanceFunc : (String,String) -> Int) {

    companion object Builder {

        private val wordsList : MutableList<String> = mutableListOf()
        private var distanceFunc : (String,String) -> Int = ::LevenshteinDistance

        fun load(vararg words : String) : Builder {

            for( word in words )
                wordsList.add( word )

            return this
        }
        fun LevenshteinDistance(str1 : String , str2 : String) : Int {

            fun array2dOfInt(sizeOuter: Int, sizeInner: Int): Array<IntArray>
                    = Array(sizeOuter) { IntArray(sizeInner) }

            fun min( a : Int , b : Int , c : Int ) : Int  = Math.min( a , Math.min(b,c) )

            val arr = array2dOfInt(str1.length + 1 , str2.length + 1)

            for (col in 0..str2.length)
                arr[0][col] = col

            for( row in 1..str1.length )
                arr[row][0] = row

            for( row in 1..str1.length ){
                for( col in 1..str2.length ){

                    if( str1[row-1] == str2[col-1] ) {
                        arr[row][col] = arr[row - 1][col - 1]
                    }
                    else{
                        arr[row][col] = 1 + min( arr[row][col-1] , arr[row-1][col] , arr[row-1][col-1] )
                    }
                }
            }

            return arr[str1.length][str2.length]
        }
        fun loadFromReader(reader : BufferedReader) : Builder {

//            val reader = BufferedReader(FileReader(filename))

            while (true) {

                val word = reader.readLine() ?: break

                wordsList.add( word )

            }

            return this
        }

        fun withEditDistanceFunction( func : (str1:String,str2:String)->Int ) : Builder {
            distanceFunc = func

            return this
        }

        fun build() : SpellChecker {

            if( wordsList.size == 0 )
                throw Exception("please specify words list")

            return SpellChecker(wordsList , distanceFunc)
        }

    }

    private val bkTree : BKTree

    init {

        bkTree = BKTree(distanceFunc)
        for( word in words ){
            bkTree.add( word )
        }

    }

    val totalWords: Int get() = wordsList.size

    fun suggest(misspellWord : String , tolerance : Int = 1) : List<String> {

        return bkTree.getSpellSuggestion( misspellWord , tolerance )

    }

}