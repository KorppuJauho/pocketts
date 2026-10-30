package io.github.korppujauho.pocketts

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.util.Range
import android.view.Display
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame

/**
 * A phone camera for a stream, with the app's own code rather than WebRTC's, so its settings can change while it
 * runs: the flashlight ([torch]), the zoom ([zoom], each camera its own) and focusing on a tapped spot ([focusAt]). Its pictures go to WebRTC as WebRTC's own camera code gives them: the same size and
 * frame rate chosen the same way, the same stabilization and focus, the same turn and mirroring. So the preview,
 * [CameraTurn] and [DualCamera] see no difference.
 *
 * Everything happens on the picture helper's thread, as in WebRTC's own: the camera's callbacks, its requests and the
 * pictures. [switchTo] closes the camera before opening the other. Losing the camera (a call, another app, an error)
 * calls [onLost], on that thread.
 */
class PhoneCamera(
    context: Context,
    info: CameraInfo,
    private val onLost: (reason: String) -> Unit,
) : VideoCapturer {
    private val manager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val display: Display? = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    private lateinit var helper: SurfaceTextureHelper
    private lateinit var observer: CapturerObserver
    private lateinit var handler: Handler
    @Volatile private var info = info
    private val zooms = java.util.concurrent.ConcurrentHashMap<String, Float>() // each camera's own zoom
    @Volatile private var requestWaiting = false

    // On the camera thread only
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var format: CaptureFormat? = null
    private var generation = 0 // which opening this is: an older one's callbacks are ignored
    private var started = false // onCapturerStarted was said
    private var switched: ((front: Boolean) -> Unit)? = null
    private var width = 0
    private var height = 0
    private var fps = 30
    private var focus: MeteringRectangle? = null // a tapped spot, for a few seconds
    private var focusTurn = 0 // which tap it is: an older one's timeout doesn't undo a newer one

    /** The flashlight wanted on. It lights only on a camera that has one: off on the front camera, back on after flipping. */
    @Volatile var torch = false
        set(value) {
            field = value
            requestSoon()
        }

    /** The camera's zoom now (each camera keeps its own), within what it allows (see [CameraInfo.zoomMin]). */
    var zoom: Float
        get() = zooms[info.id] ?: 1f
        set(value) {
            zooms[info.id] = value.coerceIn(info.zoomMin, info.zoomMax)
            requestSoon()
        }

    // A pinch gives many zooms a second: one new request at a time, with the latest
    private fun requestSoon() {
        if (!::handler.isInitialized || requestWaiting) return
        requestWaiting = true
        handler.post {
            requestWaiting = false
            request()
        }
    }

    /**
     * Focuses and sets the exposure on a spot of the picture (0 - 1 across it, as the sensor lies) for a few seconds,
     * then goes back to following the scene. A camera that can't focus (many front ones) only sets the exposure.
     */
    fun focusAt(x: Float, y: Float) {
        handler.post {
            val i = info
            val f = format ?: return@post
            if (!i.tapFocus && !i.tapExposure) return@post
            val a = i.activeArray
            val r = CameraView.region(x, y, a.width(), a.height(), zoom, f.width.toFloat() / f.height)
            focus = MeteringRectangle(Rect(r[0], r[1], r[2], r[3]), MeteringRectangle.METERING_WEIGHT_MAX - 1)
            val turn = ++focusTurn
            request(trigger = true)
            handler.postDelayed({
                if (turn == focusTurn && focus != null) {
                    focus = null
                    request()
                }
            }, FOCUS_MS)
        }
    }

    /** Which camera it is now. */
    val camera: CameraInfo get() = info

    override fun initialize(helper: SurfaceTextureHelper, context: Context, observer: CapturerObserver) {
        this.helper = helper
        this.observer = observer
        handler = helper.handler
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        handler.post {
            this.width = width
            this.height = height
            fps = framerate
            open()
        }
    }

    /** The other camera ([to]); [done] (on the camera thread) once its pictures come. */
    fun switchTo(to: CameraInfo, done: (front: Boolean) -> Unit) {
        handler.post {
            close()
            info = to
            switched = done
            open()
        }
    }

    @SuppressLint("MissingPermission") // only shared once the camera permission is there
    private fun open() {
        val n = ++generation
        val i = info
        val size = CameraEnumerationAndroid.getClosestSupportedSize(i.sizes, width, height)
        val range = CameraEnumerationAndroid.getClosestSupportedFramerateRange(i.framerates, fps)
        format = CaptureFormat(size.width, size.height, range)
        AppLog.log("[Streams] Opening $i at ${size.width}x${size.height}, ${range.min / 1000}-${range.max / 1000} fps")
        try {
            manager.openCamera(i.id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (n != generation) return camera.close()
                    device = camera
                    configure(camera, n)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (n == generation) lost("taken by something else")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (n == generation) lost("error $error")
                }
            }, handler)
        } catch (e: Exception) {
            lost(e.toString())
        }
    }

    private fun configure(camera: CameraDevice, n: Int) {
        val f = format ?: return
        try {
            helper.setTextureSize(f.width, f.height)
            val output = Surface(helper.surfaceTexture)
            surface = output
            val onThread = Executor { handler.post(it) }
            camera.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, listOf(OutputConfiguration(output)), onThread,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (n != generation) return s.close()
                        session = s
                        request()
                        helper.startListening(::picture)
                        if (!started) {
                            started = true
                            observer.onCapturerStarted(true)
                        }
                        switched?.invoke(info.front)
                        switched = null
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        s.close()
                        if (n == generation) lost("couldn't be set up")
                    }
                }))
        } catch (e: Exception) {
            lost(e.toString())
        }
    }

    // The one request the camera repeats, made again whenever a setting changes (the camera isn't restarted for it)
    private fun request(trigger: Boolean = false) {
        val s = session ?: return
        val camera = device ?: return
        val target = surface ?: return
        val f = format ?: return
        val i = info
        try {
            val r = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            r.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(f.framerate.min / i.fpsFactor, f.framerate.max / i.fpsFactor))
            r.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            r.set(CaptureRequest.CONTROL_AE_LOCK, false)
            // Optical stabilization when there is one, else the picture's own
            if (i.opticalStabilization) {
                r.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                r.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
            } else if (i.videoStabilization) {
                r.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                r.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
            }
            // Zoom: the camera's own (a sharp picture, not an enlarged one); regions are measured on the whole sensor
            val z = zoom
            if (i.zoomRatio && Build.VERSION.SDK_INT >= 30) {
                r.set(CaptureRequest.CONTROL_ZOOM_RATIO, z)
            } else if (z > 1f) {
                val c = CameraView.crop(i.activeArray.width(), i.activeArray.height(), z)
                r.set(CaptureRequest.SCALER_CROP_REGION, Rect(c[0], c[1], c[2], c[3]))
            }
            // Focus: on the tapped spot for a while, else following the scene
            val spot = focus
            if (spot != null && i.tapFocus) {
                r.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                r.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(spot))
            } else if (i.continuousVideoFocus) {
                r.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }
            if (spot != null && i.tapExposure) r.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(spot))
            // The flashlight: lit for the whole recording, the exposure still automatic
            if (i.hasFlash) r.set(CaptureRequest.FLASH_MODE, if (torch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            r.addTarget(target)
            s.setRepeatingRequest(r.build(), null, handler)
            if (trigger && spot != null && i.tapFocus) {
                // A focus run on the spot: an earlier one stopped first
                r.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                s.capture(r.build(), null, handler)
                r.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                s.capture(r.build(), null, handler)
            }
        } catch (e: Exception) {
            AppLog.log("[Streams] Camera request failed: $e")
        }
    }

    private fun picture(frame: VideoFrame) {
        val i = info
        if (session == null) return
        // As WebRTC's own camera code: the mirror Android adds to the front camera taken off, the sensor's turn
        // taken out of the picture and given as its label instead
        val m = Matrix()
        m.preTranslate(0.5f, 0.5f)
        if (i.front) m.preScale(-1f, 1f)
        m.preRotate(-i.sensorOrientation.toFloat())
        m.preTranslate(-0.5f, -0.5f)
        val buffer = frame.buffer as TextureBufferImpl
        val out = VideoFrame(
            buffer.applyTransformMatrix(m, buffer.width, buffer.height),
            CameraMath.frameRotation(i.sensorOrientation, (display?.rotation ?: 0) * 90, i.front),
            frame.timestampNs,
        )
        observer.onFrameCaptured(out)
        out.release()
    }

    private fun close() {
        generation++
        focus = null
        focusTurn++
        helper.stopListening()
        try { session?.close() } catch (_: Exception) {}
        session = null
        surface?.release()
        surface = null
        device?.close()
        device = null
    }

    private fun lost(reason: String) {
        AppLog.log("[Streams] The $info stopped: $reason")
        close()
        if (!started) {
            started = true
            observer.onCapturerStarted(false)
        }
        onLost(reason)
    }

    /** Closes the camera; waits a few seconds at most (a camera that doesn't answer doesn't hold the app). */
    override fun stopCapture() {
        val done = CountDownLatch(1)
        handler.post {
            close()
            done.countDown()
        }
        if (!done.await(3, TimeUnit.SECONDS)) AppLog.log("[Streams] The camera was slow to close")
        observer.onCapturerStopped()
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        handler.post {
            close()
            this.width = width
            this.height = height
            fps = framerate
            open()
        }
    }

    override fun dispose() {}

    override fun isScreencast() = false

    private companion object {
        const val FOCUS_MS = 5000L // how long a tapped spot holds
    }
}
