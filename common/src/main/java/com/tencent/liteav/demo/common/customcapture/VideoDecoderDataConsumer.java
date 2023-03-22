package com.tencent.liteav.demo.common.customcapture;

import com.tencent.liteav.demo.common.customcapture.exceptions.ProcessException;
import com.tencent.liteav.demo.common.customcapture.structs.Frame;
import com.tencent.liteav.demo.common.customcapture.structs.TextureFrame;

import java.util.concurrent.TimeUnit;


/**
 * 将解码出来的内容存入ByteArray，然后作为输出给到下一个节点
 */
public class VideoDecoderDataConsumer extends VideoDecoderBaseConsumer {

    public VideoDecoderDataConsumer(int width, int height) {
        super(width, height);
    }

    @Override
    public void processFrame() throws ProcessException {
        super.processFrame();
        Frame frame = mFrameProvider.dequeueOutputBuffer();
        if (frame != null) {
            customFrame(frame);
            // 将Frame归还给Decoder
            mFrameProvider.enqueueOutputBuffer(frame);
        }
    }

    private void customFrame(Frame frame) {
        byte[] data = new byte[frame.size];
        frame.buffer.position(frame.offset);
        frame.buffer.get(data);

        TextureFrame textureFrame = new TextureFrame();
        textureFrame.width = frame.width;
        textureFrame.height = frame.height;
        textureFrame.timestampMs = TimeUnit.MICROSECONDS.toMillis(frame.presentationTimeUs);
        textureFrame.data = data;
        synchronized (this) {
            mWaitOutBuffers.add(textureFrame);
        }
    }
}
