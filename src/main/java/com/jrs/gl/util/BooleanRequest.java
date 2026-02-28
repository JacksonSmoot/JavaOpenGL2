package com.jrs.gl.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class BooleanRequest {
    private final AtomicBoolean request = new AtomicBoolean(false);
    private final AtomicBoolean response = new AtomicBoolean(false);
    public BooleanRequest() {}

    public void request() {
        this.request.set(true);
    }
    public void respond() {
        this.response.set(true);
    }

    public boolean isRequested() {
        return request.get();
    }

    public boolean isResponse() {
        return response.get();
    }

    public void requestAndWait(){
        this.request.set(true);
        while(!response.get()) {try{Thread.sleep(1);}catch(InterruptedException e){}}
    }

    public void requestWaitAndResetRequest(){
        this.request.set(true);
        while(!request.get()) {try{Thread.sleep(1);}catch(InterruptedException e){}}
        request.set(false);
    }

    public void respondAndHang(){
        if(request.get()){
            response.set(true);
            while(response.get()){try{Thread.sleep(1);}catch(InterruptedException e){}}
        }
    }

    public void reset(){
        this.request.set(false);
        this.response.set(false);
    }
}
