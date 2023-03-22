package com.tencent.liteav.demo.common.customcapture;

/***********************************************************************************************************************
 *
 *                 自定义渲染功能 CustomRenderVideo
 *
 *  该示例代码通过 openGL 将 SDK 回调出来的视频帧渲染到系统的 TextureView 上。
 *
 *  本示例代码中采用了 texture，也就是 openGL 纹理的方案，这是 android 系统下性能最好的一种视频处理方案。
 *
 *  1. 构造函数：会创建一个{@link android.os.HandlerThread}线程，所有的OpenGL操作均在该线程进行。
 *
 *  2. start()：传入一个系统TextureView（这个 View 需要加到 activity 的控件树上），用来显示渲染的结果。
 *
 *  3. onSurfaceTextureAvailable(): TextureView 的 SurfaceTexture 已经准备好，将SurfaceTexture与
 *     {@link com.tencent.trtc.TRTCCloudDef.TRTCVideoFrame#texture}中的EGLContext（可为null）作为参数，
 *     生成一个新的EGLContext，SurfaceTexture也会作为此EGLContext的渲染目标。
 *
 *  4. onRenderVideoFrame(): SDK 视频帧回调，在回调中可以拿到视频纹理ID和对应的 EGLContext。
 *     用这个 EGLContext 作为参数创建出来的新的 EGLContext，这样新的 EGLContext 就能访问SDK返回的纹理。
 *     然后会向HandlerThread发送一个渲染消息，用来渲染得到的视频纹理。
 *
 *  5. renderInternal(): HandlerThread线程具体的渲染流程，将视频纹理渲染到 TextureView。
 *************************************************************************************************************************/


import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.TextureView;

import com.tencent.liteav.demo.common.customcapture.utils.Size;
import com.tencent.liteav.demo.common.opengl.GLConstants;
import com.tencent.liteav.demo.common.opengl.GPUImageFilter;
import com.tencent.liteav.demo.common.opengl.GpuImageI420Filter;
import com.tencent.liteav.demo.common.opengl.OpenGlUtils;
import com.tencent.liteav.demo.common.opengl.Rotation;
import com.tencent.liteav.demo.common.opengl.helper.EglCore;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;

import static com.tencent.liteav.demo.common.opengl.OpenGlUtils.NO_TEXTURE;

@TargetApi(17)
public class CustomRenderVideo implements Handler.Callback {
    public static final  String TAG                 = "CustomRenderVideo";
    private static final int    MSG_RENDER          = 2;
    private static final int    MSG_DESTROY         = 3;
    private static final int    RENDER_TYPE_TEXTURE = 0;
    private static final int    RENDER_TYPE_I420    = 1;
    private static final int    RENDER_TYPE_RGBA    = 2;

    private final HandlerThread      mGLThread;
    private final GLHandler          mGLHandler;
    private       TextureView        mRenderView;
    private       int                mRenderType     = RENDER_TYPE_TEXTURE;
    private final VideoFrame         mProcessedFrame = new VideoFrame();
    private       EglCore            mEglCore;
    private       SurfaceTexture     mSurfaceTexture;
    private       Object             mSharedEglContext;
    private       Size               mSurfaceSize    = new Size();
    private       Size               mLastInputSize  = new Size();
    private       Size               mLastOutputSize = new Size();
    private final FloatBuffer        mGLCubeBuffer;
    private final FloatBuffer        mGLTextureBuffer;
    private       GPUImageFilter     mNormalFilter;
    private       GpuImageI420Filter mYUVFilter;
    private       int                mTextureId      = NO_TEXTURE;
    private final boolean            mEnableCustomProcess;

    public CustomRenderVideo(boolean enableCustomProcess) {
        mEnableCustomProcess = enableCustomProcess;
        mGLCubeBuffer = OpenGlUtils.createNormalCubeVerticesBuffer();
        mGLTextureBuffer = OpenGlUtils.createTextureCoordsBuffer(Rotation.NORMAL, false, false);

        mGLThread = new HandlerThread(TAG);
        mGLThread.start();
        mGLHandler = new GLHandler(mGLThread.getLooper(), this);
        Log.i(TAG, "CustomRenderVideo");
    }

