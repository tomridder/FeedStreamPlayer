package com.tencent.liteav.demo.common.customcapture;

import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;

import com.tencent.liteav.demo.common.customcapture.decoder.Decoder;
import com.tencent.liteav.demo.common.customcapture.exceptions.ProcessException;
import com.tencent.liteav.demo.common.customcapture.exceptions.SetupException;
import com.tencent.liteav.demo.common.customcapture.extractor.Extractor;
import com.tencent.liteav.demo.common.customcapture.extractor.ExtractorAdvancer;
import com.tencent.liteav.demo.common.customcapture.extractor.RangeExtractorAdvancer;
import com.tencent.liteav.demo.common.customcapture.structs.TextureFrame;
import com.tencent.liteav.demo.common.customcapture.utils.MediaUtils;
import com.tencent.liteav.demo.common.customcapture.utils.Size;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class VideoFrameReader extends BaseReader {
    private static final String TAG = "VideoFrameReader";

    public static final int FRAME_TYPE_TEXTURE   = 0;
    public static final int FRAME_TYPE_BYTEARRAY = 1;

    public interface VideoFrameReadListener {
        void onFrameAvailable(TextureFrame frame);
    }

    private final int                      mVideoFrameType;
    private final String                   mVideoPath;
    private final long                     mLoopDurationMs;
    private       VideoFrameReadListener   mListener;
    private       Decoder                  mVideoDecoder;
    private       VideoDecoderBaseConsumer mDecoderConsumer;
    private       long                     mStartTimeMs = -1;

    public VideoFrameReader(String videoPath, long durationMs, CountDownLatch countDownLatch) {
        this(FRAME_TYPE_TEXTURE, videoPath, durationMs, countDownLatch);
    }

    public VideoFrameReader(int type, String videoPath, long durationMs, CountDownLatch countDownLatch) {
        super(countDownLatch);
        mVideoFrameType = type;
        mVideoPath = videoPath;
        mLoopDurationMs = durationMs;
    }

    public void setListener(VideoFrameReadListener listener) {
        mListener = listener;
    }

    @Override
    protected void setup() throws SetupException {
        Size size = retriveVideoSize();
        mDecoderConsumer = createVideoDecoderConsumer(size);
        mDecoderConsumer.setup();
        SurfaceTexture surfaceTexture = (SurfaceTexture) mDecoderConsumer.getConsumerObject();
        ExtractorAdvancer advancer = new RangeExtractorAdvancer(MILLISECONDS.toMicros(mLoopDurationMs));
        Extractor extractor = new Extractor(true, mVideoPath, advancer);
        mVideoDecoder = new Decoder(extractor, surfaceTexture);
        mVideoDecoder.setLooping(true);
        mVideoDecoder.setup();
        mDecoderConsumer.setFrameProvider(mVideoDecoder);
    }

    private VideoDecoderBaseConsumer createVideoDecoderConsumer(Size size) {
        if (FRAME_TYPE_TEXTURE == mVideoFrameType) {
            return new VideoDecoderSurfaceConsumer(size.width, size.height);
        } else {
            return new VideoDecoderDataConsumer(size.width, size.height);
        }
    }

    @Override
    protected void processFrame() throws ProcessException {
        if (mStartTimeMs == -1) {
            mStartTimeMs = SystemClock.elapsedRealtime();
        }

        mVideoDecoder.processFrame();
        mDecoderConsumer.processFrame();

        TextureFrame textureFrame = mDecoderConsumer.dequeueOutputBuffer();
        if (textureFrame == null) {
            return;
        }

        // 检查当前帧与预期发送的时间差多久，睡眠这段时间，然后再发送
        long sleepTime;
        while ((sleepTime = textureFrame.timestampMs - (SystemClock.elapsedRealtime() - mStartTimeMs)) > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        VideoFrameReadListener listener = mListener;
        if (listener != null) {
            listener.onFrameAvailable(textureFrame);
        }
        mDecoderConsumer.enqueueOutputBuffer(textureFrame);
    }

    private Size retriveVideoSize() throws SetupException {
        MediaFormat mediaFormat = MediaUtils.retriveMediaFormat(mVideoPath, true);
        Size size = new Size();
        size.width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        size.height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        if (mediaFormat.containsKey(MediaUtils.KEY_ROTATION)) {
            int rotation = mediaFormat.getInteger(MediaUtils.KEY_ROTATION);
            if (rotation == 90 || rotation == 270) {
                size.swap();
            }
        }
        return size;
    }

    @Override
    protected void release() {
        if (mVideoDecoder != null) {
            mVideoDecoder.release();
            mVideoDecoder = null;
        }

        if (mDecoderConsumer != null) {
            mDecoderConsumer.release();
            mDecoderConsumer = null;
        }
        Log.i(TAG, "released");
    }
}
