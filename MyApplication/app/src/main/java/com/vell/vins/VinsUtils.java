package com.vell.vins;

public class VinsUtils {
    private static boolean nativeReady;

    static {
        try {
            System.loadLibrary("vins_android_wrapper");
            nativeReady = true;
        } catch (UnsatisfiedLinkError e) {
            nativeReady = false;
        }
    }

    public static void recvImu(double timeSec, double ax, double ay, double az, double gx, double gy, double gz) {
        if (nativeReady) {
            nativeRecvImu(timeSec, ax, ay, az, gx, gy, gz);
        }
    }

    public static boolean recvImage(double timeSec, long rgbaPtr, boolean renderPreview,
                                    long featurePreviewPtr) {
        if (nativeReady) {
            return nativeRecvImage(timeSec, rgbaPtr, renderPreview, featurePreviewPtr);
        }
        return false;
    }

    public static void recvGPS(double timeSec, double latitude, double longitude, double altitude,
                               double posAccuracy) {
        if (nativeReady) {
            nativeRecvGPS(timeSec, latitude, longitude, altitude, posAccuracy);
        }
    }

    public static void init(String configPath) {
        if (nativeReady) {
            nativeInit(configPath);
        }
    }

    public static void shutdown() {
        if (nativeReady) {
            nativeShutdown();
        }
    }

    public static float[] getLatestPosition() {
        return nativeReady ? nativeGetLatestPosition() : new float[]{0f, 0f, 0f};
    }

    public static float[] getLatestGPS() {
        return nativeReady ? nativeGetLatestGPS() : new float[]{0f, 0f, 0f};
    }

    public static float[] getLatestRotation() {
        return nativeReady ? nativeGetLatestRotation() : new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
    }

    public static float[] getLatestEulerAngles() {
        return nativeReady ? nativeGetLatestEulerAngles() : new float[]{0f, 0f, 0f};
    }

    public static float[] getLatestGroundCenter() {
        return nativeReady ? nativeGetLatestGroundCenter() : new float[]{0f, 0f, 0f};
    }

    public static void enableAR(boolean isAR) {
        if (nativeReady) {
            nativeEnableAR(isAR);
        }
    }

    public static boolean initSucess() {
        return nativeReady && nativeInitSucess();
    }

    public static int getInitProgress() {
        return nativeReady ? nativeGetInitProgress() : 0;
    }

    public static int getInitFailureCount() {
        return nativeReady ? nativeGetInitFailureCount() : 0;
    }

    public static boolean isNativeReady() {
        return nativeReady;
    }

    private static native void nativeRecvImu(double timeSec, double ax, double ay, double az, double gx, double gy, double gz);
    private static native boolean nativeRecvImage(double timeSec, long rgbaPtr, boolean renderPreview,
                                                  long featurePreviewPtr);
    private static native void nativeRecvGPS(double timeSec, double latitude, double longitude, double altitude,
                                             double posAccuracy);
    private static native void nativeInit(String configPath);
    private static native void nativeShutdown();
    private static native float[] nativeGetLatestPosition();
    private static native float[] nativeGetLatestGPS();
    private static native float[] nativeGetLatestRotation();
    private static native float[] nativeGetLatestEulerAngles();
    private static native float[] nativeGetLatestGroundCenter();
    private static native void nativeEnableAR(boolean isAR);
    private static native boolean nativeInitSucess();
    private static native int nativeGetInitProgress();
    private static native int nativeGetInitFailureCount();
}
