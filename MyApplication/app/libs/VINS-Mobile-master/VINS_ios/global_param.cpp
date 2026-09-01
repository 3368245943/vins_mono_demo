//
//  global_param.cpp
//  VINS_ios
//
//  Created by HKUST Aerial Robotics on 2017/05/09.
//  Copyright © 2017 HKUST Aerial Robotics. All rights reserved.
//

#include <stdio.h>
#include "global_param.hpp"

double FOCUS_LENGTH_Y;
double PY;
double FOCUS_LENGTH_X;
double PX;
double SOLVER_TIME;
int FREQ;

//extrinsic param
double TIC_X;
double TIC_Y;
double TIC_Z;
double RIC_y;
double RIC_p;
double RIC_r;

bool setGlobalParam(DeviceType device)
{
    RIC_y = 0.0;
    RIC_p = 0.0;
    RIC_r = 180.0;
    switch (device) {
        case iPhone7P:
            printf("Device iPhone7 plus param\n");
            FOCUS_LENGTH_X = 526.600;
            FOCUS_LENGTH_Y = 526.678;
            PX = 243.481;
            PY = 315.280;
            
            SOLVER_TIME = 0.06;
            FREQ = 3;
            
            TIC_X = 0.0;
            TIC_Y = 0.092;
            TIC_Z = 0.01;
            return true;
            break;
            
        case iPhone7:
            printf("Device iPhone7 param\n");
            FOCUS_LENGTH_X = 526.958;
            FOCUS_LENGTH_Y = 527.179;
            PX = 244.473;
            PY = 313.844;
            SOLVER_TIME = 0.06;
            FREQ = 3;
            
            //extrinsic param
            TIC_X = 0.0;
            TIC_Y = 0.092;
            TIC_Z = 0.01;
            return true;
            break;
            
        case iPhone6s:
            printf("Device iPhone6s param\n");
            FOCUS_LENGTH_Y = 549.477;
            PY = 320.379;
            FOCUS_LENGTH_X = 548.813;
            PX = 238.520;
            SOLVER_TIME = 0.06;
            FREQ = 3;
            
            //extrinsic param
            TIC_X = 0.0;
            TIC_Y = 0.065;
            TIC_Z = 0.0;
            return true;
            break;
            
        case iPhone6sP:
            printf("Device iPhone6sP param\n");
            FOCUS_LENGTH_X = 547.565;
            FOCUS_LENGTH_Y = 547.998;
            PX = 239.033;
            PY = 309.452;
            
            SOLVER_TIME = 0.06;
            FREQ = 3;
            
            //extrinsic param
            TIC_X = 0.0;
            TIC_Y = 0.065;
            TIC_Z = 0.0;
            return true;
            break;
            
        case iPadPro97:
            printf("Device ipad97 param\n");
            FOCUS_LENGTH_X = 547.234;
            FOCUS_LENGTH_Y = 547.464;
            PX = 241.549;
            PY = 317.957;
            
            SOLVER_TIME = 0.06;
            FREQ = 3;
            
            //extrinsic param
            TIC_X = 0.0;
            TIC_Y = 0.092;
            TIC_Z = 0.1;
            return true;
            break;
            
        case iPadPro129:
            printf("Device iPad129 param\n");
            FOCUS_LENGTH_X = 547.234;
            FOCUS_LENGTH_Y = 547.464;
            PX = 241.549;
            PY = 317.957;
            
            SOLVER_TIME = 0.06;
            FREQ = 3;
            
            //extrinsic param
            TIC_X = 0.0;
            TIC_Y = 0.092;
            TIC_Z = 0.1;
            return true;
            break;

        case GalaxyS7:
            printf("Device GalaxyS7 param\n");
            FOCUS_LENGTH_X = 478.7620;
            FOCUS_LENGTH_Y = 478.6281;
            PX = 231.9446;
            PY = 319.5067;

            SOLVER_TIME = 1.0; // 0.06 TODO
            FREQ = 3;

            //extrinsic param
            // TIC = Translation IMU to Camera
            TIC_X = 0.0; // view direction
            TIC_Y = -0.0045; //-0.005 // to the left when device is in landscape (up when normal)
            TIC_Z = -0.01505; //-0.018792; // up when in landscape (right when normal)
            return true;
            break;
        case Mix2:
            // Camera2 intrinsic calibration for the target 2510DRK44C main camera.
            // The 4096x3072 active array is scaled to 640x480, then rotated clockwise
            // before reaching the tracker as a 480x640 image.
            FOCUS_LENGTH_X = 439.0891;
            FOCUS_LENGTH_Y = 439.0941;
            PX = 257.0006;
            PY = 310.1405;

            // This device needs about 55 ms just to complete the first Ceres
            // With the estimator compiled at -O3, tracking solves in roughly
            // 16-35 ms on this device. Keep the intended 10 Hz backend rate;
            // the camera-rate live pose fills the intervals between updates.
            SOLVER_TIME = 0.06;
            FREQ = 3;

            // No camera-to-gyroscope translation is published by this device.  Do not
            // reuse the unrelated iPhone calibration that previously lived here.
            TIC_X = 0.0;
            TIC_Y = 0.0;
            TIC_Z = 0.0;

            // CameraCharacteristics reports LENS_POSE_ROTATION
            // [-0.707107, 0.707107, 0, 0] (x,y,z,w) for the main camera.
            // After the clockwise image rotation performed in android_wrapper,
            // the tracker-camera to device-IMU rotation is exactly Rx(180 deg).
            // The previous hand-eye result contained a spurious 10.2 degree
            // pitch, which projected gravity into horizontal acceleration as
            // soon as the phone rotated.
            RIC_y = 0.0;
            RIC_p = 0.0;
            RIC_r = 180.0;
            return true;
            break;
        case unDefine:
            return false;
            break;
        default:
            return false;
            break;
    }
}
