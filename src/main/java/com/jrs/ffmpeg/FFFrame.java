package com.jrs.ffmpeg;

import org.bytedeco.ffmpeg.avutil.AVFrame;

import static org.bytedeco.ffmpeg.global.avutil.*;

public class FFFrame implements AutoCloseable {
    public final AVFrame f;
    private boolean closed;

    public FFFrame() {
        this.f = av_frame_alloc();
        if (f == null) throw new OutOfMemoryError("av_frame_alloc failed");
    }

    /** Make this frame reference src's buffers (refcounted). */
    public static FFFrame refFrom(AVFrame src) {
        FFFrame out = new FFFrame();
        int r = av_frame_ref(out.f, src);
        if (r < 0) throw new RuntimeException("av_frame_ref failed: " + r);
        return out;
    }

    public static FFFrame refFrom(FFFrame src) {
        FFFrame out = new FFFrame();
        int r = av_frame_ref(out.f, src.f);
        if (r < 0) throw new RuntimeException("av_frame_ref failed: " + r);
        return out;
    }

    public void unref() {
        if (!closed) av_frame_unref(f);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // av_frame_unref(f);
        av_frame_free(f);
    }
}
