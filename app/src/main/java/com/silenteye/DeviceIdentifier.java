package com.silenteye;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Random;

public class DeviceIdentifier {
    private static final String PREF_NAME = "EchoRunnerPrefs";
    private static final String KEY_DEVICE_ID = "device_key";

    public static String getDeviceKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String deviceKey = prefs.getString(KEY_DEVICE_ID, null);

        if (deviceKey == null) {
            // Remove any characters that Firebase dislikes
            String model = Build.MODEL != null ? Build.MODEL.replaceAll("[^a-zA-Z0-9]", "") : "UnknownDevice";
            int randomNum = new Random().nextInt(999999);
            deviceKey = model + "_" + String.format("%06d", randomNum);
            prefs.edit().putString(KEY_DEVICE_ID, deviceKey).apply();
        }

        return deviceKey;
    }
}
