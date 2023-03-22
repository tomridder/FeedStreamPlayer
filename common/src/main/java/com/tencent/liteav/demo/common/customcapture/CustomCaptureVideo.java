package com.tencent.liteav.demo.common.customcapture;

/*****************************************************************
 *
 *                 测试自定义采集功能 CustomCaptureVideo
 *
 *  该示例代码通过从手机中的一个视频文件里读取音频以及视频画面，并通过 TRTCCloud 的 sendCustomAudioData和
 *  sendCustomVideoData 接口，将这些音频、视频画面送给 SDK 进行编码和发送。
 *
 *  音视频读取分别由两个单独的类来实现，为了使两边采集的音视频一致，在初始的时候，会计算出循环读取的周期。
 *  周期以音频时长为准，且按照20ms对齐。
 *
 *  两个读取类以CountDownLatch来做初始的同步操作。
 *
 ******************************************************************/

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;
import android.widget.Toast;

import com.tencent.liteav.demo.common.customcapture.exceptions.SetupException;
import com.tencent.liteav.demo.common.customcapture.utils.MediaUtils;

import java.util.concurrent.CountDownLatch;

public class CustomCaptureVideo {
    private static final String           TAG             = "SendCustomData";
    private final        boolean          mWithVideo;
    private              String           mVideoFilePath;
    private              Context          mContext;
    private              VideoFrameReader mVideoFrameReader;
    private              AudioFrameReader mAudioFrameReader;
    private              boolean          mIsAudioStopped = false;
    private              int              mFrameType;

    public CustomCaptureVideo(Context context, String videoFilePath, boolean withVideo) {
        this(context, videoFilePath, withVideo, VideoFrameReader.FRAME_TYPE_TEXTURE);
    }

    public CustomCaptureVideo(Context context, String videoFilePath, boolean withVideo, int frameType) {
        mWithVideo = withVideo;
        mContext = context.getApplicationContext();
        mVideoFilePath = videoFilePath;
        mFrameType = frameType;
    }

    // 需要一定耗时，最好放在非主线程调用
    public synchronized void start(AudioFrameReader.AudioFrameReadListener audioListener, VideoFrameReader.VideoFrameReadListener videoListener) {
        if (mAudioFrameReader != null || mVideoFrameReader != null) {
            return;
        }

        long duration;
        try {
            // 循环的时长按照音频长度，同时按照20ms对齐
            MediaFormat mediaFormat = MediaUtils.retriveMediaFormat(mVideoFilePath, false);
            duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
            duration = (duration / MILLISECONDS.toMicros(20) + 1) * MILLISECONDS.toMicros(20);
        } catch (SetupException e) {
            Log.e(TAG, "setup failed.", e);
            Toast.makeText(mContext, "打开文件失败!", Toast.LENGTH_LONG).show();
            return;
        }

        CountDownLatch countDownLatch = new CountDownLatch(mWithVideo ? 2 : 1);
        if (mWithVideo) {
            mVideoFrameReader = new VideoFrameReader(mFrameType, mVideoFilePath,
                    MICROSECONDS.toMillis(duration), countDownLatch);
            mVideoFrameReader.setListener(videoListener);
            mVideoFrameReader.start();
        }

        mAudioFrameReader = new AudioFrameReader(mVideoFilePath, MICROSECONDS.toMillis(duration), countDownLatch);
        mAudioFrameReader.setListener(audioListener);
        mAudioFrameReader.start();
        stopAudio(mIsAudioStopped);
    }

    public void stopAudio(boolean stop) {
        mIsAudioStopped = stop;
        if (mAudioFrameReader != null) {
            mAudioFrameReader.enableSend(!mIsAudioStopped);
        }
    }

    public synchronized void stop() {
        if (mVideoFrameReader != null) {
            mVideoFrameReader.setListener(null);
            mVideoFrameReader.stopRead();
            mVideoFrameReader = null;
        }

        if (mAudioFrameReader != null) {
            mAudioFrameReader.setListener(null);
            mAudioFrameReader.stopRead();
            mAudioFrameReader = null;
        }
    }
}
