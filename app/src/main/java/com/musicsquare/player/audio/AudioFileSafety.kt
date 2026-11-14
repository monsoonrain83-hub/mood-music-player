package com.musicsquare.player.audio

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Centralized streaming copy and integrity verification for local audio work.
 * Enforces a hard input limit even when SAF providers hide or lie about file size.
 */
object AudioFileSafety {
    const val MAX_INPUT_BYTES:Long=2L*1024*1024*1024
    const val MAX_OUTPUT_BYTES:Long=2L*1024*1024*1024
    private const val BUFFER_SIZE=64*1024

    fun copyBounded(
        source:InputStream,destination:OutputStream,
        limit:Long=MAX_INPUT_BYTES,checkCancelled:()->Unit={}
    ):Long {
        require(limit>0) { "Invalid copy limit" }
        val bytes=ByteArray(BUFFER_SIZE)
        var copied=0L
        while(true) {
            checkCancelled()
            val count=source.read(bytes)
            if(count<0)break
            if(count==0)continue
            require(count.toLong()<=limit-copied) { "Audio file exceeds size limit" }
            destination.write(bytes,0,count)
            copied+=count
        }
        require(copied>0) { "Audio file is empty" }
        return copied
    }

    fun sha256(source:InputStream,limit:Long=MAX_OUTPUT_BYTES,checkCancelled:()->Unit={}):Pair<Long,ByteArray> {
        require(limit>0) { "Invalid digest limit" }
        val digest=MessageDigest.getInstance("SHA-256")
        val bytes=ByteArray(BUFFER_SIZE)
        var count=0L
        while(true) {
            checkCancelled()
            val n=source.read(bytes)
            if(n<0)break
            if(n==0)continue
            require(n.toLong()<=limit-count) { "Audio file exceeds verification limit" }
            digest.update(bytes,0,n)
            count+=n
        }
        return count to digest.digest()
    }
}
