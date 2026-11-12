package com.musicsquare.player.domain

import org.junit.Assert.*
import org.junit.Test

class MoodCoordinatesTest {
 @Test fun cornersAndCenter() {
  assertEquals(20,MoodCoordinates.cell(0f,0f))
  assertEquals(4,MoodCoordinates.cell(1f,1f))
  assertEquals(12,MoodCoordinates.cell(0.5f,0.5f))
 }
 @Test fun allCellCentersRoundTrip() {
  for(cell in 0..24) {
   val (v,e)=MoodCoordinates.center(cell)
   assertEquals(cell,MoodCoordinates.cell(v,e))
  }
 }
 @Test fun clampsOutOfRange() {
  assertEquals(20,MoodCoordinates.cell(-5f,-1f))
  assertEquals(4,MoodCoordinates.cell(10f,7f))
 }
}
