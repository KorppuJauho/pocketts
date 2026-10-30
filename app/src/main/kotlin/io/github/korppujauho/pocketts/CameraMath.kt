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

/** A back camera's kind, by how wide it sees compared with the main one. */
enum class Lens { WIDE, MAIN, TELE }

/** Zoom, tapping and lenses. */
object CameraView {
    /** How wide a lens sees, in degrees, from the sensor's size and the focal length (both in mm). */
    fun fieldOfView(sensorSize: Float, focal: Float): Float = Math.toDegrees(2 * kotlin.math.atan(sensorSize / (2.0 * focal))).toFloat()

    /**
     * The middle of a [w] × [h] sensor area that a [zoom] shows: left, top, right, bottom. Under 1× (Android's zoom
     * ratio on phones whose lenses work as one camera) the whole area is the nearest it can say.
     */
    fun crop(w: Int, h: Int, zoom: Float): IntArray {
        val z = maxOf(zoom, 1f)
        val cw = Math.round(w / z)
        val ch = Math.round(h / z)
        val left = (w - cw) / 2
        val top = (h - ch) / 2
        return intArrayOf(left, top, left + cw, top + ch)
    }

    /** A point on a picture shown turned by [turn] (clockwise), back on the picture as it was; all 0 - 1. */
    fun unturn(x: Float, y: Float, turn: Int): Pair<Float, Float> {
        var a = x
        var b = y
        repeat((turn % 360 + 360) % 360 / 90) {
            val turnedBack = b to 1 - a
            a = turnedBack.first
            b = turnedBack.second
        }
        return a to b
    }

    /** Where a tap lands on a picture shown whole and centred in a box: 0 - 1 across the picture, or null beside it. */
    fun fitted(x: Float, y: Float, boxW: Float, boxH: Float, aspect: Float): Pair<Float, Float>? {
        if (boxW <= 0f || boxH <= 0f || aspect <= 0f) return null
        val (pw, ph) = if (boxW / boxH > aspect) boxH * aspect to boxH else boxW to boxW / aspect
        val px = (x - (boxW - pw) / 2) / pw
        val py = (y - (boxH - ph) / 2) / ph
        return if (px in 0f..1f && py in 0f..1f) px to py else null
    }

    /**
     * The sensor region for focusing and exposing at a point of the picture (0 - 1 across it, as the sensor lies),
     * [size] of the picture across: left, top, right, bottom in a [w] × [h] sensor area at [zoom]. The picture is the
     * middle of what the zoom shows, cut to its own shape ([aspect]: its width / height).
     */
    fun region(x: Float, y: Float, w: Int, h: Int, zoom: Float, aspect: Float, size: Float = 0.15f): IntArray {
        val c = crop(w, h, zoom)
        var fw = (c[2] - c[0]).toFloat()
        var fh = (c[3] - c[1]).toFloat()
        var left = c[0].toFloat()
        var top = c[1].toFloat()
        if (fw / fh > aspect) {
            val pw = fh * aspect
            left += (fw - pw) / 2
            fw = pw
        } else {
            val ph = fw / aspect
            top += (fh - ph) / 2
            fh = ph
        }
        val cx = left + x * fw
        val cy = top + y * fh
        val hw = fw * size / 2
        val hh = fh * size / 2
        fun clamp(v: Float, max: Int) = Math.round(v).coerceIn(0, max)
        return intArrayOf(clamp(cx - hw, w - 1), clamp(cy - hh, h - 1), clamp(cx + hw, w - 1), clamp(cy + hh, h - 1))
    }

    /**
     * Which back cameras to offer, from how wide each sees ([fovs], the main one first, as Android lists it): the
     * widest of the clearly wider ones as the wide-angle, the narrowest of the clearly narrower ones as the telephoto.
     * Ones that see about the same (the same lens again, or all the lenses as one camera) are left out. Indexes into
     * [fovs], widest first; empty when there's nothing to choose.
     */
    fun lenses(fovs: List<Float>): List<Pair<Int, Lens>> {
        val main = fovs.firstOrNull() ?: return emptyList()
        if (main <= 0f) return emptyList()
        val others = fovs.withIndex().drop(1).filter { it.value > 0f }
        val wide = others.filter { it.value > main * 1.2f }.maxByOrNull { it.value }?.index
        val tele = others.filter { it.value < main / 1.2f }.minByOrNull { it.value }?.index
        if (wide == null && tele == null) return emptyList()
        return listOfNotNull(wide?.let { it to Lens.WIDE }, 0 to Lens.MAIN, tele?.let { it to Lens.TELE })
    }
}
