//
// Created by vell on 19-2-23.
//
#include "android_wrapper.h"

#include <mutex>

namespace {
std::mutex lifecycleMutex;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_nativeRecvImu(JNIEnv *env, jclass type, jdouble timeSec, jdouble ax,
                                     jdouble ay, jdouble az, jdouble gx, jdouble gy, jdouble gz) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    if (inited) {
        ImuPtr imu_msg(new IMU_MSG());
        imu_msg->header = timeSec;
        imu_msg->acc << ax, ay, az;
        imu_msg->gyr << gx, gy, gz;
        viewControllerGlobal->recv_imu(imu_msg);
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_vell_vins_VinsUtils_nativeRecvImage(JNIEnv *env, jclass type, jdouble timeS, jlong rgbaPtr,
                                              jboolean renderPreview, jlong featurePreviewPtr) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    if (inited) {
        cv::Mat &cameraFrame = *(cv::Mat *) rgbaPtr;
        Mat rotatedCameraFrame;
        Mat rotatedFeaturePreview;
        cv::rotate(cameraFrame, rotatedCameraFrame, cv::ROTATE_90_CLOCKWISE);
        viewControllerGlobal->processImage(rotatedCameraFrame, timeS, false, renderPreview,
                                           featurePreviewPtr != 0 ? &rotatedFeaturePreview : nullptr);
        if (featurePreviewPtr != 0 && !rotatedFeaturePreview.empty()) {
            cv::Mat &featurePreview = *(cv::Mat *) featurePreviewPtr;
            Mat previewLandscape;
            cv::rotate(rotatedFeaturePreview, previewLandscape, cv::ROTATE_90_COUNTERCLOCKWISE);
            if (previewLandscape.size() != featurePreview.size()) {
                cv::resize(previewLandscape, previewLandscape, featurePreview.size());
            }
            previewLandscape.copyTo(featurePreview);
        }
        Mat preview;
        cv::rotate(rotatedCameraFrame, preview, cv::ROTATE_90_COUNTERCLOCKWISE);
        if (preview.type() == cameraFrame.type()) {
            // The 3D trajectory renderer outputs 640x360 while Camera2 supplies
            // 640x480. Return the rendered map instead of silently discarding it.
            if (preview.size() != cameraFrame.size()) {
                cv::resize(preview, preview, cameraFrame.size(), 0, 0, cv::INTER_LINEAR);
            }
            preview.copyTo(cameraFrame);
        } else {
            LOGE("Preview type mismatch: rendered=%d camera=%d", preview.type(),
                 cameraFrame.type());
        }
        return viewControllerGlobal->lastFrameIsMap() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_nativeRecvGPS(JNIEnv *env, jclass type, jdouble timeSec, jdouble latitude,
                                     jdouble longitude, jdouble altitude, jdouble posAccuracy) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    if (inited) {
        if (viewControllerGlobal->globalOptimization != nullptr) {
            viewControllerGlobal->globalOptimization->inputGPS(timeSec, latitude, longitude,
                                                               altitude, posAccuracy);
        }
    }

}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_nativeInit(JNIEnv *env, jclass type, jstring configPath_) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    const char *configPath = env->GetStringUTFChars(configPath_, 0);
    if (!inited) {
        viewControllerGlobal = std::unique_ptr<ViewController>(new ViewController);
        LOGI("Successfully created Viewcontroller Object");

        if (configPath != nullptr && configPath[0] != '\0') {
            viewControllerGlobal->setConfigDir(configPath);
        }

        viewControllerGlobal->testMethod();

        // startup method of ViewController
        viewControllerGlobal->viewDidLoad();
        inited = true;
    }
    env->ReleaseStringUTFChars(configPath_, configPath);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_nativeShutdown(JNIEnv *env, jclass type) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    if (!inited) {
        return;
    }
    inited = false;
    viewControllerGlobal.reset();
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_nativeGetLatestPosition(JNIEnv *env, jclass type) {
    Vector3f pos = viewControllerGlobal->getLatestPosition();
    jfloat posJ[3] = {
            pos(0), pos(1), pos(2)
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, posJ);
    return ret;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_nativeGetLatestRotation(JNIEnv *env, jclass type) {

    Matrix3f rot = viewControllerGlobal->getLatestRotation();
    // 旋转90度
    Eigen::Matrix3f RIC = Utility::ypr2R(Vector3d(0.0f, 90.0f, 0.0f)).cast<float>();
    rot = rot * RIC;
    jfloat posJ[9] = {
            rot(0, 0), rot(0, 1), rot(0, 2),
            rot(1, 0), rot(1, 1), rot(1, 2),
            rot(2, 0), rot(2, 1), rot(2, 2)
    };
    jfloatArray ret = env->NewFloatArray(9);
    env->SetFloatArrayRegion(ret, 0, 9, posJ);
    return ret;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_nativeGetLatestEulerAngles(JNIEnv *env, jclass type) {

    Matrix3f rot = viewControllerGlobal->getLatestRotation();
    Eigen::Matrix3f RIC = Utility::ypr2R(Vector3d(0.0f, 90.0f, 0.0f)).cast<float>();
    rot = rot * RIC;
    Vector3d angles = Utility::R2ypr(rot.cast<double>());
    jfloat retJ[3] = {
            static_cast<jfloat>(angles(0)),
            static_cast<jfloat>(angles(1)),
            static_cast<jfloat>(angles(2))
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, retJ);
    return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_vell_vins_VinsUtils_nativeInitSucess(JNIEnv *env, jclass type) {
    if (inited) {
        return (jboolean) viewControllerGlobal->initSucess();
    }
    return (jboolean) false;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_vell_vins_VinsUtils_nativeGetInitProgress(JNIEnv *env, jclass type) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    return inited ? static_cast<jint>(viewControllerGlobal->getInitProcess()) : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_vell_vins_VinsUtils_nativeGetInitFailureCount(JNIEnv *env, jclass type) {
    std::lock_guard<std::mutex> lock(lifecycleMutex);
    return inited ? static_cast<jint>(viewControllerGlobal->getInitFailureCount()) : 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_vell_vins_VinsUtils_nativeEnableAR(JNIEnv *env, jclass type, jboolean isAR) {
    if (inited) {
        viewControllerGlobal->enableAR(isAR);
    }
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_nativeGetLatestGroundCenter(JNIEnv *env, jclass type) {
    Vector3f pos = viewControllerGlobal->getLatestGroundCenter();
    jfloat posJ[3] = {
            pos(0), pos(1), pos(2)
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, posJ);
    return ret;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_vell_vins_VinsUtils_nativeGetLatestGPS(JNIEnv *env, jclass type) {
    Vector3d pos = viewControllerGlobal->getLatestPosition().cast<double>();
    double latitude;
    double longitude;
    double altitude;
    viewControllerGlobal->globalOptimization->XYZ2GPS(pos.data(), latitude, longitude, altitude);
    jfloat posJ[3] = {
            (float) latitude, (float) longitude, (float) altitude
    };
    jfloatArray ret = env->NewFloatArray(3);
    env->SetFloatArrayRegion(ret, 0, 3, posJ);
    return ret;

}
