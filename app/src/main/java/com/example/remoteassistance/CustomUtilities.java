package com.example.remoteassistance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public final class CustomUtilities {

    //********** GENERAL APP SETTINGS **********//
    public static final int PERMISSION_REQ_ID = 22;
    public static final String[] REQUESTED_PERMISSIONS =
            {
                    android.Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            };

    // Fill the App ID of your project generated on Agora Console.
    public static final String appId = "";
    // Fill the channel name.
    public static final String channelName = "";
    // Fill the temp token generated on Agora Console.
    public static final String token = "";
    // An integer that identifies the local user.
    public static final int uid = 0;

    //********** CONSTANTS **********//
    public static final String CHANNEL_NAME = "channel_name";
    public static final String TOKEN = "token";


    //********** UTILITY METHODS **********//
    public static boolean checkSelfPermission(AppCompatActivity appCompatActivity)
    {
        return ContextCompat.checkSelfPermission(appCompatActivity, REQUESTED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appCompatActivity, REQUESTED_PERMISSIONS[1]) == PackageManager.PERMISSION_GRANTED;
    }

    public static void showMessage(AppCompatActivity appCompatActivity, String message) {
        appCompatActivity.runOnUiThread(() ->
                Toast.makeText(appCompatActivity.getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
}
