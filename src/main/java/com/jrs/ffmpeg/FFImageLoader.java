package com.jrs.ffmpeg;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

public final class FFImageLoader {

    /** Result is a LONG-LIVED direct ByteBuffer you own (RGBA, tightly packed). */
    public static final class RgbaImage {
        public final int width;
        public final int height;
        public final ByteBuffer rgba; // size = width * height * 4

        public RgbaImage(int width, int height, ByteBuffer rgba) {
            this.width = width;
            this.height = height;
            this.rgba = rgba;
        }
    }

    /**
     * Load an image (png/jpg/webp/etc) using FFmpeg demux+decode, convert to RGBA,
     * and return a LONG-LIVED direct ByteBuffer (caller owns it).
     *
     * Notes:
     * - This reads the FIRST video stream (typical for images).
     * - For animated formats (gif/webp), this returns the first decoded frame.
     */
    public static RgbaImage loadRgba(Path imagePath) {
        String url = imagePath.toAbsolutePath().toString();

        AVFormatContext fmt = avformat_alloc_context();
        if (fmt == null) throw new OutOfMemoryError("avformat_alloc_context failed");

        AVPacket pkt = av_packet_alloc();
        if (pkt == null) {
            avformat_free_context(fmt);
            throw new OutOfMemoryError("av_packet_alloc failed");
        }

        AVFrame frame = av_frame_alloc();
        if (frame == null) {
            av_packet_free(pkt);
            avformat_free_context(fmt);
            throw new OutOfMemoryError("av_frame_alloc failed");
        }

        AVCodecContext decCtx = null;
        SwsContext sws = null;

        try {
            int r = avformat_open_input(fmt, url, null, null);
            if (r < 0) throw new RuntimeException("avformat_open_input failed: " + r + " path=" + url);

            r = avformat_find_stream_info(fmt, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
            if (r < 0) throw new RuntimeException("avformat_find_stream_info failed: " + r);

            int videoStreamIndex = av_find_best_stream(fmt, AVMEDIA_TYPE_VIDEO, -1, -1, (AVCodec) null, 0);
            if (videoStreamIndex < 0) {
                throw new RuntimeException("No video stream found in image file (is this a readable image?): " + url);
            }

            AVStream st = fmt.streams(videoStreamIndex);
            var par = st.codecpar();

            AVCodec dec = avcodec_find_decoder(par.codec_id());
            if (dec == null) throw new RuntimeException("No decoder for codec_id=" + par.codec_id());

            decCtx = avcodec_alloc_context3(dec);
            if (decCtx == null) throw new OutOfMemoryError("avcodec_alloc_context3 failed");

            r = avcodec_parameters_to_context(decCtx, par);
            if (r < 0) throw new RuntimeException("avcodec_parameters_to_context failed: " + r);

            r = avcodec_open2(decCtx, dec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
            if (r < 0) throw new RuntimeException("avcodec_open2 failed: " + r);

            // Read packets until we decode one frame
            boolean gotFrame = false;
            while (!gotFrame) {
                r = av_read_frame(fmt, pkt);
                if (r < 0) {
                    // EOF or error
                    throw new RuntimeException("av_read_frame failed before decoding any frame: " + r);
                }

                if (pkt.stream_index() != videoStreamIndex) {
                    av_packet_unref(pkt);
                    continue;
                }

                r = avcodec_send_packet(decCtx, pkt);
                av_packet_unref(pkt);
                if (r < 0) throw new RuntimeException("avcodec_send_packet failed: " + r);

                while (true) {
                    r = avcodec_receive_frame(decCtx, frame);
                    if (r == 0) {
                        gotFrame = true;
                        break;
                    }
                    if (r == AVERROR_EAGAIN() || r == AVERROR_EOF()) break;
                    throw new RuntimeException("avcodec_receive_frame failed: " + r);
                }
            }

            int w = frame.width();
            int h = frame.height();
            if (w <= 0 || h <= 0) throw new RuntimeException("Decoded image has invalid size: " + w + "x" + h);

            int srcFmt = frame.format();

            sws = sws_getContext(
                    w, h, srcFmt,
                    w, h, AV_PIX_FMT_RGBA,
                    SWS_BILINEAR,
                    null, null, (DoublePointer) null
            );
            if (sws == null) throw new RuntimeException("sws_getContext failed");

            int rgbaBytes = w * h * 4;
            ByteBuffer rgba = ByteBuffer.allocateDirect(rgbaBytes);
            rgba.clear();

            BytePointer dst = new BytePointer(rgba);
            PointerPointer<BytePointer> dstData = new PointerPointer<>(4);
            dstData.put(0, dst);
            dstData.put(1, null);
            dstData.put(2, null);
            dstData.put(3, null);

            IntPointer dstLinesize = new IntPointer(4);
            dstLinesize.put(0, w * 4);

            int outH = sws_scale(
                    sws,
                    frame.data(),
                    frame.linesize(),
                    0,
                    h,
                    dstData,
                    dstLinesize
            );
            if (outH <= 0) throw new RuntimeException("sws_scale failed: outH=" + outH);

            rgba.position(0);
            rgba.limit(rgbaBytes);

            return new RgbaImage(w, h, rgba);

        } finally {
            if (sws != null) sws_freeContext(sws);
            if (decCtx != null) avcodec_free_context(decCtx);

            av_frame_unref(frame);
            av_frame_free(frame);

            av_packet_unref(pkt);
            av_packet_free(pkt);

            // Important: close input too
            if (fmt != null) {
                avformat_close_input(fmt); // closes and frees internal allocations
                // fmt pointer becomes invalid after close_input in C, but JavaCPP wrapper tolerates.
            }
        }
    }

    private FFImageLoader() {}
}