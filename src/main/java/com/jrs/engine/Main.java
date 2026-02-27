package com.jrs.engine;

import com.jrs.gl.components.Window;
import com.jrs.gl.components.WindowBuilder;
import com.jrs.gl.g2d.GL2D;

import com.jrs.gl.input.keyboard.KeyboardInputEvent;
import com.jrs.gl.input.keyboard.KeyboardInputListener;
import com.jrs.gl.input.mouse.MouseInputEvent;
import com.jrs.gl.input.mouse.MouseInputListener;
import com.jrs.gl.managers.DiskShaderManager;
import com.jrs.gl.resources.texture.GLBufferedTexture;
import com.jrs.media.MusicPlayer;
import com.jrs.media.MusicPlayerV2;
import com.jrs.media.MusicPlayerV3;
import com.jrs.media.OpenALManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.system.MemoryUtil.memFree;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void fileWalkmain(String[] args) throws IOException {
        Path root = Path.of("/Volumes/General"); // change to your drive

        long start = System.nanoTime();

        Counter counter = new Counter();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                counter.count++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Ignore inaccessible files
                return FileVisitResult.CONTINUE;
            }
        });

        long end = System.nanoTime();
        double seconds = (end - start) / 1_000_000_000.0;

        System.out.println("Files found: " + counter.count);
        System.out.println("Time: " + seconds + " seconds");
        System.out.println("Files/sec: " + (counter.count / seconds));
    }

    static class Counter {
        long count = 0;
    }

    public static void main(String[] args) throws Exception {

        System.setProperty("java.awt.headless", "true");

        Path path = null;
        if(args.length < 2) {throw new IllegalArgumentException("Missing arguments: -path <path>");}
        if(args.length > 2) {throw new IllegalArgumentException("Too many arguments: -path <path>");}
        if(args[0].equals("-path")) {
            path = Path.of(args[1]);
        }

        // Path path = Path.of("/Users/jacksonsmoot/com/jrs/dev/JavaOpenGL2/src/main/resources/demos/311 Punkrocker feat Iggy Pop - Teddybears.m4a");
        // DiskShaderManager

        MusicPlayerGraphics graphics = new MusicPlayerGraphics(path);
        graphics.run();
    }

}