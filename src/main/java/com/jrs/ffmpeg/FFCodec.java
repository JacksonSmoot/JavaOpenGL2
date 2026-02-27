package com.jrs.ffmpeg;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacpp.PointerPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bytedeco.ffmpeg.global.avcodec.*;

public class FFCodec implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FFCodec.class);

    public final AVCodecContext cc;

    public AVCodecParameters cp;

    public AVCodec c;

    private boolean closed;

    private boolean decoderOpen;

    public FFCodec(AVStream stream) {
        if (stream == null) throw new IllegalArgumentException("input stream is null");
        cp = stream.codecpar();
        if (cp == null) {
            throw new NullPointerException("stream.codecpar() returned null");
        }
        c = avcodec.avcodec_find_decoder(cp.codec_id());
        if(c == null) {
            throw new NullPointerException("avcodec.avcodec_find_decoder failed to find valid decoder and returned null");
        }
        cc = avcodec.avcodec_alloc_context3(c);
        if(cc == null){
            throw new OutOfMemoryError("Failed to allocate AVcc");
        }
        avcodec.avcodec_parameters_to_context(cc, cp);
        this.closed = false;
        this.decoderOpen = false;
    }

    public void openDecoder(){
        if(decoderOpen){
            logger.warn("Tried to open decoder while already open, action blocked.");
            return;
        }
        if (avcodec_open2(cc, c, (AVDictionary) null) < 0) {
            throw new RuntimeException("Failed to open valid decoder");
        }
        decoderOpen = true;
    }

    public void openDecoder(AVDictionary dictionary){
        if(decoderOpen){
            logger.warn("Tried to open decoder while already open, action blocked.");
            return;
        }
        if (avcodec_open2(cc, c, dictionary) < 0) {
            throw new RuntimeException("Failed to open valid decoder");
        }
        decoderOpen = true;
    }

    public void openDecoder(PointerPointer<AVDictionary> dictionaryPointerPointer){
        if(decoderOpen){
            logger.warn("Tried to open decoder while already open, action blocked.");
            return;
        }
        if (avcodec_open2(cc, c, dictionaryPointerPointer) < 0) {
            throw new RuntimeException("Failed to open valid decoder");
        }
        decoderOpen = true;
    }

    public int send(FFPacket packet){
        return send(packet.p);
    }
    
    public int send(AVPacket packet){
        return avcodec_send_packet(cc, packet);
    }
    
    public int receive(FFFrame frame){
        if(frame == null) throw new NullPointerException("ffframe is null");
        return receive(frame.f);
    }

    public int receive(AVFrame frame){
        if(frame == null) throw new NullPointerException("frame is null");
        return avcodec_receive_frame(cc, frame);
    }
    
    
    public void flush(){
        avcodec_flush_buffers(cc);
        // c = cc.codec();
    }

    @Override
    public void close() {
        if (closed) return;
        if(cc != null) avcodec.avcodec_free_context(cc);
        closed = true;
    }


}
