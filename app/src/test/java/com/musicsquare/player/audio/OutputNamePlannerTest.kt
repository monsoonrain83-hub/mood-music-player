package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test

class OutputNamePlannerTest {
    @Test fun preservesExistingFilesAndReservesNextAvailableName() {
        val existing=mutableSetOf("song_converted.mp3","song_converted_2.mp3")
        assertEquals("song_converted_3.mp3",OutputNamePlanner.reserve("song.flac",0,"mp3",existing))
        assertTrue(existing.contains("song_converted_3.mp3"))
    }
    @Test fun caseInsensitiveCollisionDetection() {
        val existing=mutableSetOf("TRACK_CONVERTED.MP3".lowercase())
        assertEquals("track_converted_2.mp3",OutputNamePlanner.reserve("track.wav",0,"mp3",existing))
    }
    @Test fun sanitizesUnsafeNames() {
        assertEquals("audio_4",OutputNamePlanner.sanitizedBase("...",3))
        assertEquals("my_song",OutputNamePlanner.sanitizedBase("my/song.wav",0))
    }
    @Test fun rejectsInvalidExtension() {
        assertThrows(IllegalArgumentException::class.java) {
            OutputNamePlanner.reserve("song.mp3",0,"../bad",mutableSetOf())
        }
    }
}
