package com.musicsquare.player.lyrics

import org.junit.Assert.*
import org.junit.Test

class LrcParserTest {
 @Test fun parsesPersianAndEnglishWithMultipleTimestamps() {
  val rows=LrcParser.parse("[00:01.50]سلام دنیا\n[00:02.125][00:03.20]Hello")
  assertEquals(3,rows.size)
  assertEquals(1500L,rows[0].atMs)
  assertEquals("سلام دنیا",rows[0].text)
  assertEquals(2125L,rows[1].atMs)
  assertEquals(3200L,rows[2].atMs)
 }
 @Test fun selectsCurrentLineWithoutGuessing() {
  val lyrics=Lyrics(listOf(TimedLine(1000,"اول"),TimedLine(2500,"دوم")),null,"test")
  assertNull(lyrics.currentLine(500))
  assertEquals("اول",lyrics.currentLine(2400))
  assertEquals("دوم",lyrics.currentLine(3000))
 }
 @Test fun ignoresInvalidSecondsAndPreservesSortedOrder() {
  val rows=LrcParser.parse("[00:61.00]bad\\n[01:02.5]second\\n[00:02.50]first")
  assertEquals(2,rows.size)
  assertEquals(2500L,rows[0].atMs)
  assertEquals(62500L,rows[1].atMs)
 }
 @Test fun limitsExcessiveTimestampRows() {
  val input=(0..10050).joinToString("\\n") { "[00:01.00]text" }
  assertEquals(10000,LrcParser.parse(input).size)
 }
}
