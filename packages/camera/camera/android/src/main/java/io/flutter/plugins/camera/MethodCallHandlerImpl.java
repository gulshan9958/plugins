// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;

import camrax.camera.FlCameraX;
import fl.camera.CameraTools;
import camrax.camera.FlCameraEvent;
import io.flutter.embedding.engine.systemchannels.PlatformChannel;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugins.camera.CameraPermissions.PermissionsRegistry;
import io.flutter.plugins.camera.types.ExposureMode;
import io.flutter.plugins.camera.types.FlashMode;
import io.flutter.plugins.camera.types.FocusMode;
import io.flutter.view.TextureRegistry;

import java.util.HashMap;
import java.util.Map;

final class MethodCallHandlerImpl implements MethodChannel.MethodCallHandler {
    private final Activity activity;
    private final BinaryMessenger messenger;

    private final CameraPermissions cameraPermissions;
    private final PermissionsRegistry permissionsRegistry;
    private final TextureRegistry textureRegistry;
    private final MethodChannel methodChannel;
    private final EventChannel imageStreamChannel;


    private FlCameraX flCamera = null;


    MethodCallHandlerImpl(
            Activity activity,
            BinaryMessenger messenger,
            CameraPermissions cameraPermissions,
            PermissionsRegistry permissionsAdder,

            TextureRegistry textureRegistry) {
        this.activity = activity;
        this.messenger = messenger;
        this.cameraPermissions = cameraPermissions;
        this.permissionsRegistry = permissionsAdder;
        this.textureRegistry = textureRegistry;

        methodChannel = new MethodChannel(messenger, "plugins.flutter.io/camera");
        imageStreamChannel = new EventChannel(messenger, "plugins.flutter.io/camera/imageStream");
        methodChannel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
        switch (call.method) {
            case "availableCameras":
                try {
                    result.success(CameraUtils.getAvailableCameras(activity));
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            case "create": {
                if (flCamera != null) {
                    flCamera.dispose();
                }
                cameraPermissions.requestPermissions(
                        activity,
                        permissionsRegistry,
                        call.argument("enableAudio"),
                        (String errCode, String errDesc) -> {
                            if (errCode == null) {
                                try {
                                    instantiateCamera(call, result);
                                } catch (Exception e) {
                                    handleException(e, result);
                                }
                            } else {
                                result.error(errCode, errDesc, null);
                            }

                        });
                break;
            }
            case "initialize": {

                if (flCamera != null) {
                    try {
                        startPreview(null, call, result);
                        result.success(null);
                    } catch (Exception e) {
                        handleException(e, result);
                        handleException(e, result);
                    }
                } else {
                    result.error(
                            "cameraNotFound",
                            "Camera not found. Please call the 'create' method before calling 'initialize'.",
                            null);
                }
                break;
            }
            case "takePicture": {
                flCamera.takePicture(result);
                break;
            }
            case "prepareForVideoRecording": {
                // This optimization is not required for Android.
                result.success(null);
                break;
            }
            case "startVideoRecording": {
                // camera.startVideoRecording(result);
                break;
            }
            case "stopVideoRecording": {
                //  camera.stopVideoRecording(result);
                break;
            }
            case "pauseVideoRecording": {
                //  camera.pauseVideoRecording(result);
                break;
            }
            case "resumeVideoRecording": {
                //   camera.resumeVideoRecording(result);
                break;
            }
            case "setFlashMode": {
                String modeStr = call.argument("mode");
                FlashMode mode = FlashMode.getValueForString(modeStr);
                if (mode == null) {
                    result.error("setFlashModeFailed", "Unknown flash mode " + modeStr, null);
                    return;
                }
                boolean hasFlash = true;
                try {
                    hasFlash = flCamera.setFlashMode(result, mode);
                } catch (Exception e) {
                    handleException(e, result);
                }
                if(hasFlash){
                    result.success(null);
                }
                break;
            }
            case "setExposureMode": {
                result.success(null);
               /* String modeStr = call.argument("mode");
               // ExposureMode mode = ExposureMode.getValueForString(modeStr);
                if (mode == null) {
                    result.error("setExposureModeFailed", "Unknown exposure mode " + modeStr, null);
                    return;
                }
                try {
                  //  camera.setExposureMode(result, mode);
                } catch (Exception e) {
                    handleException(e, result);
                }*/
                break;
            }
            case "setExposurePoint": {
                Boolean reset = call.argument("reset");
                Double x = null;
                Double y = null;
                if (reset == null || !reset) {
                    x = call.argument("x");
                    y = call.argument("y");
                }
                try {
                    result.success(null);
                   // camera.setExposurePoint(result, x, y);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "getMinExposureOffset": {
                try {
                    result.success(0.0);
                   // result.success(camera.getMinExposureOffset());
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "getMaxExposureOffset": {
                try {
                    result.success(0.0);
                   // result.success(camera.getMaxExposureOffset());
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "getExposureOffsetStepSize": {
                try {
                    result.success(null);
                   // result.success(camera.getExposureOffsetStepSize());
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "setExposureOffset": {
                try {
                    result.success(0.0);
                 //   camera.setExposureOffset(result, call.argument("offset"));
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "setFocusMode": {
                result.success(null);
                break;
            }
            case "setFocusPoint": {



                Boolean reset = call.argument("reset");
                Double x = null;
                Double y = null;
                if (reset == null || !reset) {
                    x = call.argument("x");
                    y = call.argument("y");
                }
                try {
                    flCamera.setFocusPoint(result, x, y);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "startImageStream": {
                try {
                    flCamera.startPreviewWithImageStream(imageStreamChannel);
                    result.success(null);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "stopImageStream": {
                try {
                    if (flCamera.getanalysis() != null) {
                        flCamera.getanalysis().clearAnalyzer();
                    }
                    result.success(null);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "getMaxZoomLevel": {
                assert flCamera != null;

                try {
                    float maxZoomLevel = 0;
                    result.success(maxZoomLevel);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "getMinZoomLevel": {
                assert flCamera != null;

                try {
                    float minZoomLevel = 0;
                    result.success(minZoomLevel);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "setZoomLevel": {
                assert flCamera != null;

                Double zoom = call.argument("zoom");

                if (zoom == null) {
                    result.error(
                            "ZOOM_ERROR", "setZoomLevel is called without specifying a zoom level.", null);
                    return;
                }

                try {
                    result.success(null);
                   // flCamera.setZoomLevel(result, zoom.floatValue());
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "lockCaptureOrientation": {
                PlatformChannel.DeviceOrientation orientation =
                        CameraUtils.deserializeDeviceOrientation(call.argument("orientation"));

                try {
                    // camera.lockCaptureOrientation(orientation);
                    result.success(null);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "unlockCaptureOrientation": {
                try {


                    // camera.unlockCaptureOrientation();
                    result.success(null);
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            }
            case "dispose": {
                if (flCamera != null) {
                    flCamera.dispose();
                }
                result.success(null);
                break;
            }
            default:
                result.notImplemented();
                break;
        }
    }


    void stopListening() {
        methodChannel.setMethodCallHandler(null);
    }

    private void instantiateCamera(MethodCall call, Result result) throws CameraAccessException {
        String cameraName = call.argument("cameraName");
        String resolutionPreset = call.argument("resolutionPreset");
        boolean enableAudio = call.argument("enableAudio");

        TextureRegistry.SurfaceTextureEntry flutterSurfaceTexture =
                textureRegistry.createSurfaceTexture();
        DartMessenger dartMessenger =
                new DartMessenger(
                        messenger, flutterSurfaceTexture.id(), new Handler(Looper.getMainLooper()));
        /*camera =
                new Camera(
                        activity,
                        flutterSurfaceTexture,
                        dartMessenger,
                        cameraName,
                        resolutionPreset,
                        enableAudio);*/
        flCamera =
                new FlCameraX(activity, flutterSurfaceTexture, dartMessenger);
        flCamera.setCameraSelect(cameraName);
        dartMessenger.sendDeviceOrientationChangeEvent(PlatformChannel.DeviceOrientation.PORTRAIT_UP);

        Map<String, Object> reply = new HashMap<>();
        reply.put("cameraId", flutterSurfaceTexture.id());
        result.success(reply);

    }

    public void startPreview(
            ImageAnalysis.Analyzer imageAnalyzer,
            MethodCall call,
            MethodChannel.Result result
    ) {

        // A variable number of use-cases can be passed here -
        // camera provides access to CameraControl & CameraInfo

        //val resolution = call.argument<String>("resolution")
        // val cameraId = call.argument<String>("cameraId")
        // String previewSize = new CameraTools().computeBestPreviewSize(cameraId, resolution)
        if (flCamera != null) {
            flCamera.initCameraX(result, imageAnalyzer);
        }
    }

    // We move catching CameraAccessException out of onMethodCall because it causes a crash
    // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
    // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
    @SuppressWarnings("ConstantConditions")
    private void handleException(Exception exception, Result result) {
        if (exception instanceof CameraAccessException) {
            result.error("CameraAccess", exception.getMessage(), null);
            return;
        }

        // CameraAccessException can not be cast to a RuntimeException.
        throw (RuntimeException) exception;
    }
}
