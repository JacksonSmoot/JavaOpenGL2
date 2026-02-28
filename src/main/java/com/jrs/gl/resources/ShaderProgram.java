package com.jrs.gl.resources;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram implements AutoCloseable{
    private final int programId;

    private final Map<String, Integer> uniformCache = new HashMap<>();

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        int vs = compileShader(GL_VERTEX_SHADER, vertexSrc);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragmentSrc);

        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteProgram(programId);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new RuntimeException("Program link failed:\n" + log);
        }

        // shaders can be deleted after linking (program keeps the compiled result)
        glDetachShader(programId, vs);
        glDetachShader(programId, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compileShader(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);

        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);

            String kind = (type == GL_VERTEX_SHADER) ? "VERTEX" :
                    (type == GL_FRAGMENT_SHADER) ? "FRAGMENT" : "UNKNOWN";

            throw new RuntimeException(kind + " shader compile failed:\n" + log);
        }
        return id;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public static void unbind() {
        glUseProgram(0);
    }

    public int id() {
        return programId;
    }

    public int uniformLocation(String name) {
        Integer cached = uniformCache.get(name);
        if (cached != null) return cached;

        int loc = glGetUniformLocation(programId, name);
        // loc can be -1 if the uniform is unused/optimized out or name is wrong
        uniformCache.put(name, loc);
        return loc;
    }

    public void setUniform1f(String name, float f1) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform1f(loc, f1);
    }

    public void setUniform2f(String name, float f1, float f2) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform2f(loc, f1, f2);
    }

    public void setUniform3f(String name, float f1, float f2, float f3) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform3f(loc, f1, f2, f3);
    }

    public void setUniform4f(String name, float f1, float f2, float f3, float f4) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform4f(loc, f1, f2, f3, f4);
    }

    public void setUniform1i(String name, int v) {
        int loc = uniformLocation(name);
        if (loc != -1) glUniform1i(loc, v);
    }


    @Override
    public void close() {
        glDeleteProgram(programId);
        uniformCache.clear();
    }
}