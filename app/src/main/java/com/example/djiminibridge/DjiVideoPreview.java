package com.example.djiminibridge;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.TextureView;

import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

class DjiVideoPreview implements TextureView.SurfaceTextureListener {

    private final Context context;
    private final TextureView textureView;
    private DJICodecManager codecManager;
    private VideoFeeder.VideoDataListener videoDataListener;
    private boolean started;

    DjiVideoPreview(Context context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;
        this.textureView.setSurfaceTextureListener(this);
    }

    boolean start() {
        if (started) {
            return true;
        }

        if (codecManager == null && textureView.isAvailable()) {
            codecManager = new DJICodecManager(
                    context,
                    textureView.getSurfaceTexture(),
                    textureView.getWidth(),
                    textureView.getHeight()
            );
        }

        if (codecManager == null) {
            return false;
        }

        videoDataListener = (videoBuffer, size) -> codecManager.sendDataToDecoder(videoBuffer, size);
        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
        started = true;
        return true;
    }

    void stop() {
        if (videoDataListener != null) {
            VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(videoDataListener);
            videoDataListener = null;
        }
        started = false;
    }

    void close() {
        stop();
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (codecManager == null) {
            codecManager = new DJICodecManager(context, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        close();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}
