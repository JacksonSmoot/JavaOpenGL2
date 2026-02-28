package com.jrs.media;

import com.jrs.ffmpeg.FFCodec;
import com.jrs.ffmpeg.FFFrame;
import com.jrs.ffmpeg.FFMediaInput;
import com.jrs.ffmpeg.FFPacket;
import com.jrs.gl.g2d.GL2D;
import com.jrs.gl.resources.texture.GLBufferedTexture;

import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

public final class GifSpritePlayer implements AutoCloseable {

    // Timing policy
    private static final int DEFAULT_DELAY_MS = 100;
    private static final int MIN_DELAY_MS = 10;
    private static final int MAX_DELAY_MS = 2000;

    // Small bounded pipeline
    private static final int QUEUE_CAP = 6; // preserves order without huge buffering
    private static final int POOL_CAP  = 6; // reuse buffers once size known

    private final Path path;

    private volatile boolean running;
    private Thread decodeThread;

    // FFmpeg
    private FFMediaInput input;
    private FFCodec decoder;
    private int videoStreamIndex = -1;
    private AVStream stream;
    private double timeBaseSeconds;

    // swscale
    private SwsContext sws;
    private int swsW = -1, swsH = -1, swsSrcFmt = -1;

    // Frames and buffer pool
    private final ArrayBlockingQueue<Frame> ready = new ArrayBlockingQueue<>(QUEUE_CAP);
    private final ArrayBlockingQueue<ByteBuffer> pool = new ArrayBlockingQueue<>(POOL_CAP);

    // GPU
    private GLBufferedTexture tex;

    // Playback
    private long nextSwapNs = 0;
    private boolean started = false;

    public GifSpritePlayer(Path path) {
        this.path = path;
    }

