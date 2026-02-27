package com.jrs.ffmpeg;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.ffmpeg.global.avformat;

import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;

public class FFMediaInput implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FFMediaInput.class);
    public final AVFormatContext fmt;
    private final Map<Integer, List<Integer>> streams = new HashMap<>();
    private boolean closed;
    
    public void populateStreamPositionCache(){
        for (int i = 0; i < fmt.nb_streams(); i++) {
            int type = fmt.streams(i).codecpar().codec_type();
            streams.computeIfAbsent(type, k -> new ArrayList<>()).add(i);
        }
    }

    public FFMediaInput(java.nio.file.Path path) {
        this(path.toString());
    }

    public FFMediaInput(java.io.File file) {
        this(file.getPath());
    }

    public FFMediaInput(String path) {
        fmt = avformat.avformat_alloc_context();
        if (avformat.avformat_open_input(fmt, path, null, null) != 0)
            throw new RuntimeException("avformat_open_input failed: " + path);
        if (avformat.avformat_find_stream_info(fmt, (AVDictionary) null) < 0)
            throw new RuntimeException("avformat_find_stream_info failed");

        populateStreamPositionCache();

        System.out.println("FFMediaInput Created:");
        System.out.println("\tPath: " + path);
        System.out.println("\tStream Count: " + streamCount());
        System.out.println("\tStreams: " + streams.toString());
    }
    // avcodec_parameters_to_context

    public FFCodec ffCodecFromStream(int streamType, int streamNumber) {
        return new FFCodec(streamAtIndex(getStreamIndex(streamType, streamNumber)));
    }

    public FFAudioCodec ffAudioCodecFromStream(int streamNumber) {
        return new FFAudioCodec(streamAtIndex(getStreamIndex(AVMEDIA_TYPE_AUDIO, streamNumber)));
    }

    public int streamCount() { return fmt.nb_streams(); }

    public AVStream streamAtIndex(int idx) { return fmt.streams(idx); }

    public org.bytedeco.ffmpeg.avcodec.AVCodecParameters codecpar(int i) { return fmt.streams(i).codecpar(); }
    
    public int getStreamIndex(int streamType, int streamNumber){
        if(!streams.containsKey(streamType)){
            logger.warn("No cached stream found for streamType: {}", streamType);
            return -1;
        }
        List<Integer> list = streams.get(streamType);
        if(streamNumber < 0 || streamNumber >= list.size()){
            logger.warn("Invalid stream number: {}", streamNumber);
        }
        return list.get(streamNumber);
    }
    
    public int readInto(com.jrs.ffmpeg.FFPacket pkt) {
        return avformat.av_read_frame(fmt, pkt.p);
    }

    /** Returns 0 on success, AVERROR_EOF at end, <0 on other error. */
    public int readInto(org.bytedeco.ffmpeg.avcodec.AVPacket pkt) {
        return avformat.av_read_frame(fmt, pkt);
    }

    public void seekToTs(long ts, int flags) {
        int r = avformat.av_seek_frame(fmt, -1, ts, flags);
        if (r < 0) throw new RuntimeException("av_seek_frame failed: " + r);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        avformat.avformat_close_input(fmt);
        avformat.avformat_free_context(fmt);
    }
}
