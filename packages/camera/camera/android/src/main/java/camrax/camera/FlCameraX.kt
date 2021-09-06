package camrax.camera;

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import camrax.camera.BitmapHelper.processImage
import com.google.common.util.concurrent.ListenableFuture
import fl.camera.CameraTools
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.camera.DartMessenger
import io.flutter.plugins.camera.types.ExposureMode
import io.flutter.plugins.camera.types.FlashMode
import io.flutter.plugins.camera.types.FocusMode
import io.flutter.view.TextureRegistry
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.StreamHandler
import kotlin.collections.set


class FlCameraX(
    private val activity: Activity,
    private var textureEntry: TextureRegistry.SurfaceTextureEntry?,
    private val dartMessenger: DartMessenger
) {

    private val executor = ContextCompat.getMainExecutor(activity)
    private var cameraProvider: ProcessCameraProvider? = null

    //public var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var camera: Camera? = null
    var imageCapture: ImageCapture? = null
    var analysis: ImageAnalysis? = null
    public var cameraSelector: CameraSelector? = null
    var textureId: Long? = null
    var previewResolution: Size? = null


    fun getanalysis(): ImageAnalysis? {
        return analysis
    }

    fun settextureId(textureId: Long) {
        this.textureId = textureId
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }


    @SuppressLint("RestrictedApi")
    fun initCameraX(


        result: MethodChannel.Result,
        imageAnalyzer: ImageAnalysis.Analyzer?
    ) {
        if (!checkPermission()) return
        val provider = ProcessCameraProvider.getInstance(activity)
        val owner = activity as LifecycleOwner


        provider.addListener({
            cameraProvider = provider.get()


            val surfaceProvider = Preview.SurfaceProvider { request ->
                val texture = textureEntry!!.surfaceTexture()
                val resolution = request.resolution
                texture.setDefaultBufferSize(resolution.width, resolution.height)
                val surface = Surface(texture)
                request.provideSurface(surface, executor, { })
            }
            val preview =
                Preview.Builder()
                    // .setTargetResolution(previewSize)
                    .build()
                    .apply { setSurfaceProvider(surfaceProvider) }

            analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // .setTargetResolution(previewSize)
                .build().apply {
                    if (imageAnalyzer != null) {
                        setAnalyzer(executor, imageAnalyzer)
                    }
                }

            try {
                cameraProvider!!.unbindAll()
                val screenAspectRatio = AspectRatio.RATIO_4_3
                imageCapture = ImageCapture.Builder()
                    .setTargetName("Capture")
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) // We request aspect ratio but no resolution to match preview config, but letting
                    // CameraX optimize for whatever specific resolution best fits requested capture mode
                    .setTargetAspectRatio(screenAspectRatio) //.setTargetResolution(new Size(4500, 6000))
                    // Set initial target rotation, we will have to call this again if rotation changes
                    // during the lifecycle of this use case
                    // .setTargetRotation(rotation)
                    .build()

                camera = cameraProvider!!.bindToLifecycle(
                    owner,
                    cameraSelector!!,
                    analysis,
                    preview,

                    imageCapture
                )

                /*camera!!.cameraInfo.torchState.observe(owner, { state ->
                    // TorchState.OFF = 0; TorchState.ON = 1
                  //  flCameraEvent.sendEvent(mapOf("flash" to (state == TorchState.ON)))
                })
                camera!!.cameraInfo.zoomState.observe(owner, { state ->
                  *//*  flCameraEvent.sendEvent(
                        mapOf(
                            "maxZoomRatio" to state.maxZoomRatio,
                            "zoomRatio" to state.zoomRatio
                        )
                    )*//*
                })*/

                // Default PreviewSurfaceProvider
                //preview.setSurfaceProvider(cameraView.getPreviewSurfaceProvider());

                /*    val rotation: Int = camera.getDisplay().getRotation()*/
                // ImageCapture


                try {
                    camera!!.cameraInfo.cameraState.observe(owner, { state ->
                        if (state.type == CameraState.Type.OPEN) {
                            try {
                                if (preview?.attachedSurfaceResolution != null) {
                                    previewResolution = preview.attachedSurfaceResolution!!
                                    //  val portrait = camera!!.cameraInfo.sensorRotationDegrees % 180 == 0
                                    val w = previewResolution?.width
                                    val h = previewResolution?.height



                                    dartMessenger.sendCameraInitializedEvent(
                                        w,
                                        h,
                                        ExposureMode.auto,
                                        FocusMode.auto,
                                        true,
                                        true
                                    )
                                }
                            } catch (e: Exception) {
                                dartMessenger.sendCameraErrorEvent(e.message)

                            }

                        } else if (state.type == CameraState.Type.CLOSED) {
                            dartMessenger.sendCameraClosingEvent()
                        }


                    })
                } catch (e: Exception) {
                    dartMessenger.sendCameraErrorEvent(e.message)

                }

                //result.success(map)
            } catch (e: Exception) {
                dartMessenger.sendCameraErrorEvent(e.message)
            }
        }, executor)

    }

    fun setFlashMode(
        result: MethodChannel.Result,
        mode: io.flutter.plugins.camera.types.FlashMode
    ) {

        // Get the flash availability
        val flashAvailable: Boolean = camera?.cameraInfo!!.hasFlashUnit()

        // Check if flash is available.

        // Check if flash is available.
        if (flashAvailable == null || !flashAvailable) {
            result.error("setFlashModeFailed", "Device does not have flash capabilities", null)
            return
        }

        updateFlash(mode)
    }

    private fun updateFlash(mode: io.flutter.plugins.camera.types.FlashMode) {

        when (mode) {
            FlashMode.off -> {
                camera?.cameraControl?.enableTorch(false);
                imageCapture!!.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            FlashMode.auto -> {
                camera?.cameraControl?.enableTorch(false);
                imageCapture!!.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            FlashMode.always -> {
                camera?.cameraControl?.enableTorch(false);
                imageCapture!!.flashMode = ImageCapture.FLASH_MODE_ON
            }
            FlashMode.torch -> {
                camera?.cameraControl?.enableTorch(true);

            }

        }
    }

    fun setCameraSelect(cameraId: String) {
        this.cameraSelector = CameraTools.getCameraSelector(cameraId)


    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)

    }

    fun dispose() {
        val owner = activity as LifecycleOwner
        camera?.cameraInfo?.torchState?.removeObservers(owner)
        camera?.cameraInfo?.zoomState?.removeObservers(owner)
        cameraProvider?.unbindAll()
        textureEntry?.release()
        camera = null
        textureEntry = null
        cameraProvider = null
        if (analysis != null) {
            analysis?.clearAnalyzer();
        }
    }

    fun startPreviewWithImageStream(imageStreamChannel: EventChannel) {
        imageStreamChannel.setStreamHandler(
            object : StreamHandler(), EventChannel.StreamHandler {
                override fun onListen(o: Any?, imageStreamSink: EventChannel.EventSink?) {
                    if (imageStreamSink != null) {
                        setImageStreamImageAvailableListener(imageStreamSink)
                    }
                }

                override fun onCancel(o: Any?) {
                    try {
                        if (analysis != null) {
                            analysis?.clearAnalyzer()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setImageStreamImageAvailableListener(imageStreamSink: EventChannel.EventSink) {

        if (analysis == null) return
        try {
            analysis?.clearAnalyzer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        analysis?.setAnalyzer(executor) { imageProxy ->


            try {
                onImageStream(imageProxy, imageStreamSink)


            } catch (e: Exception) {
                e.printStackTrace()
                print(e)
            }
        }


    }

    @SuppressLint("UnsafeOptInUsageError")
    fun onImageStream(img: ImageProxy, imageStreamSink: EventChannel.EventSink) {

        processImage(img, imageStreamSink);
    }

    fun takePicture(result: MethodChannel.Result) {


        val file = File(activity.cacheDir, "${System.currentTimeMillis()}.jpg")


        // Create output options object which contains file + metadata
        val outputOptions: ImageCapture.OutputFileOptions =
            ImageCapture.OutputFileOptions.Builder(file)

                .build()

        imageCapture!!.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    result.success(file.absolutePath);
                }

                override fun onError(exception: ImageCaptureException) {
                    // result.error(exception.message,exception);
                    result.error("cannotCapture", exception.message, null)


                }
            })


    }

    fun setFocusPoint(result: MethodChannel.Result, x: Double, y: Double) {

        /*  String modeStr = call.argument("mode");
                 FlashMode mode = FlashMode.getValueForString(modeStr);
                 if (mode == null) {
                     result.error("setFlashModeFailed", "Unknown flash mode " + modeStr, null);
                     return;
                 }
                 try {
                     camera.setFlashMode(result, mode);
                 } catch (Exception e) {
                     handleException(e, result);
                 }result.success(true)
          */


        val w = previewResolution?.height
        val h = previewResolution?.width

        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            w!!.toFloat(),
            h!!.toFloat()
        )

        /*val factory: MeteringPointFactory = DisplayOrientedMeteringPointFactory(
            cameraView.getDisplay(),
            camera?.cameraInfo!!,
            w!!.toFloat(),
            h!!.toFloat()
        )*/
        val point = factory.createPoint(
            (y.toFloat() * w),
            ((1 - x.toFloat()) * h)
        )
        try {
            val action = FocusMeteringAction.Builder(point) //.disableAutoCancel()
                //.setAutoCancelDuration(6, TimeUnit.SECONDS)
                .disableAutoCancel()
                .build()
            val resultListenableFuture: ListenableFuture<FocusMeteringResult>? =
                camera?.cameraControl?.startFocusAndMetering(action)
            //FocusTimeoutHandler focusTimeoutHandler = new FocusTimeoutHandler();
            //focusTimeoutHandler.startFocusTimeout(resultListenableFuture);
            //FocusTimeoutHandler focusTimeoutHandler = new FocusTimeoutHandler();
            //focusTimeoutHandler.startFocusTimeout(resultListenableFuture);
            resultListenableFuture?.addListener({
                try {
                    val focusMeteringResult = resultListenableFuture.get()
                    if (focusMeteringResult.isFocusSuccessful) {
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                } catch (e: java.lang.Exception) {
                    result.error("focusError", e.message, null)
                }
            }, executor)
        } catch (e1: java.lang.Exception) {
            //do nothing.
        }
    }


}


class Helper() {
    @SuppressLint("UnsafeOptInUsageError")
    fun call(img: ImageProxy): MutableMap<String, Any>? {
        if (img.image != null) {
            /* val planes: MutableList<Map<String, Any>> =
                 ArrayList()
             val buffer = BitmapHelper.yuv420ThreePlanesToNV21(
                 img.image!!.planes , img.width, img.height
             )


             val bytes = ByteArray(buffer.remaining())
             buffer[bytes, 0, bytes.size]
             val planeBuffer: MutableMap<String, Any> =
                 HashMap()
             // planeBuffer["bytesPerRow"] = plane.rowStride
             // planeBuffer["bytesPerPixel"] = plane.pixelStride
             planeBuffer["bytes"] = bytes
             planes.add(planeBuffer)

             val imageBuffer: MutableMap<String, Any> =
                 HashMap()
             imageBuffer["width"] = img.width
             imageBuffer["height"] = img.height
             imageBuffer["format"] = img.format
             imageBuffer["planes"] = planes

             //img.close()*/
            val planes: MutableList<Map<String, Any>> = ArrayList()
            for (plane in img.planes) {
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer[bytes, 0, bytes.size]
                val planeBuffer: MutableMap<String, Any> = java.util.HashMap()
                planeBuffer["bytesPerRow"] = plane.rowStride
                planeBuffer["bytesPerPixel"] = plane.pixelStride
                planeBuffer["bytes"] = bytes
                planes.add(planeBuffer)
            }

            val imageBuffer: MutableMap<String, Any> = java.util.HashMap()
            imageBuffer["width"] = img.width
            imageBuffer["height"] = img.height
            imageBuffer["format"] = img.format
            imageBuffer["planes"] = planes

            return imageBuffer;
        }
        return null
    }

}