    public void start() {
        if (running) return;

        input = new FFMediaInput(path);
        videoStreamIndex = av_find_best_stream(input.fmt, AVMEDIA_TYPE_VIDEO, -1, -1,
                (org.bytedeco.ffmpeg.avcodec.AVCodec) null, 0);
        if (videoStreamIndex < 0) {
            throw new RuntimeException("No video stream found in GIF: " + path);
        }

        stream = input.streamAtIndex(videoStreamIndex);
        AVRational tb = stream.time_base();
        timeBaseSeconds = av_q2d(tb);

        decoder = new FFCodec(stream);
        decoder.openDecoder();

        running = true;
        decodeThread = new Thread(this::decodeLoop, "gif-decode:" + path.getFileName());
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    /** Call once per frame on your GL thread. */
    public void updateAndDraw(GL2D g, int x, int y) {
        // grab first frame
        if (!started) {
            Frame f = ready.poll();
            if (f == null) return;

            ensureTexture(f.w, f.h);
            uploadToGpu(f);
            recycle(f);

            started = true;
            long now = System.nanoTime();
            nextSwapNs = now + f.delayNs;
        } else {
            long now = System.nanoTime();

            // Stable scheduling: accumulate nextSwapNs (prevents jitter)
            while (now >= nextSwapNs) {
                Frame f = ready.poll();
                if (f == null) {
                    nextSwapNs = now + msToNs(MIN_DELAY_MS);
                    break;
                }

                ensureTexture(f.w, f.h);
                uploadToGpu(f);
                nextSwapNs += f.delayNs;
                recycle(f);
            }
        }

        if (tex != null) {
            g.drawImage(tex, x, y, tex.getWidth(), tex.getHeight(), GL2D.IMAGE_NORMAL);
        }
    }

    private void ensureTexture(int w, int h) {
        if (tex == null) {
            tex = new GLBufferedTexture(w, h);
            initPool(w, h); // now we know size -> enable reuse
        } else if (tex.getWidth() != w || tex.getHeight() != h) {
            tex.resize(w, h);
            initPool(w, h);
        }
    }

    private void initPool(int w, int h) {
        pool.clear();
        int bytes = w * h * 4;
        for (int i = 0; i < POOL_CAP; i++) {
            pool.offer(ByteBuffer.allocateDirect(bytes));
        }
    }

    private void uploadToGpu(Frame f) {
        f.rgba.position(0);
        tex.upload(f.rgba);
        tex.send();
    }

    private void recycle(Frame f) {
        if (f == null || f.rgba == null) return;
        f.rgba.position(0);
        pool.offer(f.rgba); // best effort recycle
    }

    private void decodeLoop() {
        try (FFPacket pkt = new FFPacket();
             FFFrame frame = new FFFrame()) {

            long lastPts = AV_NOPTS_VALUE;

            while (running) {
                int rr = input.readInto(pkt);

                if (rr == AVERROR_EOF) {
                    loopToStart();
                    lastPts = AV_NOPTS_VALUE;
                    continue;
                }
                if (rr < 0) {
                    // For desktop sprites: treat read error like EOF and loop
                    loopToStart();
                    lastPts = AV_NOPTS_VALUE;
                    continue;
                }

                if (pkt.streamIndex() != videoStreamIndex) {
                    pkt.unref();
                    continue;
                }

                int r = decoder.send(pkt);
                pkt.unref();
                if (r < 0 && r != AVERROR_EAGAIN()) {
                    loopToStart();
                    lastPts = AV_NOPTS_VALUE;
                    continue;
                }

                while (running) {
                    r = decoder.receive(frame);
                    if (r == AVERROR_EAGAIN() || r == AVERROR_EOF) break;
                    if (r < 0) {
                        frame.unref();
                        break;
                    }

                    int w = frame.f.width();
                    int h = frame.f.height();
                    int srcFmt = frame.f.format();

                    // Init/reinit sws
                    if (sws == null || w != swsW || h != swsH || srcFmt != swsSrcFmt) {
                        if (sws != null) sws_freeContext(sws);

                        sws = sws_getContext(
                                w, h, srcFmt,
                                w, h, AV_PIX_FMT_RGBA,
                                SWS_BILINEAR,
                                null, null, (DoublePointer) null
                        );
                        if (sws == null) throw new RuntimeException("sws_getContext failed");
                        swsW = w; swsH = h; swsSrcFmt = srcFmt;
                    }

                    // IMPORTANT: before pool is initialized (unknown size), just allocate.
                    // Once pool is initialized, reuse.
                    ByteBuffer out = pool.poll();
                    if (out == null || out.capacity() < w * h * 4) {
                        out = ByteBuffer.allocateDirect(w * h * 4);
                    }
                    out.clear();

                    // sws_scale into out
                    BytePointer dst = new BytePointer(out);
                    PointerPointer<BytePointer> dstData = new PointerPointer<>(4);
                    dstData.put(0, dst);
                    dstData.put(1, null);
                    dstData.put(2, null);
                    dstData.put(3, null);

                    IntPointer dstLinesize = new IntPointer(4);
                    dstLinesize.put(0, w * 4);

                    int outLines = sws_scale(
                            sws,
                            frame.f.data(),
                            frame.f.linesize(),
                            0,
                            h,
                            dstData,
                            dstLinesize
                    );
                    if (outLines <= 0) {
                        pool.offer(out);
                        frame.unref();
                        continue;
                    }

                    out.position(0);
                    out.limit(w * h * 4);

                    long pts = safeBestEffortPts(frame.f);
                    int delayMs = computeDelayMs(frame.f.pkt_duration(), pts, lastPts, timeBaseSeconds);
                    lastPts = pts;

                    Frame f = new Frame(out, w, h, msToNs(delayMs));

                    // Backpressure: preserve order and correct playback (no “3 frames” problem)
                    ready.put(f);

                    frame.unref();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (sws != null) {
                sws_freeContext(sws);
                sws = null;
            }
        }
    }

    private void loopToStart() {
        try {
            int r = av_seek_frame(input.fmt, videoStreamIndex, 0, AVSEEK_FLAG_BACKWARD);
            if (r < 0) input.seekToTs(0, AVSEEK_FLAG_BACKWARD);
            decoder.flush();
        } catch (Throwable ignored) {}
    }

    private static long safeBestEffortPts(org.bytedeco.ffmpeg.avutil.AVFrame f) {
        try { return f.best_effort_timestamp(); }
        catch (Throwable ignored) { return AV_NOPTS_VALUE; }
    }

    private static int computeDelayMs(long pktDuration, long pts, long lastPts, double tbSec) {
        int ms = -1;

        // 1) pkt_duration
        if (pktDuration > 0 && tbSec > 0) {
            ms = (int) Math.round(pktDuration * tbSec * 1000.0);
        }

        // 2) pts delta fallback
        if (ms <= 0 && pts != AV_NOPTS_VALUE && lastPts != AV_NOPTS_VALUE && pts > lastPts && tbSec > 0) {
            ms = (int) Math.round((pts - lastPts) * tbSec * 1000.0);
        }

        // 3) default
        if (ms <= 0) ms = DEFAULT_DELAY_MS;

        if (ms < MIN_DELAY_MS) ms = MIN_DELAY_MS;
        if (ms > MAX_DELAY_MS) ms = MAX_DELAY_MS;
        return ms;
    }

    private static long msToNs(int ms) {
        return (long) ms * 1_000_000L;
    }

    @Override
    public void close() {
        running = false;
        if (decodeThread != null) {
            try { decodeThread.join(); } catch (InterruptedException ignored) {}
            decodeThread = null;
        }

        Frame f;
        while ((f = ready.poll()) != null) recycle(f);
        pool.clear();

        if (decoder != null) { decoder.close(); decoder = null; }
        if (input != null) { input.close(); input = null; }

        if (sws != null) { sws_freeContext(sws); sws = null; }

        if (tex != null) { tex.close(); tex = null; }
    }

    private static final class Frame {
        final ByteBuffer rgba;
        final int w, h;
        final long delayNs;

        Frame(ByteBuffer rgba, int w, int h, long delayNs) {
            this.rgba = rgba;
            this.w = w;
            this.h = h;
            this.delayNs = delayNs;
        }
    }
}