package com.jrs.engine;

import com.jrs.gl.components.Window;
import com.jrs.gl.components.WindowBuilder;
import com.jrs.gl.g2d.GL2D;
import com.jrs.gl.input.keyboard.KeyboardInputEvent;
import com.jrs.gl.input.keyboard.KeyboardInputListener;
import com.jrs.gl.input.mouse.MouseInputEvent;
import com.jrs.gl.input.mouse.MouseInputListener;
import com.jrs.gl.resources.texture.GLBufferedTexture;
import com.jrs.gl.resources.texture.GLImage;
import com.jrs.media.AudioPlayer;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.nio.file.Path;

public class MusicPlayerGraphics implements KeyboardInputListener, MouseInputListener, AutoCloseable {
    private Window window;

    private AudioPlayer player;

    private Rectangle sliderBounds;

    private Rectangle playBounds;

    private GLImage albumArt;

    private int texW, texH, maxImageWidth;

    private boolean draggingSlider = false;

    private boolean playerPaused = false;

    private Path path;

    public MusicPlayerGraphics(Path path) {
        this.path = path;
        WindowBuilder windowBuilder =
                WindowBuilder.defaultMacOS()
                        .title("MusicPlayer").size(1280, 720);

        window = new Window(windowBuilder);

        window.setVisible(true);

        window.makeContextCurrent();

        window.getGL2D().setClearColor(0, 0, 0, 0);

        player = new AudioPlayer(path);

        player.setLooping(true);

        player.start();

        window.addMouseListener(this);
        window.addKeyboardListener(this);

        sliderBounds = new Rectangle(0, 500, 800, 50);
        sliderBounds.x = (window.getWindowSize().width / 2) - (sliderBounds.width / 2);

        maxImageWidth = sliderBounds.width / 3;

        albumArt = player.getAlbumArt();

        if(albumArt!=null){
            texW = Math.min(albumArt.getWidth(), maxImageWidth);
            texH = albumArt.getHeight();
            if(texW != albumArt.getWidth()){
                texH = (int)((double)albumArt.getHeight() * ((double)texW / (double)albumArt.getWidth()));
            }
        }

        int bsize = 50;
        playBounds = new Rectangle(0,0,bsize,bsize);
        playBounds.x = (window.getWindowSize().width / 2) - (playBounds.width / 2);
        playBounds.y = sliderBounds.y + (playBounds.width * 2);
    }

    private boolean isMouseInRectBounds(Rectangle rect, int mx, int my){
        return mx >= rect.x && mx <= rect.x + rect.width &&
                my >= rect.y && my <= rect.y + rect.height;
    }

    private boolean isMouseInSliderBounds(int mx, int my){
        return mx >= sliderBounds.x && mx <= sliderBounds.x + sliderBounds.width &&
                my >= sliderBounds.y && my <= sliderBounds.y + sliderBounds.height;
    }

    public void run() throws InterruptedException {
        player.play();
        while (!window.shouldClose()) {
            GL2D g2d = window.getGL2D();
            g2d.mount();
            g2d.clear();

            if(albumArt!=null){
                int outline_size = 5;
                g2d.fillRoundRect(sliderBounds.x - outline_size, sliderBounds.y - texH - sliderBounds.height - outline_size, texW + (outline_size*2), texH + (outline_size*2), 10, 255, 255, 255, 255);
                g2d.drawImage(albumArt, sliderBounds.x, sliderBounds.y - texH - sliderBounds.height, texW, texH, GL2D.IMAGE_VFLIP);
            }

            g2d.fillRect(sliderBounds.x, sliderBounds.y, sliderBounds.width, sliderBounds.height, 128, 128, 128, 255);
            {
                if(draggingSlider){
                    int rmx = Math.clamp((long)window.getMx(), sliderBounds.x, sliderBounds.x + sliderBounds.width);
                    int dx = (int)rmx - sliderBounds.x;
                    g2d.fillRect(sliderBounds.x, sliderBounds.y, dx, sliderBounds.height, 255, 0, 0, 255);
                }
                else{
                    double time_prop = (double)sliderBounds.width / player.getDurationNs();
                    int rwidth = (int)(time_prop * player.getElapsedNs());
                    g2d.fillRect(sliderBounds.x, sliderBounds.y, rwidth, sliderBounds.height, 255, 0, 0, 255);
                }
            }

            if(playerPaused){
                g2d.fillTriangle(playBounds.x, playBounds.y, playBounds.x, playBounds.y + playBounds.width, playBounds.x + playBounds.width, playBounds.y + (playBounds.width / 2), 255, 255, 255, 255);
            }
            else{
                g2d.fillRect(playBounds.x, playBounds.y, playBounds.width / 3, playBounds.width, 255, 255, 255, 255);
                g2d.fillRect(playBounds.x + ((playBounds.width/3)*2), playBounds.y, playBounds.width / 3, playBounds.width, 255, 255, 255, 255);
            }



            // g2d.drawImage(titleTex, 0, 0, GL2D.IMAGE_VFLIP);
            window.pollEvents();
            g2d.unmount();
            g2d.flip();
        }
    }

    private void playerSwitchSate(){
        if(playerPaused){resumePlayer();}
        else if(!playerPaused){pausePlayer();}
    }

    private void pausePlayer(){
        if(!playerPaused) player.pause();
        playerPaused = true;
    }

    private void resumePlayer(){
        if(playerPaused) player.play();
        playerPaused = false;
    }

    private long mousePosToNs(int mx){
        int mxn = Math.clamp(mx, sliderBounds.x, sliderBounds.x + sliderBounds.width);
        mxn = mxn - sliderBounds.x;
        long time_prop = player.getDurationNs() / sliderBounds.width;
        long seekTime = time_prop * mxn;
        return seekTime;
    }

    @Override
    public void keyPressed(KeyboardInputEvent e) {
        if(e.key() == GLFW.GLFW_KEY_SPACE){
            playerSwitchSate();
        }
    }

    @Override
    public void keyReleased(KeyboardInputEvent e) {}

    @Override
    public void keyHeld(KeyboardInputEvent e) {}

    @Override
    public void mouseButtonPressed(MouseInputEvent e) {
        if(isMouseInSliderBounds((int)e.x(), (int)e.y())){
            draggingSlider = true;
            if(!playerPaused) pausePlayer();

        }
        else if(isMouseInRectBounds(playBounds, (int)e.x(), (int)e.y())){
            playerSwitchSate();
        }
    }

    @Override
    public void mouseButtonReleased(MouseInputEvent e) {
        if(playerPaused && draggingSlider){
            draggingSlider = false;
            player.seekToNs(mousePosToNs((int)e.x()));
            resumePlayer();
        }
    }

    @Override
    public void mouseMoved(MouseInputEvent e) {

    }

    @Override
    public void mouseDragged(MouseInputEvent e) {

    }

    @Override
    public void close(){
        try{
            player.close();
        } catch (InterruptedException ignored) {}
        if(albumArt != null) albumArt.close();
        window.close();
    }
}