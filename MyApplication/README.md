# VINS-Mobile Android VIO

这是一个基于 HKUST Aerial Robotics Group 开源项目
[VINS-Mobile](https://github.com/HKUST-Aerial-Robotics/VINS-Mobile) 修改的 Android
单目视觉惯性里程计（VIO）应用。项目通过 Camera2、Android 传感器和 JNI 接入
VINS 滑动窗口优化器，提供实时特征监测、轨迹建图、位姿显示和传感器数据录制。

当前版本主要针对 Xiaomi `2510DRK44C`（device codename: `annibale`）调试。
相机内参、相机与 IMU 外参以及时间偏移均与设备相关，移植到其他手机前必须重新标定。

## 功能

- Camera2 单目图像采集，固定横屏运行；
- 相机画面持续显示特征监测点，初始化前后均可观察前端跟踪；
- VINS-Mono/VINS-Mobile 视觉惯性初始化与滑动窗口优化；
- 独立的主视角轨迹地图和相机副视角；
- 实时 POS、姿态、特征数量、初始化状态和 GNSS 信息；
- 录制相机帧、IMU 和 GNSS，并导出 ZIP 到系统“下载/VINS”目录；
- 可选回环检测相关代码（当前默认关闭）。

## 相对上游的主要修改

### Android 平台适配

- 将原 iOS VINS-Mobile 前端、后端和绘制代码接入 Android JNI；
- 使用 Camera2 `YUV_420_888` 图像和 Android `SensorManager` IMU 数据；
- 增加应用生命周期、权限、相机线程、传感器线程和 native 线程退出管理；
- 增加 Android 页面、实时状态显示、双视角显示和录制导出功能。

### 相机与 IMU 一致性

- 相机输入固定为 640x480，并在 native 层顺时针旋转到 480x640 tracker 坐标；
- 使用旋转后图像坐标对应的相机内参；
- 根据 Camera2 `LENS_POSE_ROTATION` 确定 tracker-camera 到 device-IMU 的旋转为
  `yaw=0 deg, pitch=0 deg, roll=180 deg`；
- 使用校准陀螺仪数据，修正了把 `ASENSOR_TYPE_GYROSCOPE` 事件错误读取为
  `uncalibrated_gyro` union 字段的问题；
- 缓存陀螺仪样本，并在相邻加速度样本之间按时间戳线性插值，避免快速俯仰时
  加速度和角速度错配；
- 加入设备实测的相机/IMU固定时间偏移（当前为 `+20.5 ms`）；
- 固定已校准陀螺仪偏置，同时保留加速度偏置在线估计，避免静止误差被持续积分。

### 稳定性与性能

- native VINS 核心使用 `-O3` 编译；
- 相机以约 30 FPS 工作，后端按每 3 帧一次约 10 Hz 优化；
- 限制 Ceres 单次迭代和求解时间，并输出初始/最终 cost、迭代数和终止状态；
- 主地图不再使用无约束 PnP 位置进行实时外推；
- 故障恢复不继承已经发散的旧世界坐标；
- estimator 返回初始化状态时同步清理失效的地图渲染缓存；
- 降低地图渲染和点云复制开销，减少主视角路径显示延迟。

## 构建环境

- Android Studio（支持 Android Gradle Plugin 9.3.2）；
- JDK 11 或更高版本；
- Android SDK 37；
- Android NDK `21.4.7075529`；
- CMake `3.22.1`；
- 目标 ABI：`arm64-v8a`；
- 最低 Android API：21。

仓库已包含当前构建所需的 OpenCV AAR、native OpenCV、VINS-Mobile、Ceres、Eigen、
DBoW/DUtils/DVision 和 Boost 源码或二进制依赖。

### Git LFS

Android arm64 版本的预编译 `libceres.a` 约 452 MB，超过 GitHub 单文件 100 MB 限制，
仓库已通过 `.gitattributes` 将其配置为 Git LFS 对象。首次提交或克隆前需安装 Git LFS：

```bash
git lfs install
git lfs pull
```

不要跳过 LFS 后直接提交该静态库，否则 GitHub 会拒绝 push。源码压缩包中包含完整静态库，
无需额外下载。

```bash
./gradlew assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 首次启动时允许相机、定位和高采样率传感器权限。
2. 将场景中有纹理的区域保持在画面内，平稳移动并产生足够视差以完成初始化。
3. 初始化后主页面显示轨迹地图，相机副视角持续显示实时画面和监测点。
4. 点击录制按钮开始采集，再次点击后生成 ZIP 文件并导出到“下载/VINS”。

建议避免强反光、纯色墙面、严重运动模糊和长时间遮挡镜头。单目 VIO 需要持续视觉约束，
这些条件会导致特征数量下降或初始化失败。

## 设备移植

以下参数不能直接用于其他手机：

- `app/libs/VINS-Mobile-master/VINS_ios/global_param.cpp` 中 Mix2/目标设备的相机内参和外参；
- `app/src/main/java/com/vell/vins/Vins.java` 中 `CAMERA_IMU_TIME_OFFSET_NANOS`；
- 相机分辨率、图像旋转方向和 Camera2 主相机 ID。

新设备至少需要重新确认相机内参、畸变、Camera2 pose rotation、相机/IMU时间偏移、
IMU噪声和偏置模型。参数不匹配通常表现为转动时位置快速发散。

## 开源来源与引用

核心算法来自 VINS-Mobile，并沿用其 GPLv3 许可证。完整第三方清单和许可证位置见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。上游许可证原文保留在
`app/libs/VINS-Mobile-master/LICENSE`。

学术使用请引用 VINS-Mobile/VINS-Mono 相关论文：

```bibtex
@article{qin2018vins,
  title={VINS-Mono: A Robust and Versatile Monocular Visual-Inertial State Estimator},
  author={Qin, Tong and Li, Peiliang and Shen, Shaojie},
  journal={IEEE Transactions on Robotics},
  year={2018}
}

@inproceedings{li2017monocular,
  title={Monocular Visual-Inertial State Estimation for Mobile Augmented Reality},
  author={Li, Peiliang and Qin, Tong and Hu, Botao and Zhu, Fengyuan and Shen, Shaojie},
  booktitle={IEEE International Symposium on Mixed and Augmented Reality},
  year={2017}
}
```

## 许可证

本项目是 VINS-Mobile 的修改版本，整体发布和再分发必须遵守 GNU GPLv3。
第三方组件继续受各自许可证约束。发布源码或 APK 前，请保留原作者版权声明、许可证文件、
本修改说明以及对应源码。项目按“现状”提供，不附带任何明示或暗示担保。
