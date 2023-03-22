package com.tencent.liteav.demo.common.customcapture;

import java.nio.ByteBuffer;


public class VideoFrame {

    public VideoFrame() {
        texture = new Texture();
    }

    public static final int VIDEO_PIXEL_FORMAT_UNKNOWN = 0;

    public static final int VIDEO_PIXEL_FORMAT_I420 = 1;

    public static final int VIDEO_PIXEL_FORMAT_Texture_2D = 2;

    /// OES 外部纹理格式（适用于 Android 平台）
    public static final int VIDEO_PIXEL_FORMAT_TEXTURE_EXTERNAL_OES = 3;

    public static final int VIDEO_PIXEL_FORMAT_NV21 = 4;

    public static final int VIDEO_PIXEL_FORMAT_RGBA = 5;


    public static final int VIDEO_BUFFER_TYPE_UNKNOWN = 0;

    public static final int VIDEO_BUFFER_TYPE_BYTE_BUFFER = 1;

    public static final int VIDEO_BUFFER_TYPE_BYTE_ARRAY = 2;

    public static final int VIDEO_BUFFER_TYPE_TEXTURE = 3;

    public static class Texture {
        ///【字段含义】视频纹理 ID
        public int textureId;

        ///【字段含义】使用 (javax.microedition.khronos.egl.*) 定义的 OpenGL 上下文环境。
        public javax.microedition.khronos.egl.EGLContext eglContext10;

        ///【字段含义】使用 (android.opengl.*) 定义的 OpenGL 上下文环境。
        public android.opengl.EGLContext eglContext14;
    }


    public int pixelFormat;

    public int bufferType;

    public Texture texture;

    ///【字段含义】bufferType 为 {@link TRTCCloudDef#VIDEO_BUFFER_TYPE_BYTE_ARRAY} 时的视频数据，承载用于 java 层的字节数组。
    public byte[] data;

    ///【字段含义】bufferType 为 {@link TRTCCloudDef#VIDEO_BUFFER_TYPE_BYTE_BUFFER} 时的视频数据，承载用于 JNI 层的 Direct Buffer。
    public ByteBuffer buffer;

    ///【字段含义】视频宽度
    public int width;

    ///【字段含义】视频高度
    public int height;

    ///【字段含义】视频帧的时间戳，单位毫秒
    ///【推荐取值】自定义视频采集时可以设置为0。若该参数为0，SDK 会自定填充 timestamp 字段，但请“均匀”地控制 sendCustomVideoData 的调用间隔。
    public long timestamp;

    ///【字段含义】视频像素的顺时针旋转角度
    public int rotation;
}


