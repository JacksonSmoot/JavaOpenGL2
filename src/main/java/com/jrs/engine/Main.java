package com.jrs.engine;

import com.jrs.gl.components.CrashHandler;
import com.jrs.gl.components.Window;
import com.jrs.gl.components.WindowBuilder;
import com.jrs.gl.g2d.GL2D;

import com.jrs.gl.input.keyboard.KeyboardInputEvent;
import com.jrs.gl.input.keyboard.KeyboardInputListener;
import com.jrs.gl.input.mouse.MouseInputEvent;
import com.jrs.gl.input.mouse.MouseInputListener;
import com.jrs.gl.managers.DiskShaderManager;
import com.jrs.gl.resources.texture.GLBufferedTexture;
import com.jrs.media.GifSpritePlayer;
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

    private static void windowTester(String[] args) throws Exception {
        Path videoPath = null;
        GifSpritePlayer streamingGifPlayer = new GifSpritePlayer(videoPath);
        streamingGifPlayer.start();


        WindowBuilder windowBuilder =
                WindowBuilder.defaultMacOS()
                        .title("MusicPlayer").size(1280, 720)
                        .decorated(false)
                        .mousePassthrough(true)
                        .floating(true)
                        .transparent(true);

        Window window = new Window(windowBuilder);

        window.setVisible(true);

        window.makeContextCurrent();

        GL2D gl2d = window.getGL2D();

        while(!window.shouldClose()) {
            gl2d.clear();
            gl2d.mount();
            streamingGifPlayer.updateAndDraw(gl2d, 0, 0);
            gl2d.unmount();
            gl2d.flip();
            window.pollEvents();
        }
    }

    public static void main(String[] args) throws Exception {

        System.setProperty("java.awt.headless", "true");
        windowTester(args);


           //  Path path = Path.of("/Users/jacksonsmoot/com/jrs/dev/JavaOpenGL2/src/main/resources/demos/311 Punkrocker feat Iggy Pop - Teddybears.m4a");
            // DiskShaderManager
//
//            MusicPlayerGraphics graphics = new MusicPlayerGraphics(path);
//            try {
//                graphics.run();
//            } catch (InterruptedException ignored) {}
//            graphics.close();

//        Path path = null;
//        if(args.length < 2) {
//            CrashHandler.propException(new IllegalArgumentException("Missing arguments: -path <path>"));
//            System.exit(-1);
//        }
//        if(args.length > 2) {CrashHandler.propException(new IllegalArgumentException("Too many arguments: -path <path>")); System.exit(-1);}
//        if(args[0].equals("-path")) {
//            path = Path.of(args[1]);
//        }


    }

}