    public void start(TextureView videoView) {
        if (videoView == null) {
            Log.w(TAG, "start error when render view is null");
            return;
        }
        Log.i(TAG, "start render, videoView=" + videoView);

        // 设置TextureView的SurfaceTexture生命周期回调，用于管理GLThread的创建和销毁
        mRenderView = videoView;
        if (mRenderView.getWidth() != 0 && mRenderView.getHeight() != 0) {
            mSurfaceTexture = mRenderView.getSurfaceTexture();
            mSurfaceSize.width = mRenderView.getWidth();
            mSurfaceSize.height = mRenderView.getHeight();
        }

        mRenderView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // 保存surfaceTexture，用于创建OpenGL线程
                mSurfaceTexture = surface;
                mSurfaceSize = new Size(width, height);
                Log.i(TAG, String.format("onSurfaceTextureAvailable width: %d, height: %d", width, height));
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                mSurfaceSize = new Size(width, height);
                Log.i(TAG, String.format("onSurfaceTextureSizeChanged width: %d, height: %d", width, height));
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                // surface释放了，需要停止渲染
                mSurfaceTexture = null;
                // 等待Runnable执行完，再返回，否则GL线程会使用一个无效的SurfaceTexture
                mGLHandler.runAndWaitDone(new Runnable() {
                    @Override
                    public void run() {
                        uninitGlComponent();
                    }
                });
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    public void stop() {
        if (mRenderView != null) {
            mRenderView.setSurfaceTextureListener(null);
        }
        mGLHandler.obtainMessage(MSG_DESTROY).sendToTarget();
    }

    /**
     * 当视频帧准备送入编码器时，SDK会触发该回调，将视频帧抛出来，这里主要处理TRTC_VIDEO_BUFFER_TYPE_TEXTURE
     */
    public void renderFrame(final VideoFrame frame) {
        if (frame.texture != null) {
            // 等待frame.texture的纹理绘制完成
            GLES20.glFinish();
        }
        mGLHandler.obtainMessage(MSG_RENDER, frame).sendToTarget();
    }

    private void initGlComponent(Object eglContext) {
        if (mSurfaceTexture == null) {
            return;
        }

        // 创建的时候，增加判断，防止这边创建的时候，传入的EGLContext已经被销毁了。
        try {
            if (eglContext instanceof javax.microedition.khronos.egl.EGLContext) {
                mEglCore = new EglCore((javax.microedition.khronos.egl.EGLContext) eglContext, new Surface(mSurfaceTexture));
            } else {
                mEglCore = new EglCore((android.opengl.EGLContext) eglContext, new Surface(mSurfaceTexture));
            }
        } catch (Exception e) {
            Log.e(TAG, "create EglCore failed.", e);
            return;
        }

        mEglCore.makeCurrent();
        if (mRenderType == RENDER_TYPE_TEXTURE || mRenderType == RENDER_TYPE_RGBA) {
            mNormalFilter = new GPUImageFilter();
            mNormalFilter.init();
        } else if (mRenderType == RENDER_TYPE_I420) {
            mYUVFilter = new GpuImageI420Filter();
            mYUVFilter.init();
        }
    }

    private void renderInternal(VideoFrame frame) {
        mRenderType = RENDER_TYPE_I420;
        if (frame.bufferType == VideoFrame.VIDEO_BUFFER_TYPE_TEXTURE) {
            mRenderType = RENDER_TYPE_TEXTURE;
        } else if (frame.pixelFormat == VideoFrame.VIDEO_PIXEL_FORMAT_I420) {
            mRenderType = RENDER_TYPE_I420;
        } else if (frame.pixelFormat == VideoFrame.VIDEO_PIXEL_FORMAT_RGBA) {
            mRenderType = RENDER_TYPE_RGBA;
        } else {
            Log.w(TAG, "error video frame type");
            return;
        }

        if (frame.texture != null && mSharedEglContext != null) {
            Object objectContext = frame.texture.eglContext10 != null ? frame.texture.eglContext10 :
                    frame.texture.eglContext14;
            if (mSharedEglContext != objectContext) {
                uninitGlComponent();
            }
        }

        if (mEglCore == null && mSurfaceTexture != null) {
            Object eglContext = null;
            if (frame.texture != null) {
                eglContext = frame.texture.eglContext10 != null ? frame.texture.eglContext10 : frame.texture.eglContext14;
            }
            initGlComponent(eglContext);
            mSharedEglContext = eglContext;
        }

        if (mEglCore == null) {
            return;
        }

        if (mEnableCustomProcess) {
            doCustomProcess(frame);
        }

        if (mLastInputSize.width != frame.width || mLastInputSize.height != frame.height
                || mLastOutputSize.width != mSurfaceSize.width || mLastOutputSize.height != mSurfaceSize.height) {
            Pair<float[], float[]> cubeAndTextureBuffer = OpenGlUtils.calcCubeAndTextureBuffer(GLConstants.GLScaleType.FIT_CENTER,
                    Rotation.NORMAL, false, frame.width, frame.height, mSurfaceSize.width, mSurfaceSize.height);
            mGLCubeBuffer.clear();
            mGLCubeBuffer.put(cubeAndTextureBuffer.first);
            mGLTextureBuffer.clear();
            mGLTextureBuffer.put(cubeAndTextureBuffer.second);
            OpenGlUtils.deleteTexture(mTextureId);
            mTextureId = NO_TEXTURE;

            mLastInputSize = new Size(frame.width, frame.height);
            mLastOutputSize = new Size(mSurfaceSize.width, mSurfaceSize.height);
        }

        mEglCore.makeCurrent();
        GLES20.glViewport(0, 0, mSurfaceSize.width, mSurfaceSize.height);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GLES20.glClearColor(0, 0, 0, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        if (mRenderType == RENDER_TYPE_TEXTURE) {
            mNormalFilter.onDraw(frame.texture.textureId, mGLCubeBuffer, mGLTextureBuffer);
        } else if (mRenderType == RENDER_TYPE_I420) {
            if (frame.data != null) {
                mYUVFilter.loadYuvDataToTexture(frame.data, frame.width, frame.height);
            } else {
                mYUVFilter.loadYuvDataToTexture(frame.buffer, frame.width, frame.height);
            }
            mYUVFilter.onDraw(NO_TEXTURE, mGLCubeBuffer, mGLTextureBuffer);
        } else if (mRenderType == RENDER_TYPE_RGBA) {
            ByteBuffer buffer = (frame.data != null ? ByteBuffer.wrap(frame.data) : frame.buffer);
            buffer.position(0);
            mTextureId = OpenGlUtils.loadTexture(GLES20.GL_RGBA, buffer, frame.width, frame.height, mTextureId);
            mNormalFilter.onDraw(mTextureId, mGLCubeBuffer, mGLTextureBuffer);
        }
        mEglCore.swapBuffer();
    }

    private void doCustomProcess(VideoFrame frame) {
//        if (mVideoFrameListener == null) {
//            if (mRenderType == RENDER_TYPE_TEXTURE) {
//                mVideoFrameListener = new TRTCVideoTextureFrameListener();
//            } else {
//                mVideoFrameListener = new TRTCVideoBytesFrameListener();
//            }
//            mVideoFrameListener.onGLContextCreated();
//        }
//
//        if (mProcessedFrame.width != frame.width || mProcessedFrame.height != frame.height) {
//            uninitProcessedFrameResource();
//        }
//
//        mProcessedFrame.width = frame.width;
//        mProcessedFrame.height = frame.height;
//        if (frame.data != null && mProcessedFrame.data == null) {
//            mProcessedFrame.data = new byte[frame.data.length];
//        } else if (frame.buffer != null && mProcessedFrame.buffer == null) {
//            mProcessedFrame.buffer = ByteBuffer.allocateDirect(frame.buffer.capacity());
//        } else if (frame.texture != null && mProcessedFrame.texture == null) {
//            mProcessedFrame.texture = new TRTCCloudDef.TRTCTexture();
//            mProcessedFrame.texture.eglContext14 = frame.texture.eglContext14;
//            mProcessedFrame.texture.eglContext10 = frame.texture.eglContext10;
//            mProcessedFrame.texture.textureId = OpenGlUtils.loadTexture(GLES20.GL_RGBA, null, frame.width, frame.height, -1);
//        }
//        mVideoFrameListener.onProcessVideoFrame(frame, mProcessedFrame);
//
//        frame.texture = mProcessedFrame.texture;
//        frame.data = mProcessedFrame.data;
//        frame.buffer = mProcessedFrame.buffer;
    }

    private void uninitProcessedFrameResource() {
        Log.i(TAG, "uninitProcessedFrameResource");
        if (mProcessedFrame.texture != null) {
            OpenGlUtils.deleteTexture(mProcessedFrame.texture.textureId);
            mProcessedFrame.texture = null;
        }
        mProcessedFrame.data = null;
        mProcessedFrame.buffer = null;
        mProcessedFrame.width = mProcessedFrame.height = -1;
    }

    private void uninitGlComponent() {
        uninitProcessedFrameResource();
        OpenGlUtils.deleteTexture(mTextureId);
        mTextureId = NO_TEXTURE;
//        if (mVideoFrameListener != null) {
//            mVideoFrameListener.onGLContextDestory();
//        }
        if (mNormalFilter != null) {
            mNormalFilter.destroy();
            mNormalFilter = null;
        }
        if (mYUVFilter != null) {
            mYUVFilter.destroy();
            mYUVFilter = null;
        }
        if (mEglCore != null) {
            mEglCore.unmakeCurrent();
            mEglCore.destroy();
            mEglCore = null;
        }
        mSharedEglContext = null;
    }

    private void destroyInternal() {
        uninitGlComponent();

        if (Build.VERSION.SDK_INT >= 18) {
            mGLHandler.getLooper().quitSafely();
        } else {
            mGLHandler.getLooper().quit();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_RENDER:
                renderInternal((VideoFrame) msg.obj);
                break;
            case MSG_DESTROY:
                destroyInternal();
                break;
        }
        return false;
    }

    public static class GLHandler extends Handler {
        public GLHandler(Looper looper, Callback callback) {
            super(looper, callback);
        }

        public void runAndWaitDone(final Runnable runnable) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            post(new Runnable() {
                @Override
                public void run() {
                    runnable.run();
                    countDownLatch.countDown();
                }
            });

            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
