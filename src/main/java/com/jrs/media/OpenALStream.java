package com.jrs.media;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL10.alSourceQueueBuffers;
import static org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET;

public final class OpenALStream implements AutoCloseable {

    final int source;

    // slot -> AL buffer id
    final int[] buffers;

    // slot -> samples in that buffer (for playedSamples accounting)
    final int[] samplesInSlot;

    // free slot indices (0..n-1)
    final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();

    // engine-thread only
    final ArrayDeque<PcmChunk> pending = new ArrayDeque<>();

    long playedSamples = 0;

    int sampleRate = -1;
    int channels = -1;

    boolean shouldPlay = false;

    public OpenALStream(int source, int[] buffers) {
        this.source = source;
        this.buffers = buffers;
        this.samplesInSlot = new int[buffers.length];
        for (int i = 0; i < buffers.length; i++) freeSlots.add(i);
    }

    @Override
    public void close() {

    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public long getPlayedTimeNs(){
        AtomicLong i = new AtomicLong(-1);
        OpenALManager.manager.post((Runnable)() -> {
                long base = playedSamples;

                int offset = 0;
                int state = alGetSourcei(source, AL_SOURCE_STATE);
                if (state == AL_PLAYING) {
                    offset = alGetSourcei(source, AL_SAMPLE_OFFSET);
                    if (offset < 0) offset = 0;
                }

                long totalSamples = base + (long) offset;
                i.set((totalSamples * 1_000_000_000L) / (long) sampleRate);
        });
        while(i.get() == -1){try{Thread.sleep(1);}catch(InterruptedException ignored){}}
        return i.get();
    }

    public int getNumFreeSlots() {
        return freeSlots.size();
    }

    public int getNumSlots(){
        return buffers.length;
    }

    /** Returns a free slot index, or -1 if none. */
    public int claimFreeSlot() {
        Integer slot = freeSlots.pollFirst();
        return slot == null ? -1 : slot;
    }

    /** Returns the AL buffer id for a slot. */
    public int bufferIdForSlot(int slot) {
        return buffers[slot];
    }

    /** Returns a chunk to play, or null if none. */
    public PcmChunk pollChunk() {
        return pending.pollFirst();
    }

    /** Called right after you queued a chunk into a slot. */
    public void markSlotQueued(int slot, int samples) {
        samplesInSlot[slot] = samples;
    }

    public void reclaimByBufferIdNoSamplesAdded(int bufferId){
        int slot = findSlot(bufferId);
        if (slot < 0) throw new IllegalStateException("buffer " + bufferId + " not found");
        samplesInSlot[slot] = 0;
        freeSlots.addLast(slot);
    }

    /** Called when OpenAL unqueues a processed buffer id. */
    public void reclaimByBufferId(int bufferId) {
        int slot = findSlot(bufferId);
        if (slot < 0) throw new IllegalStateException("buffer " + bufferId + " not found");
        playedSamples += samplesInSlot[slot];
        samplesInSlot[slot] = 0;
        freeSlots.addLast(slot);
    }

    private int findSlot(int bufferId) {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] == bufferId) return i;
        }
        return -1;
    }

    public static ByteBuffer copyToHeap(ByteBuffer src) {
        // Work on a duplicate so we don't change the original position/limit
        ByteBuffer dup = src.duplicate();

        // Copy only the remaining portion
        ByteBuffer copy = ByteBuffer.allocate(dup.remaining());

        copy.put(dup);
        copy.flip(); // prepare for reading

        return copy;
    }

    public boolean hasFreeSlots() {
        return !freeSlots.isEmpty();
    }

    public boolean hasChunk(){
        return !pending.isEmpty();
    }

    public boolean hasFloat32(){
        return OpenALManager.manager.hasFloat32();
    }

    public boolean isPlaying(){
        AtomicBoolean returnNow = new AtomicBoolean(false);
        AtomicBoolean isPlaying = new AtomicBoolean(false);
        OpenALManager.manager.post((Runnable)() -> {
            int state = alGetSourcei(source, AL_SOURCE_STATE);
            isPlaying.set(state == AL_PLAYING);
            returnNow.set(true);
        });
        while(!returnNow.get()) {}
        return isPlaying.get();
    }

    public void restart(){
        AtomicBoolean returnNow = new AtomicBoolean(false);
        OpenALManager.manager.post((Runnable)() -> {
            OpenALManager.manager.rewindAndFlush(this);
            returnNow.set(true);
        });
        while(!returnNow.get()) {}
    }

    public void stopAndClear(){
        shouldPlay = false;
        AtomicBoolean returnNow = new AtomicBoolean(false);
        pending.clear();
        OpenALManager.manager.post((Runnable)() -> {
            OpenALManager.manager.stop(this);
            returnNow.set(true);
        });

        while(!returnNow.get()) {}
    }

    public void stop(){
        shouldPlay = false;
        AtomicBoolean returnNow = new AtomicBoolean(false);
        OpenALManager.manager.post((Runnable)() -> {
            OpenALManager.manager.stop(this);
            returnNow.set(true);
        });
        while(!returnNow.get()) {}
    }

    /** Engine-thread only: enqueue a PCM chunk to be played later. */
    public void enqueueChunk(ByteBuffer pcm, int channels, boolean float32, int samples) {
        shouldPlay = true;
        pending.addLast(new PcmChunk(pcm, channels, samples, float32));
    }
}