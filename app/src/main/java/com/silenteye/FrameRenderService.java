package com.silenteye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

public class FrameRenderService extends Service {

    private static final String TAG = "FrameRenderService";
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "ScreenRecordChannel";

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private String videoPath;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START_RECORDING".equals(action)) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            if (resultCode != -1 && data != null) {
                // MediaProjection requires a foreground service on recent Android versions
                Notification notification;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notification = new Notification.Builder(this, CHANNEL_ID)
                            .setContentTitle("Screen Recording")
                            .setContentText("Recording in background...")
                            .setSmallIcon(android.R.drawable.presence_video_online)
                            .build();
                } else {
                    notification = new Notification.Builder(this)
                            .setContentTitle("Screen Recording")
                            .setContentText("Recording in background...")
                            .setSmallIcon(android.R.drawable.presence_video_online)
                            .build();
                }
                startForeground(NOTIFICATION_ID, notification);

                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                if (mediaProjection != null) {
                    initMediaRecorder();
                    startRecording();
                }
            } else {
                Log.e(TAG, "Invalid result code or intent data for MediaProjection");
                stopSelf();
            }
        } else if ("STOP_RECORDING".equals(action)) {
            stopRecording();
            stopForeground(true);
            stopSelf();
        }

        return START_STICKY;
    }

    private void initMediaRecorder() {
        mediaRecorder = new MediaRecorder();
        
        // Save to internal app files directory (Movies)
        File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        videoPath = dir.getAbsolutePath() + "/ScreenRecord_" + System.currentTimeMillis() + ".mp4";

        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoPath);
        mediaRecorder.setVideoSize(screenWidth, screenHeight);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(1024 * 1000);
        mediaRecorder.setVideoFrameRate(30);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Error preparing MediaRecorder", e);
        }
    }

    private void startRecording() {
        if (mediaProjection == null || mediaRecorder == null) return;

        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecorder",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);

        mediaRecorder.start();
        Log.d(TAG, "Started screen recording: " + videoPath);
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder", e);
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        Log.d(TAG, "Stopped screen recording. File saved at: " + videoPath);
        // Here you would upload videoPath to Firebase Storage
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Record Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
