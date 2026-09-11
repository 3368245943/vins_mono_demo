# VINS-Mobile Android VIO

这是基于 HKUST Aerial Robotics Group VINS-Mobile 的 Android 单目视觉惯性里程计项目，
当前参数针对 Xiaomi 2510DRK44C 调试。项目提供相机特征监测、VIO 初始化、主视角建图、
相机副视角、位姿显示、IMU/GNSS 录制和 ZIP 导出。

## 本版本整改

- 使用校准陀螺仪数据，修复 Android `ASENSOR_TYPE_GYROSCOPE` 错读
  `uncalibrated_gyro` 的问题；
- 对加速度和陀螺仪按时间戳插值配对，避免两个传感器时间戳不完全相等时丢失 IMU；
- 保留加速度偏置在线估计，固定已校准陀螺仪偏置，降低静止时位置积分漂移；
- 使用目标设备 Camera2 pose rotation 对应的相机外参和 `+20.5 ms` 相机/IMU 时间偏移；
- 使用 640x480 相机输入并在 native 层统一旋转到 tracker 坐标；
- native VINS 使用 `-O3`，减少高频日志和地图渲染复制，降低实时延迟；
- 初始化失败或跟踪失败时清理失效地图状态，避免旧世界坐标继续污染新轨迹；
- 录制文件导出到系统“下载/VINS”目录，便于取出和复现问题。

## 构建

环境要求：Android Studio、JDK 11、Android SDK 37、NDK `21.4.7075529`、CMake `3.22.1`，
目标 ABI 为 `arm64-v8a`。

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 使用与移植

启动后允许相机、定位和高采样率传感器权限。保持场景有纹理并平稳移动，完成初始化后主视角
显示地图，副视角持续显示相机监测点。录制结束后导出 ZIP 到“下载/VINS”。

移植到其他设备必须重新确认相机内参、图像旋转、Camera2 `LENS_POSE_ROTATION`、相机/IMU
时间偏移以及 IMU 噪声参数。当前参数集中在
`app/libs/VINS-Mobile-master/VINS_ios/global_param.cpp` 和
`app/src/main/java/com/vell/vins/Vins.java`。

## 开源与许可证

核心算法来自 VINS-Mobile，遵循 GPLv3。Ceres、Eigen、OpenCV、Boost、DBoW、DUtils、DVision
和 AndroidX 等第三方组件遵循各自许可证。完整清单见
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)，上游 GPLv3 原文见
`app/libs/VINS-Mobile-master/LICENSE`。

学术使用请引用 VINS-Mobile/VINS-Mono 论文：

```bibtex
@article{qin2018vins,
  title={VINS-Mono: A Robust and Versatile Monocular Visual-Inertial State Estimator},
  author={Qin, Tong and Li, Peiliang and Shen, Shaojie},
  journal={IEEE Transactions on Robotics},
  year={2018}
}
```
