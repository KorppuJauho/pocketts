package io.github.korppujauho.pocketts

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
