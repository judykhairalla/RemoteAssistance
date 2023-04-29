package com.example.remoteassistance;

import static com.example.remoteassistance.CustomUtilities.channelName;
import static com.example.remoteassistance.CustomUtilities.token;
import static com.example.remoteassistance.CustomUtilities.uid;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;


public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = AssistantActivity.class.getSimpleName();

    //********** UI VARIABLES **********//
    private SurfaceView remoteSurfaceView;
    private SurfaceView localSurfaceView;
    private ImageButton mMuteBtn;

    //********** AGORA VARIABLES **********//
    private RtcEngine agoraEngine;
    private AppCompatActivity context;
    private boolean isJoined = false;
    private boolean isMuted = false;

    //********** AR VARIABLES **********//
    private int touchCount = 0;
    private float mScaleFactor = 0.03f;
    private int mObjectChoice = 0;
    int dataChannel;
    int mWidth, mHeight;
    private List<Float> floatList = new ArrayList<>();

    //********** ACTIVITY METHODS **********//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);
        context = this;
        initUI();
        setupVideoSDKEngine();
        joinChannel();
    }

    protected void onDestroy() {
        super.onDestroy();
        agoraEngine.stopPreview();
        agoraEngine.leaveChannel();

        // Destroy the engine in a sub-thread to avoid congestion
        new Thread(() -> {
            RtcEngine.destroy();
            agoraEngine = null;
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CustomUtilities.PERMISSION_REQUEST_CODE) {
            int deniedCount = 0;

            for (int i = 0; i < results.length; i++) {
                if (results[i] == PackageManager.PERMISSION_DENIED) {
                    deniedCount++;
                }
            }

            if (deniedCount == 0) {
                setupVideoSDKEngine();
            } else {
                finish();
            }
        }
    }

    //********** AGORA CHANNELS INITIALIZATION **********//
    private void setupVideoSDKEngine() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = CustomUtilities.appId;
            config.mEventHandler = mRtcEventHandler;
            agoraEngine = RtcEngine.create(config);
            // By default, the video module is disabled, call enableVideo to enable it.
            agoraEngine.enableVideo();
            agoraEngine.switchCamera();
        } catch (Exception e) {
            CustomUtilities.showMessage(context, e.toString());
        }
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        // Listen for the remote host joining the channel to get the uid of the host.
        public void onUserJoined(int uid, int elapsed) {
            CustomUtilities.showMessage(context,"Remote user joined " + uid);

            // Set the remote video view
            runOnUiThread(() -> setupRemoteVideo(uid));
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            CustomUtilities.showMessage(context, "Joined Channel " + channel);
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            CustomUtilities.showMessage(context,"Remote user offline " + uid + " " + reason);
            runOnUiThread(() -> remoteSurfaceView.setVisibility(View.GONE));
        }
    };

    private void setupRemoteVideo(int uid) {
        FrameLayout container = findViewById(R.id.remote_video_view_container);
        remoteSurfaceView = new SurfaceView(getBaseContext());
        container.addView(remoteSurfaceView);
        agoraEngine.setupRemoteVideo(new VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid));
        // Display RemoteSurfaceView.
        remoteSurfaceView.setVisibility(View.VISIBLE);
        initTouchListener();
    }

    private void setupLocalVideo() {
        FrameLayout container = findViewById(R.id.local_video_view_container);
        // Create a SurfaceView object and add it as a child to the FrameLayout.
        localSurfaceView = new SurfaceView(getBaseContext());
        localSurfaceView.setZOrderMediaOverlay(true);
        container.addView(localSurfaceView);
        // Call setupLocalVideo with a VideoCanvas having uid set to 0.
        agoraEngine.setupLocalVideo(new VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
    }

    public void joinChannel() {
        if (CustomUtilities.checkSelfPermission(this)) {
            ChannelMediaOptions options = new ChannelMediaOptions();

            // For a Video call, set the channel profile as COMMUNICATION.
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            // Set the client role as BROADCASTER or AUDIENCE according to the scenario.
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            // Display LocalSurfaceView.
            setupLocalVideo();
            localSurfaceView.setVisibility(View.VISIBLE);
            // Start local preview.
            agoraEngine.startPreview();
            // Join the channel with a temp token.
            // You need to specify the user ID yourself, and ensure that it is unique in the channel.
            agoraEngine.joinChannel(token, channelName, uid, options);
            dataChannel = agoraEngine.createDataStream(false, false);
            isJoined = true;
        } else {
            Toast.makeText(getApplicationContext(), "Permissions was not granted", Toast.LENGTH_SHORT).show();
        }
    }

    public void leaveChannel(View view) {
        if (!isJoined) {
            CustomUtilities.showMessage(this,"Join a channel first");
        } else {
            agoraEngine.leaveChannel();
            CustomUtilities.showMessage(this,"You left the channel");
            // Stop remote video rendering.
            if (remoteSurfaceView != null) remoteSurfaceView.setVisibility(View.GONE);
            // Stop local video rendering.
            if (localSurfaceView != null) localSurfaceView.setVisibility(View.GONE);
            isJoined = false;
        }
    }

    private void sendMessage(int touchCount, List<Float> floatList) {
        byte[] motionByteArray = new byte[floatList.size() * 4 ];
        for (int i = 0; i < floatList.size(); i++) {
            byte[] curr = ByteBuffer.allocate(4).putFloat(floatList.get(i)).array();
            for (int j = 0; j < 4; j++) {
                motionByteArray[i * 4 + j] = curr[j];
            }
        }
        agoraEngine.sendStreamMessage(dataChannel, motionByteArray);
    }

    //********** CONTROL PANEL METHODS **********//
    public void initUI(){
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        ActionBar ab = getSupportActionBar();
        setContentView(R.layout.activity_assistant);
        if (ab != null) {
            ab.hide();
        }

        mMuteBtn = findViewById(R.id.btn_mute);
        //get device screen size
        mWidth= this.getResources().getDisplayMetrics().widthPixels;
        mHeight= this.getResources().getDisplayMetrics().heightPixels;
    }

    public void initTouchListener(){
        remoteSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_MOVE:
                    case MotionEvent.ACTION_DOWN:
                        //get the touch position related to the center of the screen
                        touchCount++;
                        float x = event.getRawX() - ((float)mWidth / 2);
                        float y = event.getRawY() - ((float)mHeight / 2);
                        floatList.add(x);
                        floatList.add(y);
                        floatList.add(mObjectChoice + mScaleFactor);
                        sendMessage(touchCount, floatList);
                        touchCount = 0;
                        floatList.clear();
                        break;
                    default:
                        break;
                }
                return true;
            }

        });
    }

    public void onSwitchCameraClicked(View view) {
        agoraEngine.switchCamera();
    }

    // TODO: fix mute button
    public void onLocalAudioMuteClicked(View view) {
        isMuted = !isMuted;
        agoraEngine.muteLocalAudioStream(isMuted);
        mMuteBtn.setImageResource(isMuted ? R.drawable.ic_baseline_mic_off_24 : R.drawable.ic_baseline_mic_24);
    }
}
