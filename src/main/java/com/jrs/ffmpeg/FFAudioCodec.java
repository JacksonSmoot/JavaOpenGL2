package com.jrs.ffmpeg;

import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;

public final class FFAudioCodec extends FFCodec {
    public final AVChannelLayout cl;

    public FFAudioCodec(AVStream stream) {
        super(stream);
        cl = super.cp.ch_layout();
    }
}
