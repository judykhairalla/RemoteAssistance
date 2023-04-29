package com.example.remoteassistance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class CustomUtilities {

    //********** GENERAL APP SETTINGS **********//
    public static final int PERMISSION_REQ_ID = 22;
    public static final String[] REQUESTED_PERMISSIONS =
            {
                    android.Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            };
    public static final int PERMISSION_REQUEST_CODE = 0X0001;

    // Fill the App ID of your project generated on Agora Console.
    public static final String appId = "";
    // Fill the channel name.
    public static final String channelName = "";
    // Fill the temp token generated on Agora Console.
    public static final String token = "";
    // An integer that identifies the local user.
    public static final int uid = 0;

    //********** UTILITY METHODS **********//
    public static boolean checkSelfPermission(AppCompatActivity appCompatActivity)
    {
        List<String> needList = new ArrayList<>();
        for (String perm : REQUESTED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(appCompatActivity, perm) != PackageManager.PERMISSION_GRANTED) {
                needList.add(perm);
            }
        }

        if (!needList.isEmpty()) {
            ActivityCompat.requestPermissions(appCompatActivity, needList.toArray(new String[needList.size()]), PERMISSION_REQUEST_CODE);
            return false;
        }

        return true;
    }

    public static void showMessage(AppCompatActivity appCompatActivity, String message) {
        appCompatActivity.runOnUiThread(() ->
                Toast.makeText(appCompatActivity.getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
}
