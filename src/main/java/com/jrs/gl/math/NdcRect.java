package com.jrs.gl.math;

public class NdcRect {
    public float x;
    public float y;
    public float width;
    public float height;

    public NdcRect(float x, float y, float width, float height){
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getX(){
        return x;
    }

    public float getY(){
        return y;
    }

    public float getWidth(){
        return width;
    }

    public float getHeight(){
        return height;
    }
}
