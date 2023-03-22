package com.tencent.liteav.demo.common.customcapture;

import com.tencent.liteav.demo.common.customcapture.exceptions.SetupException;
import com.tencent.liteav.demo.common.customcapture.pipeline.ProvidedStage;
import com.tencent.liteav.demo.common.customcapture.pipeline.Provider;
import com.tencent.liteav.demo.common.customcapture.structs.Frame;
import com.tencent.liteav.demo.common.customcapture.structs.TextureFrame;

import java.util.List;

public class VideoDecoderBaseConsumer extends ProvidedStage<TextureFrame> {

    protected final int mWidth;
    protected final int mHeight;

    protected Provider<Frame> mFrameProvider;

    public VideoDecoderBaseConsumer(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void setFrameProvider(Provider<Frame> provider) {
        mFrameProvider = provider;
    }

    public Object getConsumerObject() {
        return null;
    }

    @Override
    public void setup() throws SetupException {

    }

    @Override
    protected void recycleBuffers(List<TextureFrame> canReuseBuffers) {

    }

    @Override
    public void release() {

    }
}
