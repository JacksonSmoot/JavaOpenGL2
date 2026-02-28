package com.jrs.media;
import com.jrs.ffmpeg.*;
import com.jrs.gl.resources.texture.GLBufferedTexture;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.swr_convert;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

public class MusicPlayerV3 {

    private static final Logger logger = LoggerFactory.getLogger(MusicPlayerV3.class);

    private final Path path;

    private FFMediaInput ffmpegMedia;

    private FFAudioCodec codec;

    private SwrContext swrCtx;

    private int audioStreamIndex = -1;

    private OpenALStream openALStream;

    private int outRate = -1;

    private AVChannelLayout outChLayout;

    private int outFmt = AV_SAMPLE_FMT_S16;

    // Threads
    private volatile boolean running;

    private Thread demuxThread;

    private Thread audioThread;

    // Queues
    private final int audioPktQSize = 512;

    private final BlockingQueue<FFPacket> audioPktQ = new ArrayBlockingQueue<>(audioPktQSize);

    private static final FFPacket AUDIO_EOF_PACKET = new FFPacket(null);

    private final AtomicBoolean eof = new AtomicBoolean(false);

    private final AtomicBoolean doneAudio = new AtomicBoolean(false);

    private final AtomicBoolean requestAudioPause  = new AtomicBoolean(false);

    private final AtomicBoolean requestDemuxPause  = new AtomicBoolean(false);

    private final AtomicBoolean audioPaused =  new AtomicBoolean(true);

    private final AtomicBoolean demuxPaused = new AtomicBoolean(true);

    private final AtomicBoolean playing = new AtomicBoolean(false);

    private GLBufferedTexture albumArt;

    private long streamStartPts;

    private AVRational audioTb;

    public static final int SEEK_NORMAL = 1;

    public static final int SEEK_LEGACY = 2;

    private int seekType = 1;

    private long basePositionNs = 0;

    public MusicPlayerV3(Path path) {
        this.path = path;

        if(path.toString().endsWith(".mp3")){
            seekType = SEEK_LEGACY;
            logger.warn("Given MP3, Using slower legacy seek path for audio seeking and looping");
        }

        setupOpenAL();

        setupFFmpeg();
    }

    // -- PUBLIC API --
    public long getDurationNs() {
        long durationUs = ffmpegMedia.fmt.duration();
        if (durationUs <= 0) return -1; // unknown
        return durationUs * 1_000L;
    }

    public long getElapsedNs() {
        if (!playing.get()) {
            return basePositionNs;
        }
        return basePositionNs + openALStream.getPlayedTimeNs();
    }

    public GLBufferedTexture getAlbumArt() {
        return albumArt;
    }

    public void start() {
        running = true;

        demuxThread = new Thread(this::demuxLoop, "Demux");
        audioThread = new Thread(this::audioLoop, "AudioDecode");

        demuxThread.setDaemon(true);
        audioThread.setDaemon(true);

        demuxThread.start();
        audioThread.start();
    }

    public void play(){
        if(!demuxPaused.get() || ! audioPaused.get()) {
            logger.warn("Can not play, already playing");
            return;
        }
        playing.set(true);
        if(getElapsedNs() != basePositionNs) seekToNs(basePositionNs);
        requestAudioPause.set(false);
        requestDemuxPause.set(false);
        demuxPaused.set(false);
        audioPaused.set(false);

    }

    public void pause() {

        // openAL.stop();


        long playedNs = openALStream.getPlayedTimeNs();

        basePositionNs += playedNs;

        playing.set(false);

        requestAudioPause.set(true);
        requestDemuxPause.set(true);

        clearPackets(audioPktQ);

        while(!audioPaused.get() || !demuxPaused.get()) {
            try { Thread.sleep(1); }
            catch (InterruptedException ignored) {}
        }

        openALStream.stopAndClear();
    }

    public void close() throws InterruptedException {
        openALStream.close();
//        openAL.close();
//        openAL.close();
        demuxThread.interrupt();
        demuxThread.join();
        audioThread.interrupt();
        audioThread.join();
    }

    public void seekToSec(long seconds){
        seekToNs(TimeUnit.SECONDS.toNanos(seconds));
    }

    public void seekToMs(long ms){
        long nanoseconds = TimeUnit.MILLISECONDS.toNanos(ms);
        seekToNs(nanoseconds);
    }

    public long getStreamStartPts() {
        return streamStartPts;
    }

