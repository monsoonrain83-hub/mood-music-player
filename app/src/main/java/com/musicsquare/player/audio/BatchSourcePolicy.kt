package com.musicsquare.player.audio

/** Pure validation rules applied immediately before a batch output is created. */
object BatchSourcePolicy {
    fun checkKnownSize(size:Long?) {
        if(size==null || size<0L)return // The streaming reader still enforces the hard limit.
        require(size>0L) { "Source file is empty" }
        require(size<=AudioFileSafety.MAX_INPUT_BYTES) { "Source exceeds 2 GiB input limit" }
    }

    fun checkSelection(total:Int,unique:Int) {
        require(total in 1..200) { "Select 1–200 files" }
        require(unique==total) { "Duplicate source files selected" }
    }
}
