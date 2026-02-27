package com.jrs.media;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTFloat32.AL_FORMAT_MONO_FLOAT32;
import static org.lwjgl.openal.EXTFloat32.AL_FORMAT_STEREO_FLOAT32;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Streaming OpenAL wrapper for PCM 16-bit interleaved audio (mono/stereo).
 *
 * Key features:
 * - Stable streaming with a pool of AL buffers
 * - "Samples actually played" clock: processed-buffer accounting + AL_SAMPLE_OFFSET
 * - Buffered/latency queries, underrun handling, and clock reset for seek/loop
 *
 * Assumptions:
 * - PCM16 little-endian interleaved
 * - channels: 1 or 2
 */
public final class OpenALStreamer implements AutoCloseable {

    private final int bufferCount;

    private long device = NULL;
    private long context = NULL;

    private int source = 0;
    private int[] buffers;

    // Buffers available to fill (not currently queued on the source)
    private final ArrayDeque<Integer> freeBuffers = new ArrayDeque<>();

    // For every AL buffer we queue, we also queue its sample count (same order).
    // When a buffer becomes PROCESSED and we unqueue it, we pop its sample count and add to playedSamples.
    private final ArrayDeque<Integer> queuedBufferSamples = new ArrayDeque<>();

    // "Total samples fully played" (from processed/unqueued buffers)
    private final AtomicLong playedSamples = new AtomicLong(0);

    // Track format to catch accidental changes mid-stream
    private int currentSampleRate = -1;
    private int currentChannels = -1;

    private boolean opened = false;

    // Optional: simple mute support
    private float lastGain = 1.0f;
    private final AtomicBoolean muted = new AtomicBoolean(false);

    public OpenALStreamer(int bufferCount) {
        if (bufferCount < 3) throw new IllegalArgumentException("bufferCount should be >= 3 for stable streaming");
        this.bufferCount = bufferCount;
    }

    public long getSamplesPlayed() {
        return playedSamples.get();
    }

    public void setPlayedSamples(long playedSamples) {
        this.playedSamples.set(playedSamples);
    }

    /** Opens the OpenAL device/context and creates buffers + one source. */
    public void open() {
        if (opened) return;

        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) throw new IllegalStateException("Failed to open OpenAL device");

        context = alcCreateContext(device, (int[]) null);
        if (context == NULL) {
            alcCloseDevice(device);
            device = NULL;
            throw new IllegalStateException("Failed to create OpenAL context");
        }

        if (!alcMakeContextCurrent(context)) {
            alcDestroyContext(context);
            alcCloseDevice(device);
            context = NULL;
            device = NULL;
            throw new IllegalStateException("Failed to make OpenAL context current");
        }

        AL.createCapabilities(ALC.createCapabilities(device));

        source = alGenSources();
        checkAl("alGenSources");

        buffers = new int[bufferCount];
        for (int i = 0; i < bufferCount; i++) {
            buffers[i] = alGenBuffers();
            checkAl("alGenBuffers");
            freeBuffers.add(buffers[i]);
        }

        // sensible defaults
        alSourcef(source, AL_GAIN, 1.0f);
        alSourcei(source, AL_LOOPING, AL_FALSE);
        checkAl("source defaults");

