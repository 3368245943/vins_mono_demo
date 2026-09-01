package com.vell.vins;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.camera2.CameraCharacteristics;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.myapplication.R;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

public class Vins implements SensorEventListener, LocationListener {
    private static final String TAG = Vins.class.getSimpleName();
    private String configPath;
    private long cameraTimestampsShiftWrtSensors = 0;
    // Visual/gyro alignment measured on the target 2510DRK44C recording.
    private static final long CAMERA_IMU_TIME_OFFSET_NANOS = 20_500_000L;
    private double sensorTimestampDalta = -1;
    public final SlamRecorder slamRecorder = new SlamRecorder();

    public void init(Context context, CameraCharacteristics cameraCharacteristics) {
        configPath = prepareConfigPath(context);
        File appStorage = context.getExternalFilesDir(null);
        if (appStorage == null) {
            appStorage = context.getFilesDir();
        }
        slamRecorder.setRecordRootDir(new File(appStorage, "vins/record"));
        slamRecorder.setExportContext(context.getApplicationContext());
        cameraTimestampsShiftWrtSensors = ImageUtils.getCameraTimestampsShiftWrtSensors(cameraCharacteristics);
        VinsUtils.init(configPath);
    }

    private String prepareConfigPath(Context context) {
        File configDir = new File(context.getFilesDir(), "vins/config");
        if (!configDir.exists() && !configDir.mkdirs()) {
            Log.e(TAG, "Unable to create config dir: " + configDir);
        }
        copyRawResourceIfMissing(context, configDir, R.raw.brief_k10l6, "brief_k10l6.bin");
        copyRawResourceIfMissing(context, configDir, R.raw.brief_pattern, "brief_pattern.yml");
        return configDir.getAbsolutePath();
    }

    private void copyRawResourceIfMissing(Context context, File configDir, int rawId, String fileName) {
        File targetFile = new File(configDir, fileName);
        if (targetFile.exists() && targetFile.length() > 0) {
            return;
        }
        try (InputStream inputStream = context.getResources().openRawResource(rawId);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy " + fileName + " to " + targetFile, e);
        }
    }

    public boolean recvImage(final long imageTimestamp, final Mat originMat, boolean renderPreview,
                             Mat featurePreview) {
        double timeSec = (imageTimestamp + cameraTimestampsShiftWrtSensors
                + CAMERA_IMU_TIME_OFFSET_NANOS) / 1000000000.0 + sensorTimestampDalta;

        slamRecorder.recvImage(timeSec, originMat);
        return VinsUtils.recvImage(timeSec, originMat.nativeObj, renderPreview,
                featurePreview != null ? featurePreview.nativeObj : 0L);
    }

    public void recvImu(long timeNanos, final double ax, final double ay, final double az, final double gx, final double gy, final double gz) {
        double timeSec = timeNanos / 1000000000.0 + sensorTimestampDalta;

        slamRecorder.recvImu(timeSec, ax, ay, az, gx, gy, gz);
        VinsUtils.recvImu(timeSec, ax, ay, az, gx, gy, gz);
    }

    public void recvGPS(long timeNanos, final double latitude, final double longitude, final double altitude,
                        final double posAccuracy) {
        double timeSec = timeNanos / 1000000000.0 + sensorTimestampDalta;

        slamRecorder.recvGPS(timeSec, latitude, longitude, altitude, posAccuracy);
        VinsUtils.recvGPS(timeSec, latitude, longitude, altitude, posAccuracy);
    }

    private static final class GyroSample {
        final long timestamp;
        final float x;
        final float y;
        final float z;

        GyroSample(long timestamp, float x, float y, float z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final ArrayDeque<GyroSample> pendingGyro = new ArrayDeque<>();
    private final float[] previousAcceleration = new float[3];
    private final float[] currentAcceleration = new float[3];
    private long previousAccelerationTimestamp = Long.MIN_VALUE;
    private long currentAccelerationTimestamp = Long.MIN_VALUE;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (sensorTimestampDalta == -1) {
            sensorTimestampDalta = System.currentTimeMillis() / 1000.0 - event.timestamp / 1000000000.0;
        }
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                pendingGyro.addLast(new GyroSample(event.timestamp,
                        event.values[0], event.values[1], event.values[2]));
                dispatchSynchronizedImu();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (currentAccelerationTimestamp != Long.MIN_VALUE) {
                    System.arraycopy(currentAcceleration, 0, previousAcceleration, 0,
                            currentAcceleration.length);
                    previousAccelerationTimestamp = currentAccelerationTimestamp;
                }
                System.arraycopy(event.values, 0, currentAcceleration, 0,
                        currentAcceleration.length);
                currentAccelerationTimestamp = event.timestamp;
                dispatchSynchronizedImu();
                break;
            case Sensor.TYPE_PRESSURE:
                break;
            default:
                break;
        }
    }

    private void dispatchSynchronizedImu() {
        if (previousAccelerationTimestamp == Long.MIN_VALUE
                || currentAccelerationTimestamp <= previousAccelerationTimestamp) {
            return;
        }

        while (!pendingGyro.isEmpty()) {
            GyroSample gyro = pendingGyro.peekFirst();
            if (gyro.timestamp > currentAccelerationTimestamp) {
                return;
            }
            pendingGyro.removeFirst();
            if (gyro.timestamp < previousAccelerationTimestamp) {
                continue;
            }

            double alpha = (double) (gyro.timestamp - previousAccelerationTimestamp)
                    / (double) (currentAccelerationTimestamp - previousAccelerationTimestamp);
            double ax = previousAcceleration[0]
                    + alpha * (currentAcceleration[0] - previousAcceleration[0]);
            double ay = previousAcceleration[1]
                    + alpha * (currentAcceleration[1] - previousAcceleration[1]);
            double az = previousAcceleration[2]
                    + alpha * (currentAcceleration[2] - previousAcceleration[2]);
            recvImu(gyro.timestamp, ax, ay, az, gyro.x, gyro.y, gyro.z);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        double accuracy = location.getAccuracy();
        long timeNanos = location.getElapsedRealtimeNanos();

        recvGPS(timeNanos, latitude, longitude, altitude, accuracy);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
