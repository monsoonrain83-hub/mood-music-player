package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class AudioFileSafetyTest {
    @Test fun copiesExactBytes() {
        val data=ByteArray(150_000) { (it%251).toByte() }
        val target=ByteArrayOutputStream()
        val count=AudioFileSafety.copyBounded(ByteArrayInputStream(data),target)
        assertEquals(data.size.toLong(),count)
        assertArrayEquals(data,target.toByteArray())
    }
    @Test fun rejectsEmptyInput() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioFileSafety.copyBounded(ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream())
        }
    }
    @Test fun rejectsInputAboveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioFileSafety.copyBounded(ByteArrayInputStream(ByteArray(101)),ByteArrayOutputStream(),100)
        }
    }
    @Test fun acceptsInputExactlyAtLimit() {
        assertEquals(100L,AudioFileSafety.copyBounded(ByteArrayInputStream(ByteArray(100)),ByteArrayOutputStream(),100))
    }
    @Test fun computesStreamingSha256() {
        val data="verified output".toByteArray()
        val (length,hash)=AudioFileSafety.sha256(ByteArrayInputStream(data))
        assertEquals(data.size.toLong(),length)
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(data),hash)
    }
    @Test fun rejectsDigestAboveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioFileSafety.sha256(ByteArrayInputStream(ByteArray(101)),100)
        }
    }
    @Test fun propagatesCancellationFromCopy() {
        assertThrows(IllegalStateException::class.java) {
            AudioFileSafety.copyBounded(ByteArrayInputStream(ByteArray(100)),ByteArrayOutputStream()) {
                throw IllegalStateException("cancel")
            }
        }
    }
}
