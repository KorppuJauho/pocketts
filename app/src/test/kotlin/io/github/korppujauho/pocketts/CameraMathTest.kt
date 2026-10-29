package io.github.korppujauho.pocketts

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
    fun fpsUnits() {
        assertEquals(1000, CameraMath.fpsUnitFactor(30)) // whole frames per second
        assertEquals(1, CameraMath.fpsUnitFactor(30000)) // already thousandths
        assertEquals(1000, CameraMath.fpsUnitFactor(null)) // no ranges
    }
}
