package io.github.korppujauho.pocketts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraMathTest {
    @Test
    fun frameRotation() {
        // A usual back camera (sensor at 90°): upright 90, the screen turned left 0, right 180
        assertEquals(90, CameraMath.frameRotation(90, 0, front = false))
        assertEquals(0, CameraMath.frameRotation(90, 90, front = false))
        assertEquals(180, CameraMath.frameRotation(90, 270, front = false))
        // A usual front camera (sensor at 270°): it faces the other way, so the screen's turn is added
        assertEquals(270, CameraMath.frameRotation(270, 0, front = true))
        assertEquals(0, CameraMath.frameRotation(270, 90, front = true))
        assertEquals(180, CameraMath.frameRotation(270, 270, front = true))
        // Upside down
        assertEquals(270, CameraMath.frameRotation(90, 180, front = false))
        assertEquals(90, CameraMath.frameRotation(270, 180, front = true))
    }

    @Test
    fun zoomCrop() {
        // 2× shows the middle half each way; under 1× the whole area is the nearest
        assertArrayEquals(intArrayOf(1000, 750, 3000, 2250), CameraView.crop(4000, 3000, 2f))
        assertArrayEquals(intArrayOf(0, 0, 4000, 3000), CameraView.crop(4000, 3000, 1f))
        assertArrayEquals(intArrayOf(0, 0, 4000, 3000), CameraView.crop(4000, 3000, 0.6f))
    }

    @Test
    fun tapOnTurnedPicture() {
        // A picture shown turned a quarter clockwise: its top left corner is shown at the top right
        assertPoint(0f to 0f, CameraView.unturn(1f, 0f, 90))
        assertPoint(0f to 0f, CameraView.unturn(1f, 1f, 180))
        assertPoint(0f to 0f, CameraView.unturn(0f, 1f, 270))
        assertPoint(0.25f to 0.75f, CameraView.unturn(0.25f, 0.75f, 0))
        assertPoint(0.5f to 0.5f, CameraView.unturn(0.5f, 0.5f, 90))
        // Turned all the way round is as it was
        assertPoint(0.2f to 0.7f, CameraView.unturn(0.2f, 0.7f, 360))
    }

    @Test
    fun tapOnFittedPicture() {
        // A 9:16 picture in a 400 × 400 box: 225 wide in the middle
        assertPoint(0f to 0f, CameraView.fitted(87.5f, 0f, 400f, 400f, 9f / 16f)!!)
        assertPoint(0.5f to 0.5f, CameraView.fitted(200f, 200f, 400f, 400f, 9f / 16f)!!)
        assertEquals(null, CameraView.fitted(20f, 200f, 400f, 400f, 9f / 16f)) // beside it
        // Filling its box
        assertPoint(0.25f to 1f, CameraView.fitted(100f, 300f, 400f, 300f, 4f / 3f)!!)
    }

    @Test
    fun meteringRegion() {
        // The middle of a 16:9 picture from a 4:3 sensor at 1×: the region is 15 % of the picture around the centre
        val r = CameraView.region(0.5f, 0.5f, 4000, 3000, 1f, 16f / 9f)
        assertArrayEquals(intArrayOf(1700, 1331, 2300, 1669), r)
        // The 16:9 picture is the sensor's middle band: its top edge is 375 down
        assertEquals(375, CameraView.region(0.5f, 0f, 4000, 3000, 1f, 16f / 9f, size = 0f)[1])
        // At 2× its left edge is a quarter in
        assertEquals(1000, CameraView.region(0f, 0.5f, 4000, 3000, 2f, 4f / 3f, size = 0f)[0])
        // Never outside the sensor
        assertArrayEquals(intArrayOf(0, 0, 300, 225), CameraView.region(0f, 0f, 4000, 3000, 1f, 4f / 3f))
    }

    @Test
    fun lensChoice() {
        // Main 70°, ultra-wide 110°, telephoto 30°, and the main lens again as another camera
        assertEquals(listOf(1 to Lens.WIDE, 0 to Lens.MAIN, 3 to Lens.TELE), CameraView.lenses(listOf(70f, 110f, 72f, 30f)))
        // Only the same lens: nothing to choose
        assertEquals(emptyList<Pair<Int, Lens>>(), CameraView.lenses(listOf(70f, 69f)))
        assertEquals(emptyList<Pair<Int, Lens>>(), CameraView.lenses(listOf(70f)))
        // A phone that doesn't say
        assertEquals(emptyList<Pair<Int, Lens>>(), CameraView.lenses(listOf(0f, 110f)))
        assertEquals(84f, CameraView.fieldOfView(6.4f, 3.55f), 0.5f)
    }

    @Test
    fun lensZoom() {
        // This phone's ultra-wide (104°) against its main lens (73°): 0.6×
        assertEquals("0.6×", CameraView.zoomLabel(CameraView.zoomFactor(104f, 73f)))
        assertEquals("1×", CameraView.zoomLabel(CameraView.zoomFactor(73f, 73f)))
        // A telephoto three times as long
        assertEquals("3×", CameraView.zoomLabel(CameraView.zoomFactor(27.7f, 73f)))
        assertEquals("0.5×", CameraView.zoomLabel(0.5f))
        assertEquals("2.5×", CameraView.zoomLabel(2.47f))
        // A phone that doesn't say
        assertEquals(1f, CameraView.zoomFactor(0f, 73f), 0f)
    }

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 0.001f)
        assertEquals(expected.second, actual.second, 0.001f)
    }

    @Test
    fun fpsUnits() {
        assertEquals(1000, CameraMath.fpsUnitFactor(30)) // whole frames per second
        assertEquals(1, CameraMath.fpsUnitFactor(30000)) // already thousandths
        assertEquals(1000, CameraMath.fpsUnitFactor(null)) // no ranges
    }
}
