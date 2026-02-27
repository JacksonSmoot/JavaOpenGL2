package com.jrs.gl.resources.texture;

import java.awt.Dimension;

public class GLImage extends GLTexture{
    protected int width, height;

    public GLImage(int textureId, int width, int height) {
        super(textureId);
        this.width = width;
        this.height = height;
    }

    public Dimension getSize(){
        return new Dimension(width, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
