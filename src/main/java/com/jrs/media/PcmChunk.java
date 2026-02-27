package com.jrs.media;

import java.nio.ByteBuffer;

public class PcmChunk {
    public ByteBuffer data;
    public int channels;
    public int samples;
    public boolean float32;
    public PcmChunk(ByteBuffer data, int channels, int samples, boolean float32) {
        this.data = data;
        this.channels = channels;
        this.samples = samples;
        this.float32 = float32;
    }
}
