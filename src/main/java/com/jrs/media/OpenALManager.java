package com.jrs.media;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTFloat32.AL_FORMAT_MONO_FLOAT32;
import static org.lwjgl.openal.EXTFloat32.AL_FORMAT_STEREO_FLOAT32;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class OpenALManager implements AutoCloseable {

    private static final int BYTES_PER_FLOAT32 = 4;
    private static final int BYTES_PER_PCM16 = 2;

    public static final OpenALManager manager = new OpenALManager();

    private final BlockingQueue<Runnable> commandQueue = new LinkedBlockingQueue<>();

    private Thread alThread;

    private volatile boolean running = false;

    private long device = NULL;

    private long context = NULL;

    private List<OpenALStream> streams = new ArrayList<>();

    private OpenALManager() {
        start();
    }

    private BlockingQueue<OpenALStream> freeStreamsQueue = new LinkedBlockingQueue<>();

    // THREAD SAFE PUBLIC API
    public OpenALStream requestStream() throws InterruptedException {
        commandQueue.put((Runnable) () -> {
            int bufferCount = 8;
            int source = alGenSources();

            int[] buffers = new int[bufferCount];
            for (int i = 0; i < bufferCount; i++) {
                buffers[i] = alGenBuffers();
            }

            alSourcef(source, AL_GAIN, 1.0f);
            alSourcei(source, AL_LOOPING, AL_FALSE);

            OpenALStream stream = new OpenALStream(source, buffers);

            stream.channels = 2;
            stream.sampleRate = 48000; // default sample rate, 48kh

            freeStreamsQueue.add(stream);
        });
        OpenALStream stream = freeStreamsQueue.take();
        streams.add(stream);
        System.out.println("STREAM FOUND!");
        return stream;
    }

    private void start(){
        if(!running){
            running = true;
            alThread = new Thread(this::alThreadLoop, "OpenALThread");
            alThread.setDaemon(true);
            alThread.start();
        }
    }

    private void open(){
        if(!Thread.currentThread().getName().equals("OpenALThread")){
            throw new IllegalStateException("Open called from thread that was not the OpenALThread.");
        }

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

    }

    public boolean post(Runnable r){
        if (!running) throw new IllegalStateException("OpenALManager not running");
        return commandQueue.offer(r);
    }

    public boolean hasFloat32(){
        AtomicBoolean hasFloat32 = new AtomicBoolean(false);
        AtomicBoolean doReturn = new AtomicBoolean(false);
        manager.post((Runnable) () -> {
            hasFloat32.set(AL.getCapabilities().AL_EXT_FLOAT32);
            doReturn.set(true);
        });
        while(!doReturn.get()){}
        return hasFloat32.get();
    }

    private void pumpProcessed() {
        for(OpenALStream stream : streams){
            int processed = alGetSourcei(stream.source, AL_BUFFERS_PROCESSED);

            while (processed-- > 0) {
                int buf = alSourceUnqueueBuffers(stream.source);
                stream.reclaimByBufferId(buf);
            }

            if(!stream.shouldPlay) continue;
            while(true){
                if(!stream.hasChunk() || !stream.hasFreeSlots()) break;
                int bufIdx = stream.claimFreeSlot();
                if(bufIdx == -1) break;
                PcmChunk chunk = stream.pollChunk();
                if(chunk == null) break;
                stream.markSlotQueued(bufIdx, chunk.samples);
                if(chunk.float32){
                    if(!queuePcmF32(stream, chunk, bufIdx)) {
                        System.out.println("FAILED TO QUEUE (F32)");
                    }
                }
                else{
                    if(!queuePcm16(stream, chunk, bufIdx)) {
                        System.out.println("FAILED TO QUEUE (S16)");
                    }

                }
            }
        }

    }

//    public void setGain(float gain){
//
//    }

    public void stop(OpenALStream stream) {
        alSourceStop(stream.source);

        int queued = alGetSourcei(stream.source, AL_BUFFERS_QUEUED);

        while (queued-- > 0) {
            int buf = alSourceUnqueueBuffers(stream.source);
            stream.reclaimByBufferIdNoSamplesAdded(buf);
        }
    }

    public void rewindAndFlush(OpenALStream stream) {
        // Stop playback immediately
        stop(stream);
        // Unqueue ALL queued buffers and return them to the free list
        stream.playedSamples = 0;
        // Optional but good: reset offsets by re-setting the buffer binding
        alSourcei(stream.source, AL_BUFFER, 0);

    }

    public boolean queuePcm16(OpenALStream stream, PcmChunk chunk, int bufIndex) {
        int bufId = stream.bufferIdForSlot(bufIndex);

        if (chunk.channels != 1 && chunk.channels != 2) {
            throw new IllegalArgumentException("OpenALStreamer supports only mono(1) or stereo(2) PCM16");
        }

        int bytes = chunk.data.remaining();
        if (bytes <= 0) {
            stream.reclaimByBufferIdNoSamplesAdded(bufId);
            return false;
        }

        // samples per channel frames = bytes / (channels * bytesPerSample)
        int samples = bytes / (chunk.channels * BYTES_PER_PCM16);
        if (samples <= 0) {
            stream.reclaimByBufferIdNoSamplesAdded(bufId);
            return false;
        }

        int alFormat = (chunk.channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

        alBufferData(bufId, alFormat, chunk.data, stream.sampleRate);

        alSourceQueueBuffers(stream.source, bufId);

        startIfNeeded(stream);
        return true;
    }

    private boolean queuePcmF32(OpenALStream stream, PcmChunk chunk, int bufIndex){
        int bufId = stream.bufferIdForSlot(bufIndex);

        if (!AL.getCapabilities().AL_EXT_FLOAT32) {
            throw new IllegalStateException("OpenAL float32 not supported");
        }
        int channels = chunk.channels;

        int bytes = chunk.data.remaining();
        if (bytes <= 0) {stream.reclaimByBufferIdNoSamplesAdded(bufId); return false;}

        int samples = bytes / (channels * BYTES_PER_FLOAT32);
        if (samples <= 0) { stream.reclaimByBufferIdNoSamplesAdded(bufId); return false; }

        int alFormat = (channels == 1) ? AL_FORMAT_MONO_FLOAT32 : AL_FORMAT_STEREO_FLOAT32;

        alBufferData(bufId, alFormat, chunk.data, stream.sampleRate);

        alSourceQueueBuffers(stream.source, bufId);

        startIfNeeded(stream);
        return true;
    }

    public void startIfNeeded(OpenALStream stream) {
        // pumpProcessed();

        int state = alGetSourcei(stream.source, AL_SOURCE_STATE);
        // checkAl("alGetSourcei(AL_SOURCE_STATE)");

        if (state != AL_PLAYING) {
            int queued = alGetSourcei(stream.source, AL_BUFFERS_QUEUED);
            // checkAl("alGetSourcei(AL_BUFFERS_QUEUED)");
            if (queued > 0) {
                alSourcePlay(stream.source);
                // checkAl("alSourcePlay");
            }
        }
    }

    private void alThreadLoop() {
        open();
        try {
            while (running) {
                // 1) run at least one command (blocks until something arrives)
                // Runnable r = commandQueue.take();
                Runnable r = commandQueue.poll(5, TimeUnit.MILLISECONDS);
                if (r != null) {
                    r.run();
                    // 2) drain remaining commands quickly
                    while ((r = commandQueue.poll()) != null) r.run();
                }


                pumpProcessed();
            }
        }
        catch (InterruptedException ignored) {}
        finally {}
    }

    @Override
    public void close() {
        // TODO
    }

    private static void checkAl(String where) {
        int err = alGetError();
        if (err != AL_NO_ERROR) {
            throw new IllegalStateException(where + " failed: OpenAL error 0x" + Integer.toHexString(err));
        }
    }
}