        opened = true;
    }



    /** Returns true if the OpenAL source is currently playing. */
    public boolean isPlaying() {
        requireOpen();
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        checkAl("alGetSourcei(AL_SOURCE_STATE)");
        return state == AL_PLAYING;
    }

    /** Returns how many OpenAL buffers are currently queued on the source. */
    public int queuedBufferCount() {
        requireOpen();
        int q = alGetSourcei(source, AL_BUFFERS_QUEUED);
        checkAl("alGetSourcei(AL_BUFFERS_QUEUED)");
        return q;
    }

    /** Returns number of free AL buffers available to fill right now. */
    public int freeBufferCount() {
        requireOpen();
        return freeBuffers.size();
    }

    /**
     * Reclaim processed buffers back into freeBuffers, and update playedSamples clock.
     * Call frequently (each tick or before querying time).
     */
    public void pumpProcessed() {
        if (!opened) return;

        int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
        checkAl("alGetSourcei(AL_BUFFERS_PROCESSED)");

        while (processed-- > 0) {
            int buf = alSourceUnqueueBuffers(source);
            checkAl("alSourceUnqueueBuffers");

            // Update "fully played" samples using the matching queued sample count
            Integer samp = queuedBufferSamples.pollFirst();
            if (samp != null) {
                playedSamples.addAndGet(samp);
            }

            freeBuffers.addLast(buf);
        }
    }

    /**
     * "Samples actually played" time in ns.
     *
     * Implementation:
     * - pumpProcessed() to advance playedSamples based on processed buffers
     * - add AL_SAMPLE_OFFSET (samples into the currently playing buffer)
     *
     * Note:
     * - AL_SAMPLE_OFFSET is only meaningful while playing.
     * - Some implementations report 0 when stopped/paused/underrun.
     */
    public long getPlayedTimeNs(int sampleRate) {
        requireOpen();

        // keep bookkeeping current
        // pumpProcessed();

        long base = playedSamples.get();

        int offset = 0;
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        checkAl("alGetSourcei(AL_SOURCE_STATE)");
        if (state == AL_PLAYING) {
            offset = alGetSourcei(source, AL_SAMPLE_OFFSET);
            checkAl("alGetSourcei(AL_SAMPLE_OFFSET)");
            if (offset < 0) offset = 0;
        }

        long totalSamples = base + (long) offset;
        return (totalSamples * 1_000_000_000L) / (long) sampleRate;
    }

    /**
     * Approx buffered time remaining (ns) in OpenAL queue.
     * Useful for targeting a stable latency.
     */
    public long getBufferedTimeNs(int sampleRate) {
        requireOpen();

        pumpProcessed();

        // queued samples still waiting to be played (including current buffer)
        long queuedSamples = 0;
        for (Integer s : queuedBufferSamples) queuedSamples += s;

        int offset = 0;
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        checkAl("alGetSourcei(AL_SOURCE_STATE)");
        if (state == AL_PLAYING) {
            offset = alGetSourcei(source, AL_SAMPLE_OFFSET);
            checkAl("alGetSourcei(AL_SAMPLE_OFFSET)");
            if (offset < 0) offset = 0;
        }

        long remaining = queuedSamples - offset;
        if (remaining < 0) remaining = 0;

        return (remaining * 1_000_000_000L) / (long) sampleRate;
    }

    /** Starts playback if there are queued buffers; otherwise does nothing. */
    public void startIfNeeded() {
        requireOpen();
        pumpProcessed();

        int state = alGetSourcei(source, AL_SOURCE_STATE);
        checkAl("alGetSourcei(AL_SOURCE_STATE)");

        if (state != AL_PLAYING) {
            int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
            checkAl("alGetSourcei(AL_BUFFERS_QUEUED)");
            if (queued > 0) {
                alSourcePlay(source);
                checkAl("alSourcePlay");
            }
        }
    }

    /** Detect common underrun condition: stopped while still having queued buffers. */
    public boolean isUnderrun() {
        requireOpen();
        int state = alGetSourcei(source, AL_SOURCE_STATE);
        checkAl("alGetSourcei(AL_SOURCE_STATE)");
        int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
        checkAl("alGetSourcei(AL_BUFFERS_QUEUED)");
        return state != AL_PLAYING && queued > 0;
    }

    /** Attempt to recover from underrun (restart playback if buffers queued). */
    public void recoverUnderrun() {
        requireOpen();
        if (isUnderrun()) {
            alSourcePlay(source);
            checkAl("alSourcePlay (recover underrun)");
        }
    }

    /**
     * Stop playback and clear queued buffers (buffers return to free list).
     * NOTE: This does NOT reset playedSamples. Use resetClock() for seek/loop.
     */
    public void stop() {
        if (!opened) return;

        alSourceStop(source);
        checkAl("alSourceStop");

        int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
        checkAl("alGetSourcei(AL_BUFFERS_QUEUED)");

        while (queued-- > 0) {
            int buf = alSourceUnqueueBuffers(source);
            checkAl("alSourceUnqueueBuffers (stop)");
            freeBuffers.add(buf);
        }

        // We cleared the queue, so discard pending sample counts that were never played
        queuedBufferSamples.clear();
    }

    /**
     * For seeking/looping:
     * - stop & flush queue
     * - reset playedSamples clock to 0
     */
    public void resetClockAndFlush() {
        requireOpen();
        stop();
        playedSamples.set(0);
        // keep format (or you can reset currentSampleRate/currentChannels too if desired)
    }

    public boolean hasFloat32Format(){
        requireOpen();
        return AL.getCapabilities().AL_EXT_FLOAT32;
    }

    /**
     * Queue a chunk of PCM16 interleaved audio.
     *
     * @param pcm        PCM bytes (16-bit signed little-endian), interleaved
     * @param sampleRate sample rate (e.g., 44100 or 48000)
     * @param channels   1 (mono) or 2 (stereo)
     * @return true if queued, false if no OpenAL buffer was available
     */
    public boolean queuePcm16(ByteBuffer pcm, int sampleRate, int channels) {
        requireOpen();

        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("OpenALStreamer supports only mono(1) or stereo(2) PCM16");
        }

        // Basic format consistency check
        if (currentSampleRate == -1) {
            currentSampleRate = sampleRate;
            currentChannels = channels;
        } else if (currentSampleRate != sampleRate || currentChannels != channels) {
            // safest behavior if format changes mid-stream
            resetClockAndFlush();
            currentSampleRate = sampleRate;
            currentChannels = channels;
        }

        pumpProcessed();

        Integer buf = freeBuffers.pollFirst();
        if (buf == null) return false;

        int bytes = pcm.remaining();
        if (bytes <= 0) {
            freeBuffers.addFirst(buf);
            return false;
        }

        // samples per channel frames = bytes / (channels * bytesPerSample)
        final int bytesPerSample = 2; // PCM16
        int samples = bytes / (channels * bytesPerSample);
        if (samples <= 0) {
            freeBuffers.addFirst(buf);
            return false;
        }

        int alFormat = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

        alBufferData(buf, alFormat, pcm, sampleRate);
        checkAl("alBufferData");

        alSourceQueueBuffers(source, buf);
        checkAl("alSourceQueueBuffers");

        // record sample count aligned with queue order
        queuedBufferSamples.addLast(samples);

        // handle underrun or not started: ensure playback runs
        startIfNeeded();
        recoverUnderrun();

        return true;
    }

    public void rewindAndFlush() {
        requireOpen();

        // Stop playback immediately
        alSourceStop(source);
        checkAl("alSourceStop");

        // Unqueue ALL queued buffers and return them to the free list
        int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
        checkAl("alGetSourcei(AL_BUFFERS_QUEUED)");

        while (queued-- > 0) {
            int buf = alSourceUnqueueBuffers(source);
            checkAl("alSourceUnqueueBuffers(rewindAndFlush)");
            freeBuffers.addLast(buf);
        }

        // Reset accounting so played time starts at 0
        playedSamples.set(0);
        queuedBufferSamples.clear();

        // Optional but good: reset offsets by re-setting the buffer binding
        alSourcei(source, AL_BUFFER, 0);
        checkAl("alSourcei(AL_BUFFER,0)");
    }

    public boolean queuePcmF32(ByteBuffer pcm, int sampleRate, int channels) {
        requireOpen();

        if (!AL.getCapabilities().AL_EXT_FLOAT32) {
            throw new IllegalStateException("OpenAL float32 not supported");
        }
        if (channels != 1 && channels != 2) throw new IllegalArgumentException();

        // same format consistency check as before...
        if (currentSampleRate == -1) {
            currentSampleRate = sampleRate;
            currentChannels = channels;
        } else if (currentSampleRate != sampleRate || currentChannels != channels) {
            resetClockAndFlush();
            currentSampleRate = sampleRate;
            currentChannels = channels;
        }

        pumpProcessed();
        Integer buf = freeBuffers.pollFirst();
        if (buf == null) return false;

        int bytes = pcm.remaining();
        if (bytes <= 0) { freeBuffers.addFirst(buf); return false; }

        final int bytesPerSample = 4; // float32
        int samples = bytes / (channels * bytesPerSample);
        if (samples <= 0) { freeBuffers.addFirst(buf); return false; }

        int alFormat = (channels == 1) ? AL_FORMAT_MONO_FLOAT32 : AL_FORMAT_STEREO_FLOAT32;

        alBufferData(buf, alFormat, pcm, sampleRate);
        checkAl("alBufferData(F32)");

        alSourceQueueBuffers(source, buf);
        checkAl("alSourceQueueBuffers(F32)");

        queuedBufferSamples.addLast(samples);

        startIfNeeded();
        recoverUnderrun();
        return true;
    }

    /** Set volume 0..1 */
    public void setGain(float gain) {
        requireOpen();
        lastGain = gain;
        if (!muted.get()) {
            alSourcef(source, AL_GAIN, gain);
            checkAl("alSourcef(AL_GAIN)");
        }
    }

    public float getGain() {
        return lastGain;
    }

    public void setMuted(boolean m) {
        requireOpen();
        muted.set(m);
        alSourcef(source, AL_GAIN, m ? 0.0f : lastGain);
        checkAl("alSourcef(AL_GAIN mute)");
    }

    public boolean isMuted() {
        return muted.get();
    }

    /** Close and release all OpenAL resources. */
    @Override
    public void close() {
        if (!opened) return;

        try { stop(); } catch (Throwable ignored) {}

        if (source != 0) {
            alDeleteSources(source);
            source = 0;
        }

        if (buffers != null) {
            for (int b : buffers) {
                if (b != 0) alDeleteBuffers(b);
            }
            buffers = null;
        }

        freeBuffers.clear();
        queuedBufferSamples.clear();
        playedSamples.set(0);

        alcMakeContextCurrent(NULL);
        if (context != NULL) {
            alcDestroyContext(context);
            context = NULL;
        }
        if (device != NULL) {
            alcCloseDevice(device);
            device = NULL;
        }

        opened = false;
        currentSampleRate = -1;
        currentChannels = -1;
        muted.set(false);
        lastGain = 1.0f;
    }

    // -------- helpers --------

    private void requireOpen() {
        if (!opened) throw new IllegalStateException("OpenALStreamer is not open(). Call open() first.");
    }

    private static void checkAl(String where) {
        int err = alGetError();
        if (err != AL_NO_ERROR) {
            throw new IllegalStateException(where + " failed: OpenAL error 0x" + Integer.toHexString(err));
        }
    }

    public static final long NULL_PTR = MemoryUtil.NULL;
}