package com.jrs.gl.managers;

import com.jrs.gl.resources.ShaderProgram;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiskShaderManager implements AutoCloseable {
    private Map<String, ShaderProgram> shaderPrograms = new HashMap<>();
    private Path rootDir;

    public static void ensureDefaultShadersInstalled(Path shaderDir) throws IOException {
        Files.createDirectories(shaderDir);

        ClassLoader cl = DiskShaderManager.class.getClassLoader();

        List<String> shaderNames;
        try (var is = cl.getResourceAsStream("shaders/index.txt")) {
            if (is == null) throw new IOException("Missing resource shaders/index.txt");
            shaderNames = new java.io.BufferedReader(new java.io.InputStreamReader(is))
                    .lines().filter(s -> !s.isBlank()).toList();
        }

        for (String name : shaderNames) {
            // example: shaders/basic/basic.vertex and shaders/basic/basic.fragment
            copyIfMissing(cl, "shaders/" + name + "/" + name + ".vertex",
                    shaderDir.resolve(name).resolve(name + ".vertex"));
            copyIfMissing(cl, "shaders/" + name + "/" + name + ".fragment",
                    shaderDir.resolve(name).resolve(name + ".fragment"));
        }
    }

    private static void copyIfMissing(ClassLoader cl, String resourcePath, Path outPath) throws IOException {
        if (Files.exists(outPath)) return; // don't overwrite user edits
        try (var is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Missing resource: " + resourcePath);
            Files.createDirectories(outPath.getParent());
            Files.copy(is, outPath);
        }
    }

    public DiskShaderManager(Path root) throws IOException {
        this.rootDir = root;
        ensureDefaultShadersInstalled(root);
        File[] files = new File(root.toString()).listFiles();
        for(File file : files) {
            if(file.isDirectory()) {
                File[] subFiles = new File(file.toString()).listFiles();
                String vertex = "";
                String fragment = "";
                for(File subFile : subFiles) {
                    if(subFile.toString().endsWith(".vertex")){
                        vertex = Files.readString(Path.of(subFile.toString()));
                    }
                    else if(subFile.toString().endsWith(".fragment")){
                        fragment = Files.readString(Path.of(subFile.toString()));
                    }
                }
                String fileName = file.getName();
                fileName = fileName.substring(fileName.lastIndexOf('/')+1);
                ShaderProgram shaderProgram = new ShaderProgram(vertex, fragment);
                shaderPrograms.put(fileName, shaderProgram);
            }
            else{
                throw new IOException("Unexpected file in shaders directory: \""+file.toString()+"\", expected only sub directories.");
            }
        }
    }

    public ShaderProgram getShaderProgram(String fileName) {
        return shaderPrograms.get(fileName);
    }

    @Override
    public void close(){
        for(ShaderProgram shaderProgram : shaderPrograms.values()){
            shaderProgram.close();
        }
    }
}
