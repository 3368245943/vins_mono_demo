package com.vell.vins;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.location.Criteria;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.example.myapplication.R;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class JavaCameraActivity extends Activity {
    private static final String TAG = JavaCameraActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_CODE = 12345;
    private final int imageWidth = 640;
    // The target camera exposes this 4:3 stream. Native code rotates it to 480x640.
    private final int imageHeight = 480;
    private ImageReader imageReader;
    private HandlerThread imageProcessingThread;
    private Handler imageProcessingHandler;
    private HandlerThread sensorProcessingThread;
    private Handler sensorProcessingHandler;
    private JavaCamera javaCamera;
    private Vins vins;
    private volatile boolean saveFrame = false;
    private File saveDir;
    private boolean useLocalImage = true;
    private long lastPreviewTimeNanos;
    // The native feature tracker normally takes 3-6 ms on the target device.
    // Keep the preview close to the 30 FPS camera stream instead of imposing
    // the previous 12 FPS UI cap.
    private static final long PREVIEW_INTERVAL_NANOS = 33_000_000L;
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        @Override
        public void onImageAvailable(ImageReader reader) {
            // get the newest frame
            Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }
//            Log.i(TAG,"get new image, height: " + image.getHeight() + " width: " + image.getWidth());
            Mat originMat = ImageUtils.getMatFromImage(image);

            final long now = SystemClock.elapsedRealtimeNanos();
            final boolean updatePreview = now - lastPreviewTimeNanos >= PREVIEW_INTERVAL_NANOS;
            final Mat cameraMat = updatePreview ? originMat.clone() : null;

            final boolean mapRendered = vins != null &&
                    vins.recvImage(image.getTimestamp(), originMat, updatePreview, cameraMat);

            final boolean captureFrame = saveFrame;
            if (!updatePreview && !captureFrame) {
                originMat.release();
                image.close();
                return;
            }

            if (updatePreview) {
                lastPreviewTimeNanos = now;
            }
            final Bitmap originBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(originMat, originBitmap);
            final Bitmap cameraBitmap;
            if (cameraMat != null) {
                cameraBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.RGB_565);
                Utils.matToBitmap(cameraMat, cameraBitmap);
                cameraMat.release();
            } else {
                cameraBitmap = null;
            }
            if (captureFrame) {
                try {
                    saveFrame = false;
                    if (!saveDir.exists()) {
                        saveDir.mkdirs();
                    }
                    File frameFile = new File(saveDir, String.format("%s.jpg", new Date().toString()));

                    originBitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(frameFile));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

            if (updatePreview) {
                final boolean tracking = VinsUtils.initSucess();
                final int initProgress = VinsUtils.getInitProgress();
                final int initFailures = VinsUtils.getInitFailureCount();
                final float[] pos = tracking ? VinsUtils.getLatestPosition() : null;
                final float[] ang = tracking ? VinsUtils.getLatestEulerAngles() : null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mapRendered) {
                            ((ImageView) findViewById(R.id.java_camera_view)).setImageBitmap(originBitmap);
                        }
                        ImageView cameraInset = findViewById(R.id.camera_inset);
                        if (cameraBitmap != null) {
                            cameraInset.setImageBitmap(cameraBitmap);
                        }
                        ((TextView) findViewById(R.id.tv_status)).setText(
                                !VinsUtils.isNativeReady() ? "VINS LIBRARY ERROR"
                                : tracking ? "TRACKING" : initFailures > 0
                                        ? String.format(Locale.US, "RETRYING (%d)", initFailures)
                                        : initProgress < 30 ? "COLLECTING IMU" : "INITIALIZING");
                        ((TextView) findViewById(R.id.tv_info)).setText(tracking
                                ? String.format(Locale.US,
                                "POS %+.2f %+.2f %+.2f   ANG %+.1f %+.1f %+.1f",
                                pos[0], pos[1], pos[2], ang[0], ang[1], ang[2])
                                : "POS -- -- --   ANG -- -- --");
                    }
                });
            }
            originMat.release();
            image.close();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_java_camera);
        applySystemBarInsets();
        // first make sure the necessary permissions are given
        checkPermissionsIfNeccessary();
        File appStorage = getExternalFilesDir(null);
        if (appStorage == null) {
            appStorage = getFilesDir();
        }
        saveDir = new File(appStorage, "vins/capture");

        // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
        imageProcessingThread = new HandlerThread("VinsImageProcessing");
        imageProcessingThread.start();
        imageProcessingHandler = new Handler(imageProcessingThread.getLooper());
        sensorProcessingThread = new HandlerThread("VinsSensorProcessing");
        sensorProcessingThread.start();
        sensorProcessingHandler = new Handler(sensorProcessingThread.getLooper());
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 2);

        imageReader.setOnImageAvailableListener(onImageAvailableListener, imageProcessingHandler);

        javaCamera = new JavaCamera();
        javaCamera.addImageReader(imageReader);

        findViewById(R.id.save_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFrame = true;
            }
        });
        findViewById(R.id.record).setOnClickListener(new View.OnClickListener() {
            boolean isRecording = false;

            @Override
            public void onClick(View v) {
                if (isRecording) {
                    vins.slamRecorder.stopRecord();
                    Toast.makeText(JavaCameraActivity.this,
                            "正在保存到 下载/VINS", Toast.LENGTH_LONG).show();
                } else {
                    vins.slamRecorder.startRecord();
                    Toast.makeText(JavaCameraActivity.this,
                            "录制开始，完成后保存到 下载/VINS",
                            Toast.LENGTH_LONG).show();
                }
                isRecording = vins.slamRecorder.isRecording();
                ((TextView) v).setText(isRecording ? "停止" : "录像");
            }
        });
        vins = new Vins();

        subscribeToImuUpdates(vins, SensorManager.SENSOR_DELAY_FASTEST);
        subscribeToLocationUpdates(vins, 20);

        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        registerGnssStatusListener(locationManager);
    }

    private void applySystemBarInsets() {
        final View root = findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                updateHudMargins(R.id.left_hud, insets);
                updateHudMargins(R.id.right_hud, insets);
                updateBottomHudMargin(insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private void updateHudMargins(int id, WindowInsets insets) {
        View hud = findViewById(id);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) hud.getLayoutParams();
        params.topMargin = insets.getSystemWindowInsetTop();
        params.bottomMargin = insets.getSystemWindowInsetBottom();
        hud.setLayoutParams(params);
    }

    private void updateBottomHudMargin(int inset) {
        View hud = findViewById(R.id.bottom_hud);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) hud.getLayoutParams();
        params.bottomMargin = inset;
        hud.setLayoutParams(params);
    }

    @Override
    protected void onResume() {
        super.onResume();
        javaCamera.open(this, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                try {
                    vins.init(JavaCameraActivity.this, javaCamera.getCameraCharacteristics());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(CameraDevice camera) {

            }

            @Override
            public void onError(CameraDevice camera, int error) {

            }
        });
    }

    @Override
    protected void onPause() {
        javaCamera.close();
        VinsUtils.shutdown();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (imageReader != null) {
            imageReader.close();
        }
        if (imageProcessingThread != null) {
            imageProcessingThread.quitSafely();
        }
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(vins);
        if (sensorProcessingThread != null) {
            sensorProcessingThread.quitSafely();
        }
        super.onDestroy();
    }

    /**
     * @return true if permissions where given
     */
    private boolean checkPermissionsIfNeccessary() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                List<String> permissionsNotGrantedYet = new ArrayList<>(info.requestedPermissions.length);
                for (String p : info.requestedPermissions) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNotGrantedYet.add(p);
                    }
                }
                if (permissionsNotGrantedYet.size() > 0) {
                    ActivityCompat.requestPermissions(this, permissionsNotGrantedYet.toArray(new String[permissionsNotGrantedYet.size()]),
                            PERMISSIONS_REQUEST_CODE);
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean hasAllPermissions = true;
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length == 0)
                hasAllPermissions = false;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    hasAllPermissions = false;
            }

            if (!hasAllPermissions) {
                finish();
            }
        }
    }

    private void subscribeToImuUpdates(SensorEventListener listener, int delay) {
        final SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gyroscope != null) {
            sm.registerListener(listener, gyroscope, delay, sensorProcessingHandler);
        }
        if (accelerometer != null) {
            sm.registerListener(listener, accelerometer, delay, sensorProcessingHandler);
        }
    }

    private void subscribeToLocationUpdates(LocationListener listener, long minTimeMsec) {
        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        final String bestProvider = locationManager.getBestProvider(new Criteria(), false);
        if (bestProvider != null) {
            locationManager.requestLocationUpdates(bestProvider, minTimeMsec, 0.01f, listener);
        } else {
            Log.w(TAG, "No location provider available");
        }
    }

    private void registerGnssStatusListener(final LocationManager locationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.registerGnssStatusCallback(new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    updateGpsSatelliteCount(status.getSatelliteCount());
                }
            });
            return;
        }

        locationManager.addGpsStatusListener(new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                GpsStatus status = locationManager.getGpsStatus(null);
                int count = 0;
                if (status != null && event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                    Iterator<GpsSatellite> satellites = status.getSatellites().iterator();
                    while (satellites.hasNext() && count < status.getMaxSatellites()) {
                        satellites.next();
                        count++;
                    }
                }
                updateGpsSatelliteCount(count);
            }
        });
    }

    private void updateGpsSatelliteCount(final int count) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.tv_gps_info)).setText("gps num: " + count);
            }
        });
    }

    static {
        System.loadLibrary("opencv_java3");
    }
}
