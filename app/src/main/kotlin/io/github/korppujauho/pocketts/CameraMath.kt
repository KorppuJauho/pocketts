package io.github.korppujauho.pocketts

/** The camera's arithmetic, apart from the camera itself (unit tested). */
object CameraMath {
    /**
     * The turn a camera picture is labelled with, as WebRTC's own camera code labels it: the sensor's turn, with the
     * screen's turn taken off (the front camera faces the other way, so it's added).
     */
    fun frameRotation(sensor: Int, display: Int, front: Boolean): Int {
        val screen = if (front) display else (360 - display) % 360
        return (sensor + screen) % 360
    }

    /**
     * Frame rate ranges counted in thousandths of a frame per second, as WebRTC compares them: cameras give whole
     * frames per second (then ×1000) or, a few, already thousandths. [firstUpper]: the first range's top.
     */
    fun fpsUnitFactor(firstUpper: Int?): Int = if (firstUpper == null || firstUpper < 1000) 1000 else 1
}
