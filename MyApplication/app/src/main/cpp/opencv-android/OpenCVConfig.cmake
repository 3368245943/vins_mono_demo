get_filename_component(_opencv_android_root "${CMAKE_CURRENT_LIST_DIR}/../../" ABSOLUTE)
get_filename_component(_opencv_source_root "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/opencv-3.4.5" ABSOLUTE)

set(OpenCV_INCLUDE_DIRS
    "${_opencv_source_root}/include"
    "${_opencv_source_root}/modules/core/include"
    "${_opencv_source_root}/modules/calib3d/include"
    "${_opencv_source_root}/modules/features2d/include"
    "${_opencv_source_root}/modules/flann/include"
    "${_opencv_source_root}/modules/highgui/include"
    "${_opencv_source_root}/modules/imgcodecs/include"
    "${_opencv_source_root}/modules/imgproc/include"
    "${_opencv_source_root}/modules/video/include"
)

add_library(opencv_java3 SHARED IMPORTED)
set_target_properties(opencv_java3 PROPERTIES
    IMPORTED_LOCATION "${_opencv_android_root}/jniLibs/${ANDROID_ABI}/libopencv_java3.so"
    INTERFACE_INCLUDE_DIRECTORIES "${OpenCV_INCLUDE_DIRS}"
)

set(OpenCV_LIBS opencv_java3)
add_library(opencv_core ALIAS opencv_java3)
set(OpenCV_FOUND TRUE)
