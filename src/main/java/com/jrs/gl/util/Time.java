package com.jrs.gl.util;

public final class Time {
    private long epoch = System.nanoTime();

    public Time(){}

    public Time(long epoch){
        this.epoch = epoch;
    }

    public long getEpoch(){
        return epoch;
    }

    public void setEpoch(long epoch){
        this.epoch = epoch;
    }

    public long nowNs(){
        return System.nanoTime() - epoch;
    }

    public static long staticNowNs(){
        return System.nanoTime();
    }
}
