package com.jrs.ffmpeg;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;

public class FFPacket implements AutoCloseable {
    public final AVPacket p;
    private boolean closed;

    public FFPacket() {
        this.p = av_packet_alloc();
        if (p == null) throw new OutOfMemoryError("av_packet_alloc failed");
    }

    public FFPacket(AVPacket p) {
        this.p = p;
    }

    public int readFrame(FFMediaInput mediaInput){
        return readFrame(mediaInput.fmt);
    }

    public int readFrame(AVFormatContext formatCtx) {
        return av_read_frame(formatCtx, p);
    }

    public int streamIndex(){
        return p.stream_index();
    }

    /** Create a new Packet that references the contents of src (refcounted). */
    public static FFPacket refFrom(AVPacket src) {
        FFPacket out = new FFPacket();
        int r = av_packet_ref(out.p, src);
        if (r < 0) {
            out.close();
            throw new RuntimeException("av_packet_ref failed: " + r);
        }
        return out;
    }

    public static FFPacket refFrom(FFPacket src) {
        FFPacket out = new FFPacket();
        int r = av_packet_ref(out.p, src.p);
        if (r < 0) {
            out.close();
            throw new RuntimeException("av_packet_ref failed: " + r);
        }
        return out;
    }



    public void unref() {
        if (!closed) av_packet_unref(p);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        av_packet_unref(p);
        av_packet_free(p);
    }
}
