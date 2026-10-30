package io.github.korppujauho.pocketts

import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import org.webrtc.CameraEnumerationAndroid.CaptureFormat.FramerateRange
import org.webrtc.Size

/** What a camera is and can do, read once from Android. */
class CameraInfo(val id: String, c: CameraCharacteristics) {
    val front = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
    /** How far the sensor is turned from the phone's upright, clockwise. */
    val sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    private val fpsRanges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
    /** What the camera's own frame rates are multiplied by to compare them as WebRTC does (see [CameraMath.fpsUnitFactor]). */
    val fpsFactor = CameraMath.fpsUnitFactor(fpsRanges.firstOrNull()?.upper)
    val framerates: List<FramerateRange> = fpsRanges.map { FramerateRange(it.lower * fpsFactor, it.upper * fpsFactor) }
    val sizes: List<Size> = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(SurfaceTexture::class.java)?.map { Size(it.width, it.height) }.orEmpty()

    val opticalStabilization = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        ?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
    val videoStabilization = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        ?.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
    val continuousVideoFocus = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        ?.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) == true
    /** Focus on a tapped spot: a lens that focuses, and regions for it. */
    val tapFocus = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO) == true &&
        (c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0
    /** Exposure on a tapped spot (also for a fixed-focus front camera). */
    val tapExposure = (c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0

    /** The sensor's area that pictures and regions are measured in. */
    val activeArray: Rect = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 1, 1)

    // Zoom: Android 11's zoom ratio (which can go under 1× on phones whose lenses work as one camera: the ultra-wide),
    // else cropping the picture, up to its largest digital zoom
    private val ratioRange = if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
    val zoomRatio = ratioRange != null
    val zoomMin: Float = ratioRange?.lower ?: 1f
    val zoomMax: Float = ratioRange?.upper ?: (c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)

    /** How wide it sees, across the sensor's long side, in degrees (0 when the phone doesn't say). */
    val fieldOfView: Float = run {
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
        val sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focal > 0f && sensor != null) CameraView.fieldOfView(maxOf(sensor.width, sensor.height), focal) else 0f
    }

    /** The lenses a combined camera is made of (Android 9+: its "physical" cameras), when it says. */
    val lensIds: Set<String> = try { c.physicalCameraIds } catch (_: Exception) { emptySet() }

    override fun toString() = "camera $id (${if (front) "front" else "back"}${if (hasFlash) ", flash" else ""})"

    companion object {
        /** The phone's cameras, in Android's order (one that can't be read is left out). */
        fun all(context: Context): List<CameraInfo> {
            val cm = context.getSystemService(CameraManager::class.java) ?: return emptyList()
            return try {
                cm.cameraIdList.mapNotNull { id -> of(cm, id) }
            } catch (e: Exception) {
                AppLog.log("[Streams] The cameras can't be listed: $e")
                emptyList()
            }
        }

        /** The back cameras to choose from (wide-angle, main, telephoto), when the phone offers more than one lens of its own. */
        fun backLenses(context: Context): List<Pair<CameraInfo, Lens>> {
            val backs = all(context).filter { !it.front }
            describe(context)
            return CameraView.lenses(backs.map { it.fieldOfView }).map { (i, kind) -> backs[i] to kind }
        }

        // Once a run, in the log: what the phone offers apps (which lenses can be had, and how far each zooms)
        private var described = false

        private fun describe(context: Context) {
            if (described) return
            described = true
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            fun line(i: CameraInfo) = "${i.id} ${if (i.front) "front" else "back"} %.0f° zoom %.1f-%.1f".format(i.fieldOfView, i.zoomMin, i.zoomMax)
            for (i in all(context)) {
                val parts = i.lensIds.mapNotNull { of(cm, it) }.joinToString { "lens ${line(it)}" }
                AppLog.log("[Streams] Camera ${line(i)}" + if (parts.isNotEmpty()) " (made of $parts)" else "")
            }
        }

        fun of(context: Context, id: String): CameraInfo? = context.getSystemService(CameraManager::class.java)?.let { of(it, id) }

        private fun of(cm: CameraManager, id: String): CameraInfo? =
            try {
                CameraInfo(id, cm.getCameraCharacteristics(id))
            } catch (e: Exception) {
                AppLog.log("[Streams] Camera $id can't be read: $e")
                null
            }
    }
}
