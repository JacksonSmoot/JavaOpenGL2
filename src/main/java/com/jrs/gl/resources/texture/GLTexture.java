package com.jrs.gl.resources.texture;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

public class GLTexture implements AutoCloseable {

    protected int textureID;

    public GLTexture(int textureID) {
        this.textureID = textureID;
    }

    public int getId(){
        return textureID;
    }

    public void free(){
        if(textureID != 0) glDeleteTextures(textureID);
        textureID = 0;
    }

    @Override
    public void close() {
        free();
    }
}
