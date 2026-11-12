package com.musicsquare.player.domain

/** 5×5 mood matrix: row 0 = happiest, column 4 = most energetic. */
object MoodCoordinates {
 fun cell(valence:Float,energy:Float):Int {
  require(valence.isFinite() && energy.isFinite())
  val row=4-(valence.coerceIn(0f,1f)*5f).toInt().coerceIn(0,4)
  val col=(energy.coerceIn(0f,1f)*5f).toInt().coerceIn(0,4)
  return row*5+col
 }
 fun center(cell:Int):Pair<Float,Float> {
  require(cell in 0..24)
  return ((4-cell/5)+0.5f)/5f to ((cell%5)+0.5f)/5f
 }
}
