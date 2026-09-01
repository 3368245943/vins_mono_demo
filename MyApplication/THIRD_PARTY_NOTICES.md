# Third-Party Notices

本项目包含或链接以下开源项目。此文件仅作归属和定位说明，各组件以其随附许可证原文为准。

| 组件 | 用途 | 来源 | 许可证/声明位置 |
| --- | --- | --- | --- |
| VINS-Mobile | 单目视觉惯性里程计、初始化、滑动窗口优化、回环与绘制基础 | [HKUST-Aerial-Robotics/VINS-Mobile](https://github.com/HKUST-Aerial-Robotics/VINS-Mobile) | GNU GPLv3；`app/libs/VINS-Mobile-master/LICENSE` |
| VINS-Mono | VINS-Mobile 的相关算法与论文基础 | [HKUST-Aerial-Robotics/VINS-Mono](https://github.com/HKUST-Aerial-Robotics/VINS-Mono) | GNU GPLv3；请同时引用上游论文 |
| Ceres Solver | 非线性最小二乘优化 | [ceres-solver/ceres-solver](https://github.com/ceres-solver/ceres-solver) | New BSD；`app/libs/VINS-Mobile-master/VINS_ThirdPartyLib/ceres-solver/LICENSE` |
| Eigen | 线性代数 | [eigenteam/eigen-git-mirror](https://gitlab.com/libeigen/eigen) | MPL2 及文件级附加许可；源码头部和 Eigen 上游许可 |
| OpenCV 3.4.5 | 图像转换、特征跟踪和计算机视觉基础能力 | [opencv/opencv](https://github.com/opencv/opencv) | BSD 3-Clause；`app/third_party/opencv-3.4.5/LICENSE` |
| Boost | C++基础库 | [boostorg/boost](https://github.com/boostorg/boost) | Boost Software License 1.0；各 Boost 源码版权头和上游许可证 |
| DBoW / DUtils / DVision | 回环检测、视觉词袋和工具代码 | [dorian3d/DBoW2](https://github.com/dorian3d/DBoW2) | 以 `app/libs/VINS-Mobile-master/ThirdParty` 内源码版权头及上游条款为准 |
| AndroidX | Android应用支持库 | [androidx/androidx](https://github.com/androidx/androidx) | Apache License 2.0；由 Gradle 依赖获取 |

OpenCV 源码树还包含其自带的第三方组件，例如 libjpeg-turbo、libpng、OpenEXR、
protobuf、quirc 和 cpu_features。相应许可证保留在 `app/third_party/opencv-3.4.5/3rdparty`
的各组件目录中。

## 上游作者说明

VINS-Mobile 由 HKUST Aerial Robotics Group 的 Peiliang Li、Tong Qin、Zhenfei Yang、
Kejie Qiu、Shaojie Shen 等开发。本项目修改了其平台接入、设备参数、数据同步、渲染、
录制和部分优化配置；这些修改不代表或暗示获得上游作者背书。

## 分发注意事项

- VINS-Mobile 为 GPLv3，本修改项目及其二进制分发应满足 GPLv3 对应源码义务。
- 不要删除任何第三方源码中的版权头或许可证文件。
- 发布 APK 时应同时提供可获得的对应源码、构建脚本和本声明。
- 若替换或升级第三方组件，应重新核对版本和许可证兼容性。

