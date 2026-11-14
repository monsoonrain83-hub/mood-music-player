package com.musicsquare.player.tags

/** Validates metadata before opening a writable SAF descriptor. */
object TagInputValidator {
    fun validate(changes:TagChanges):Result<TagChanges> = runCatching {
        val values=listOf(changes.title,changes.artist,changes.album,changes.year,changes.genre,changes.track,changes.comment)
        require(values.any { it!=null }) { "No metadata changes requested" }
        values.forEach { value ->
            if(value!=null) {
                require(value.length<=4096) { "Metadata field exceeds 4096 characters" }
                require(value.none { it=='\u0000' || (it.code<32 && it!='\n' && it!='\t') }) {
                    "Metadata contains unsupported control characters"
                }
            }
        }
        changes.year?.let {
            require(it.isBlank() || it.matches(Regex("[0-9]{4}"))) { "Year must be four digits" }
        }
        changes.track?.let {
            require(it.isBlank() || it.matches(Regex("[1-9][0-9]{0,3}(/[1-9][0-9]{0,3})?"))) {
                "Track must be a positive number or track/total"
            }
        }
        changes
    }
}