    private void computeAudioStartPts() {
        AVStream st = ffmpegMedia.streamAtIndex(audioStreamIndex);
        audioTb = st.time_base();

        long sStart = st.start_time(); // stream time_base units, or AV_NOPTS_VALUE
        long fStartUs = ffmpegMedia.fmt.start_time(); // AV_TIME_BASE units (microseconds), or AV_NOPTS_VALUE

        long startPts = 0;

        if (sStart != AV_NOPTS_VALUE) {
            // This is already in stream time_base units → best case.
            startPts = sStart;
        } else if (fStartUs != AV_NOPTS_VALUE) {
            // Convert format start_time (microseconds) to stream time_base units.
            AVRational avTimeBaseQ = new AVRational().num(1).den(AV_TIME_BASE); // 1/1_000_000
            startPts = av_rescale_q(fStartUs, avTimeBaseQ, audioTb);
        } else {
            // No info; assume 0.
            startPts = 0;
        }

        streamStartPts = startPts;
    }

    // -------------------- SETUP --------------------
    private void setupOpenAL(){
        try {
            openALStream = OpenALManager.manager.requestStream();
            if (openALStream.hasFloat32()) outFmt = AV_SAMPLE_FMT_FLT;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        openAL = new OpenALStreamer(8);
//        openAL.open();
//        if (openAL.hasFloat32Format()) outFmt = AV_SAMPLE_FMT_FLT;
//        openAL.setGain(0.5f);
    }

    private void setAlbumArt() {
        AVFormatContext fmt = ffmpegMedia.fmt;

        AVStream artStream = null;

        for (int i = 0; i < fmt.nb_streams(); i++) {
            AVStream st = fmt.streams(i);

            String type = switch (st.codecpar().codec_type()) {
                case AVMEDIA_TYPE_VIDEO -> "video";
                case AVMEDIA_TYPE_AUDIO -> "audio";
                case AVMEDIA_TYPE_ATTACHMENT -> "attachment";
                case AVMEDIA_TYPE_SUBTITLE -> "subtitle";
                case AVMEDIA_TYPE_NB -> "nb";
                case AVMEDIA_TYPE_UNKNOWN -> "unknown";
                case AVMEDIA_TYPE_DATA -> "data";
                default -> "unknown (ERROR)";
            };
            System.out.println("Index: " + i + ", TYPE: " + type);

            if (st.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO &&
                    (st.disposition() & AV_DISPOSITION_ATTACHED_PIC) != 0) {
                System.out.println("ALBUM ART FOUND at stream " + i);
                artStream = st;
                break;
            }
        }

        if (artStream == null) {
            System.out.println("No album art stream found.");
            return;
        }

        // ----- Pull the attached picture packet -----
        // This is a *reference* owned by the stream; do NOT free it.
        AVPacket picPkt = artStream.attached_pic();
        if (picPkt == null || picPkt.data() == null || picPkt.size() <= 0) {
            System.out.println("Album art stream had no attached_pic packet data.");
            return;
        }

        // ----- Decode it to an AVFrame (likely MJPEG/PNG/etc) -----
        AVCodecParameters par = artStream.codecpar();
        AVCodec dec = avcodec_find_decoder(par.codec_id());
        if (dec == null) {
            throw new RuntimeException("No decoder for album art codec_id=" + par.codec_id());
        }

        AVCodecContext artCtx = avcodec_alloc_context3(dec);
        if (artCtx == null) throw new OutOfMemoryError("avcodec_alloc_context3 failed for album art");

        try {
            int r = avcodec_parameters_to_context(artCtx, par);
            if (r < 0) throw new RuntimeException("avcodec_parameters_to_context(album art) failed: " + r);

            r = avcodec_open2(artCtx, dec, (AVDictionary) null);
            if (r < 0) throw new RuntimeException("avcodec_open2(album art) failed: " + r);

            AVFrame decoded = av_frame_alloc();
            if (decoded == null) throw new OutOfMemoryError("av_frame_alloc failed (album art)");

            try {
                // Some FFmpeg builds want a ref packet, so make a safe local ref
                AVPacket pktRef = av_packet_alloc();
                if (pktRef == null) throw new OutOfMemoryError("av_packet_alloc failed (album art)");
                try {
                    r = av_packet_ref(pktRef, picPkt);
                    if (r < 0) throw new RuntimeException("av_packet_ref(album art) failed: " + r);

                    r = avcodec_send_packet(artCtx, pktRef);
                    if (r < 0) throw new RuntimeException("avcodec_send_packet(album art) failed: " + r);

                    r = avcodec_receive_frame(artCtx, decoded);
                    if (r < 0) throw new RuntimeException("avcodec_receive_frame(album art) failed: " + r);
                } finally {
                    av_packet_unref(pktRef);
                    av_packet_free(pktRef);
                }

                int w = decoded.width();
                int h = decoded.height();
                int srcPixFmt = decoded.format();

                if (w <= 0 || h <= 0) {
                    throw new RuntimeException("Decoded album art has invalid size: " + w + "x" + h);
                }

                // ----- Convert decoded frame -> RGBA -----
                SwsContext sws = swscale.sws_getContext(
                        w, h, srcPixFmt,
                        w, h, AV_PIX_FMT_RGBA,
                        SWS_BILINEAR,
                        null, null, (DoublePointer) null
                );
                if (sws == null) throw new RuntimeException("sws_getContext failed for album art");

                try {
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

                    int outH = swscale.sws_scale(
                            sws,
                            decoded.data(),
                            decoded.linesize(),
                            0,
                            h,
                            dstData,
                            dstLinesize
                    );

                    if (outH <= 0) {
                        throw new RuntimeException("sws_scale(album art) failed: outH=" + outH);
                    }

                    rgba.position(0);
                    rgba.limit(rgbaBytes);

                    albumArt = new GLBufferedTexture(w, h);

                    albumArt.upload(rgba);
                    albumArt.send();

                    System.out.println("Album art decoded: " + w + "x" + h + " RGBA bytes=" + rgbaBytes);

                } finally {
                    swscale.sws_freeContext(sws);
                }

            } finally {
                av_frame_unref(decoded);
                av_frame_free(decoded);
            }
        } finally {
            avcodec_free_context(artCtx);
        }
    }

    private void setupFFmpeg() {
        ffmpegMedia = new FFMediaInput(path);

        audioStreamIndex = ffmpegMedia.getStreamIndex(AVMEDIA_TYPE_AUDIO, 0);

        if(audioStreamIndex == -1) {
            throw new RuntimeException("Failed to get FFmpeg input stream");
        }

        codec = ffmpegMedia.ffAudioCodecFromStream(0);

        codec.openDecoder();

        outRate = codec.cc.sample_rate();

        outChLayout = new AVChannelLayout();

        swrCtx = swresample.swr_alloc();

        if (swrCtx == null) throw new RuntimeException("swr_alloc failed");

        AVChannelLayout inLayout = codec.cl; // input
        outChLayout = new AVChannelLayout();
        // av_channel_layout_default(outChLayout, 2); // stereo
        if (codec.cl.nb_channels() == 1) {
            av_channel_layout_from_mask(outChLayout, AV_CH_LAYOUT_MONO);
        } else {
            av_channel_layout_from_mask(outChLayout, AV_CH_LAYOUT_STEREO);
        }

        // Set options (these names are FFmpeg option keys)
        av_opt_set_chlayout(swrCtx, "in_chlayout", inLayout, 0);
        av_opt_set_chlayout(swrCtx, "out_chlayout", outChLayout, 0);

        av_opt_set_int(swrCtx, "in_sample_rate", codec.cc.sample_rate(), 0);
        av_opt_set_int(swrCtx, "out_sample_rate", outRate, 0);

        av_opt_set_sample_fmt(swrCtx, "in_sample_fmt", codec.cc.sample_fmt(), 0);
        av_opt_set_sample_fmt(swrCtx, "out_sample_fmt", outFmt, 0);

        int ret = swresample.swr_init(swrCtx);
        if (ret < 0) throw new RuntimeException("swr_init failed: " + ret);

        setAlbumArt();

        computeAudioStartPts();

        openALStream.setSampleRate(outRate);
    }

    public void seekToNs(long ns){
        switch(seekType) {
            case SEEK_NORMAL -> seekToNsNormal(ns);
            case SEEK_LEGACY -> seekToNsLegacy(ns);
            default -> throw new RuntimeException("seekToNs() failed (invalid seek backend type");
        }
    }

    public void seekToNsNormal(long ns) {
        long targetPts = nsToStreamPts(ns, audioTb, streamStartPts);

        boolean isPlaying = playing.get();

        playing.set(false);
        eof.set(false);
        doneAudio.set(false);

        basePositionNs = ns;



//        openAL.stop();
//        while (openAL.queuedBufferCount() > 0) openAL.pumpProcessed();
//        openAL.close();
//        setupOpenAL();

        openALStream.restart();

        clearPackets(audioPktQ);

        int ret = av_seek_frame(ffmpegMedia.fmt, audioStreamIndex, targetPts, AVSEEK_FLAG_BACKWARD);
        if (ret < 0) throw new RuntimeException("Seek failed: " + ret);

        avformat_flush(ffmpegMedia.fmt);
        codec.flush();
        swresample.swr_close(swrCtx);
        swresample.swr_init(swrCtx);

        eof.set(false);
        doneAudio.set(false);
        playing.set(isPlaying);
    }

    public void seekToNsLegacy(long ns) {
        final long targetPts = nsToStreamPts(ns, audioTb, streamStartPts);

        boolean isPlaying = playing.get();

        playing.set(false);
        eof.set(false);
        doneAudio.set(false);

        basePositionNs = ns;

//        openAL.stop();
//        openAL.close();
//        setupOpenAL();
        openALStream.restart();

        clearPackets(audioPktQ);

        int ret = av_seek_frame(ffmpegMedia.fmt, audioStreamIndex, streamStartPts, AVSEEK_FLAG_BACKWARD);
        if (ret < 0) throw new RuntimeException("Seek failed: " + ret);

        avformat_flush(ffmpegMedia.fmt);
        codec.flush();
        swresample.swr_close(swrCtx);
        swresample.swr_init(swrCtx);

        if(ns == 0){
            eof.set(false);
            doneAudio.set(false);
            playing.set(isPlaying);
        }

        FFPacket pkt = new FFPacket();
        FFFrame frame = new FFFrame();
        long synthPts = streamStartPts;
        AVRational samplesTb = new AVRational().num(1).den(outRate);
        boolean found = false;

        while (!found) {
            ret = pkt.readFrame(ffmpegMedia);
            if (ret < 0) {
                if (ret == AVERROR_EOF) break;
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                continue;
            }

            if (pkt.streamIndex() != audioStreamIndex) {
                pkt.unref();
                continue;
            }

            int sret = codec.send(pkt);
            pkt.unref();
            if (sret < 0) continue;

            while (true) {
                int rret = codec.receive(frame);
                if (rret == 0) {
                    long pts = frame.f.pts();
                    if (pts == AV_NOPTS_VALUE) {
                        long samples = frame.f.nb_samples();
                        long delta = av_rescale_q(samples, samplesTb, audioTb);
                        pts = synthPts;
                        synthPts += delta;
                    } else {
                        synthPts = pts;
                    }

                    if (pts >= targetPts) {
                        found = true;
                        frame.unref();
                        break;
                    }

                    frame.unref();
                    continue;
                }

                if (rret == AVERROR_EAGAIN()) break;
                if (rret == AVERROR_EOF()) break;
                break;
            }
        }

        pkt.close();
        frame.close();

        if (!found) {
            throw new RuntimeException("seekToNsLegacy: couldn't reach target (EOF?) targetPts=" + targetPts);
        }

        eof.set(false);
        doneAudio.set(false);
        playing.set(isPlaying);
    }

    private void demuxLoop() {
        FFPacket tmp = new FFPacket();
        try {
            while (running) {

                if(demuxPaused.get()) {
                    if(eof.get() && doneAudio.get()) {
                        seekToNs(0);
                        audioPaused.set(false);
                        demuxPaused.set(false);
                        playing.set(true);
                    }
                    Thread.sleep(1);
                    continue;
                }

                if(requestDemuxPause.get()){
                    demuxPaused.set(true);
                    Thread.sleep(1);
                    continue;
                }

                int r = tmp.readFrame(ffmpegMedia);
                if (r < 0) {
                    if (r == AVERROR_EOF) {
                        eof.set(true);
                        demuxPaused.set(true);
                        audioPktQ.put(AUDIO_EOF_PACKET);
                    }
                    continue;
                }

                FFPacket copy = FFPacket.refFrom(tmp);

                if (tmp.streamIndex() == audioStreamIndex) {
                    audioPktQ.put(copy);
                } else {
                    copy.close();
                }

                tmp.unref();
            }
        } catch (InterruptedException ie) {
            logger.warn("Demux loop interrupted: {}", ie.getMessage());
        } finally {
            tmp.close();
        }
    }

    // -------------------- audio decode --------------------

    private void audioLoop() {
        FFPacket pkt = null;
        FFFrame audioFrame = new FFFrame();
        try {
            while (running) {
                if(audioPaused.get()) {
                    Thread.sleep(1);
                    continue;
                }

                if(requestAudioPause.get()){
                    audioPaused.set(true);
                    continue;
                }

                if (openALStream.getNumFreeSlots() == 0) {
                    Thread.sleep(1);
                    continue;
                }

                pkt = audioPktQ.take();
                if(pkt == AUDIO_EOF_PACKET) {
                    while(openALStream.isPlaying()){Thread.sleep(1);}
                    doneAudio.set(true);
                    audioPaused.set(true);
                    continue;
                }

                int ret = codec.send(pkt);
                pkt.close();
                pkt = null;

                if (ret < 0) continue;

                while (true) {
                    ret = codec.receive(audioFrame);
                    if (ret == 0) {
                        int inSamples = audioFrame.f.nb_samples();

                        int maxOutSamples = (int) av_rescale_rnd(
                                swresample.swr_get_delay(swrCtx, codec.cc.sample_rate()) + inSamples,
                                outRate,
                                codec.cc.sample_rate(),
                                AV_ROUND_UP
                        );

                        int outChannels = (outChLayout.u_mask() == AV_CH_LAYOUT_MONO) ? 1 : 2;
                        int bytesPerSample = (outFmt == AV_SAMPLE_FMT_FLT) ? 4 : 2;
                        int outBytesCap = maxOutSamples * outChannels * bytesPerSample;

                        Pointer raw = av_malloc(outBytesCap);
                        BytePointer outBuf = new BytePointer(raw).capacity(outBytesCap);

                        PointerPointer<BytePointer> outData = new PointerPointer<>(1);
                        outData.put(0, outBuf);

                        int outSamples = swr_convert(
                                swrCtx,
                                outData,
                                maxOutSamples,
                                audioFrame.f.data(),
                                inSamples
                        );

                        if (outSamples < 0) {
                            av_free(raw);
                            audioFrame.unref();
                            break;
                        }

                        int outBytes = outSamples * outChannels * bytesPerSample;
                        ByteBuffer pcm = outBuf.position(0).capacity(outBytes).asBuffer();
                        pcm.position(0);
                        pcm.limit(outBytes);
                        ByteBuffer buffOut = copyFromNativeToDirect(outBuf, outBytes);

                        // openALStream.enqueueChunk(buffOut, 2, true, outSamples);
                        if (outFmt == AV_SAMPLE_FMT_FLT) {
                            openALStream.enqueueChunk(buffOut, 2, true, outSamples);
                        } else {
                            openALStream.enqueueChunk(buffOut, 2, false, outSamples);
                        }
                        av_free(raw);
                        audioFrame.unref();
                        continue;
                    }

                    if (ret == AVERROR_EAGAIN()) break;
                    if (ret == AVERROR_EOF()) break;
                    break;
                }
            }
        } catch (InterruptedException ie) {
            logger.warn("Audio loop interrupted: {}", ie.getMessage());
        } finally {
            if (pkt != null) {
                try {
                    pkt.close();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static ByteBuffer copyFromNativeToDirect(BytePointer src, int bytes) {
        if (src == null) throw new NullPointerException("src");
        if (bytes < 0) throw new IllegalArgumentException("bytes < 0");

        // JVM-owned direct buffer (freed by GC eventually)
        ByteBuffer out = ByteBuffer.allocateDirect(bytes);

        ByteBuffer view = src.position(0).limit(bytes).asBuffer();

        out.put(view);
        out.flip();
        return out;
    }

    // -------------------- helpers --------------------

    private void clearPackets(BlockingQueue<FFPacket> q) {
        FFPacket p;
        while ((p = q.poll()) != null) {
            if(p!=AUDIO_EOF_PACKET) {
                try {
                    p.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // -------------------- time helpers --------------------
    public static long nsToStreamPts(long ns, AVRational audioTb, long streamStartPts) {
        // ns -> us
        long us = ns / 1_000L;

        AVRational avTimeBaseQ = new AVRational().num(1).den(AV_TIME_BASE); // 1/1_000_000
        long ptsNoOffset = av_rescale_q(us, avTimeBaseQ, audioTb);

        return streamStartPts + ptsNoOffset;
    }

    public static long streamPtsToNs(long streamPts, AVRational audioTb, long streamStartPts) {
        if (streamPts == AV_NOPTS_VALUE) return -1;

        long ptsNoOffset = streamPts - streamStartPts;
        if (ptsNoOffset < 0) ptsNoOffset = 0;

        AVRational avTimeBaseQ = new AVRational().num(1).den(AV_TIME_BASE); // 1/1_000_000
        long us = av_rescale_q(ptsNoOffset, audioTb, avTimeBaseQ);

        return us * 1_000L;
    }
}