package com.daasuu.gpuv.camerarecorder;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.daasuu.gpuv.egl.filter.GlFilter;


public class GPUCameraRecorderBuilder {


    private GLSurfaceView glSurfaceView;

    private LensFacing lensFacing = LensFacing.FRONT;
    private Resources resources;
    private Activity activity;
    private CameraRecordListener cameraRecordListener;
    private int fileWidth = 720;
    private int fileHeight = 1280;
    private boolean flipVertical = false;
    private boolean flipHorizontal = false;
    private boolean mute = false;
    private boolean recordNoFilter = false;
    private int cameraWidth = 1280;
    private int cameraHeight = 720;
    private GlFilter glFilter;


    public GPUCameraRecorderBuilder(Activity activity, GLSurfaceView glSurfaceView) {
        this.activity = activity;
        this.glSurfaceView = glSurfaceView;
        this.resources = activity.getResources();
    }

    private static Size getSize(CameraManager cameraManager, String cameraId) throws CameraAccessException {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] supportedSizes = configMap.getOutputSizes(MediaRecorder.class);

// Ưu tiên các độ phân giải theo thứ tự
        Size bestSize = null;
        int[][] priorityResolutions = {
                {3840, 2160}, // 4K
                {1920, 1080}, // Full HD
                {1280, 720},  // HD
                {720, 480}    // SD
        };

        for (int[] target : priorityResolutions) {
            for (Size size : supportedSizes) {
                if (size.getWidth() == target[0] && size.getHeight() == target[1]) {
                    bestSize = size;
                    break;
                }
            }
            if (bestSize != null) break;
        }

// Nếu không tìm thấy gì, lấy kích thước đầu tiên
        if (bestSize == null && supportedSizes.length > 0) {
            bestSize = supportedSizes[0];
        }
        return bestSize;
    }

    public GPUCameraRecorderBuilder cameraRecordListener(CameraRecordListener cameraRecordListener) {
        this.cameraRecordListener = cameraRecordListener;
        return this;
    }

    public GPUCameraRecorderBuilder filter(GlFilter glFilter) {
        this.glFilter = glFilter;
        return this;
    }

    public GPUCameraRecorderBuilder videoSize(int fileWidth, int fileHeight) {
        this.fileWidth = fileWidth;
        this.fileHeight = fileHeight;
        return this;
    }

    public GPUCameraRecorderBuilder cameraSize(int cameraWidth, int cameraHeight) {
        this.cameraWidth = cameraWidth;
        this.cameraHeight = cameraHeight;
        return this;
    }

    public GPUCameraRecorderBuilder lensFacing(LensFacing lensFacing) {
        this.lensFacing = lensFacing;
        return this;
    }

    public GPUCameraRecorderBuilder flipHorizontal(boolean flip) {
        this.flipHorizontal = flip;
        return this;
    }

    public GPUCameraRecorderBuilder flipVertical(boolean flip) {
        this.flipVertical = flip;
        return this;
    }

    public GPUCameraRecorderBuilder mute(boolean mute) {
        this.mute = mute;
        return this;
    }

    public GPUCameraRecorderBuilder recordNoFilter(boolean recordNoFilter) {
        this.recordNoFilter = recordNoFilter;
        return this;
    }

    public GPUCameraRecorder build() throws CameraAccessException {
        if (this.glSurfaceView == null) {
            throw new IllegalArgumentException("glSurfaceView and windowManager, multiVideoEffects is NonNull !!");
        }

        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        boolean isLandscapeDevice = resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        int degrees = 0;
        if (isLandscapeDevice) {
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            Log.d("GPUCameraRecorder", "Surface.ROTATION_90 = " + Surface.ROTATION_90 + " rotation = " + rotation);
            degrees = 90 * (rotation - 2);
        }

//        String cameraId = getCameraId(cameraManager, lensFacing);
//        Size bestSize = getSize(cameraManager, cameraId);
//
//        if (bestSize != null) {
//            cameraWidth = bestSize.getWidth();
//            cameraHeight = bestSize.getHeight();
//
//            fileWidth = cameraWidth;
//            fileHeight = cameraHeight;
//        }
//        Log.e("chungvv", "build: " + cameraWidth + " " + cameraHeight + " " + fileWidth + " " + fileHeight);
        GPUCameraRecorder GPUCameraRecorder = new GPUCameraRecorder(
                cameraRecordListener,
                glSurfaceView,
                fileWidth,
                fileHeight,
                cameraWidth,
                cameraHeight,
                lensFacing,
                flipHorizontal,
                flipVertical,
                mute,
                cameraManager,
                isLandscapeDevice,
                degrees,
                recordNoFilter
        );
        GPUCameraRecorder.setFilter(glFilter);
        activity = null;
        resources = null;
        return GPUCameraRecorder;
    }

    private String getCameraId(CameraManager manager, LensFacing lensFacing) throws CameraAccessException {
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing == LensFacing.FRONT && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId;
            } else if (lensFacing == LensFacing.BACK && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId;
            }
        }
        return manager.getCameraIdList()[0]; // fallback
    }

}
