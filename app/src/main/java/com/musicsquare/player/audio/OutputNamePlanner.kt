package com.musicsquare.player.audio

import java.util.Locale

/**
 * Plans stable, non-destructive names for SAF output documents.
 * Existing folder entries are reserved before the first conversion.
 */
object OutputNamePlanner {
    fun sanitizedBase(displayName:String,index:Int):String {
        val base=displayName.substringBeforeLast('.',displayName)
            .replace(Regex("[^\\p{L}\\p{N} _.-]"),"_")
            .trim().trim('.').take(90)
        return base.ifBlank { "audio_${index+1}" }
    }
    fun reserve(displayName:String,index:Int,extension:String,existing:MutableSet<String>):String {
        require(extension.matches(Regex("[a-zA-Z0-9]{2,5}")))
        val base=sanitizedBase(displayName,index)
        var suffix=1
        while(suffix<=10000) {
            val name=if(suffix==1)"${base}_converted.${extension}" else "${base}_converted_${suffix}.${extension}"
            if(existing.add(name.lowercase(Locale.ROOT)))return name
            suffix++
        }
        error("Too many files with matching names")
    }
}
