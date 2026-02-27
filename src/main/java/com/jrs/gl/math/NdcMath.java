package com.jrs.gl.math;


import java.awt.*;

public class NdcMath {

    public static float ndcX(int x, int windowWidth){
        return ((float)x / windowWidth) * 2f - 1f;
    }

    public static float ndcY(int y, int windowHeight){
        return 1f - ((float)y / windowHeight) * 2f;
    }

    public static float ndcWidth(int width, int windowWidth){
        return ((float)width / windowWidth) * 2f;
    }

    public static float ndcHeight(int height, int windowHeight){
        return ((float)height / windowHeight) * 2f;
    }

    public static NdcRect ndcRect(Rectangle windowBounds, int x, int y, int width, int height){
        return new NdcRect(ndcX(x, windowBounds.width), ndcY(y, windowBounds.height), ndcWidth(width, windowBounds.width), ndcHeight(height, windowBounds.height));
    }
}
