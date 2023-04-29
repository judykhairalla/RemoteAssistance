package com.example.remoteassistance;

import static com.example.remoteassistance.CustomUtilities.channelName;
import static com.example.remoteassistance.CustomUtilities.token;
import static com.example.remoteassistance.CustomUtilities.uid;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.remoteassistance.helpers.CameraPermissionHelper;
import com.example.remoteassistance.helpers.DisplayRotationHelper;
import com.example.remoteassistance.helpers.VirtualObject;
import com.example.remoteassistance.rendering.BackgroundRenderer;
import com.example.remoteassistance.rendering.ObjectRenderer;
import com.example.remoteassistance.rendering.PeerRenderer;
import com.example.remoteassistance.rendering.PlaneRenderer;
import com.example.remoteassistance.rendering.PointCloudRenderer;
import com.example.remoteassistance.rendering.ShapeRenderer;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.AgoraVideoFrame;
import io.agora.rtc2.video.VideoCanvas;

public class ClientActivity extends AppCompatActivity implements GLSurfaceView.Renderer{
    private static final String TAG = ClientActivity.class.getSimpleName();

    //********** UI VARIABLES **********//
    private SurfaceView remoteSurfaceView;
    private ImageButton mMuteBtn;
    private Snackbar mMessageSnackbar;

    //********** AGORA VARIABLES **********//
    private RtcEngine agoraEngine;
    private AppCompatActivity context;
    private boolean isJoined = false;
    private boolean isMuted = false;

    //********** AR VARIABLES **********//
    private boolean installRequested;
    private GLSurfaceView mSurfaceView;
    private GestureDetector mGestureDetector;
    private Handler mSenderHandler;
    private Session mSession;
    private final PlaneRenderer mPlaneRenderer = new PlaneRenderer();
    private final PointCloudRenderer mPointCloud = new PointCloudRenderer();
    private final BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private PeerRenderer mPeerObject = new PeerRenderer();
    private DisplayRotationHelper mDisplayRotationHelper;

    private ObjectRenderer placeholderRenderer = new ObjectRenderer();
    private ObjectRenderer circleRenderer = new ObjectRenderer();
    private ObjectRenderer arrowRenderer = new ObjectRenderer();
    private ObjectRenderer arrowAnticlockwiseRenderer = new ObjectRenderer();
    private ObjectRenderer arrowClockwiseRenderer = new ObjectRenderer();

    private final ArrayBlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);
    private final ArrayBlockingQueue<ArrayList> queuedSentTaps = new ArrayBlockingQueue<>(16);
    private final ArrayList<VirtualObject> virtualObjects = new ArrayList<>();

    private float mScaleFactor = 0.05f;
    private boolean mHidePoint;
    private boolean mHidePlane;
    private int mWidth, mHeight;

    // Assumed distance from the device camera to the surface on which user will try to place objects.
    // This value affects the apparent scale of objects while the tracking method of the
    // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
    // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
    // values for AR experiences where users are expected to place objects on surfaces close to the
    // camera. Use larger values for experiences where the user will likely be standing and trying to
    // place an object on the ground or floor in front of them.
    private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

    //********** ACTIVITY METHODS **********//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    public void onPause() {
        super.onPause();
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        mDisplayRotationHelper.onPause();
        mSurfaceView.onPause();
        if (mSession != null) {
            mSession.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSession == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                mSession = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (Exception e) {
                message = "This device does not support AR";
                exception = e;
            }

            if (message != null) {
                CustomUtilities.showMessage(context, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            // Create default config and check if supported.
            Config config = new Config(mSession);
            if (!mSession.isSupported(config)) {
                CustomUtilities.showMessage(context, "This device does not support AR");
            }
            mSession.configure(config);
        }
        showLoadingMessage();
        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        mSurfaceView.onResume();
        mDisplayRotationHelper.onResume();
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
        installRequested = false;
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = CustomUtilities.appId;
            config.mEventHandler = mRtcEventHandler;
            agoraEngine = RtcEngine.create(config);
        } catch (Exception e) {
            CustomUtilities.showMessage(context, e.toString());
        }

        HandlerThread thread = new HandlerThread("ArSendThread");
        thread.start();
        mSenderHandler = new Handler(thread.getLooper());
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

        @Override
        public void onStreamMessage(int uid, int streamId, byte[] data) {
            //when received the remote user's stream message data
            super.onStreamMessage(uid, streamId, data);
            CustomUtilities.showMessage(context, "GOTYOURMESSAGE");
            int touchCount = data.length / 12;       //number of touch points from data array
            for (int k = 0; k < touchCount; k++) {
                //get the touch point's x,y position related to the center of the screen and calculated the raw position
                byte[] xByte = new byte[4];
                byte[] yByte = new byte[4];
                byte[] optionsByte = new byte[4];
                for (int i = 0; i < 4; i++) {
                    xByte[i] = data[i + 12 * k];
                    yByte[i] = data[i + 12 * k + 4];
                    optionsByte[i] = data[i + 12 * k + 8];
                }
                float convertedX = ByteBuffer.wrap(xByte).getFloat();
                float convertedY = ByteBuffer.wrap(yByte).getFloat();
                float options = ByteBuffer.wrap(optionsByte).getFloat();
                float center_X = convertedX + ((float) mWidth / 2);
                float center_Y = convertedY + ((float) mHeight / 2);

                int objectChoice = (int) options;
                switch (objectChoice){
                    case 0: placeholderRenderer = circleRenderer; break;
                    case 1: placeholderRenderer = arrowRenderer; break;
                    case 2: placeholderRenderer = arrowAnticlockwiseRenderer; break;
                    case 3: placeholderRenderer = arrowClockwiseRenderer; break;
                }
                mScaleFactor = options - objectChoice;

                ArrayList arr = new ArrayList();
                arr.add(center_X);
                arr.add(center_Y);
                queuedSentTaps.offer(arr);

            }
        }
    };

    private void setupRemoteVideo(int uid) {
        FrameLayout container = findViewById(R.id.remote_video_view_container);
        remoteSurfaceView = new SurfaceView(getBaseContext());
        remoteSurfaceView.setZOrderMediaOverlay(true);
        container.addView(remoteSurfaceView);
        agoraEngine.setupRemoteVideo(new VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid));
        // Display RemoteSurfaceView.
        remoteSurfaceView.setVisibility(View.VISIBLE);
    }

    private void setupLocalVideo() {
        agoraEngine.setExternalVideoSource(true, true, Constants.ExternalVideoSourceType.VIDEO_FRAME);
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
            // Start local preview.
            agoraEngine.startPreview();
            // Join the channel with a temp token.
            // You need to specify the user ID yourself, and ensure that it is unique in the channel.
            agoraEngine.joinChannel(token, channelName, uid, options);
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
            // TODO: Stop local video rendering.

            isJoined = false;
        }
    }

    //********** GLSURFACE METHODS **********//
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f,0.1f,0.1f,1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(/*context=*/ this);
        if (mSession != null) {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
        }

        // Prepare the other rendering objects.
        circleRenderer = ShapeRenderer.circle(this);
        arrowRenderer = ShapeRenderer.arrow(this);
        arrowAnticlockwiseRenderer = ShapeRenderer.arrowAnticlockwise(this);
        arrowClockwiseRenderer = ShapeRenderer.arrowClockwise(this);

        placeholderRenderer = circleRenderer;

        try {
            mPlaneRenderer.createOnGlThread(/*context=*/this, "trigrid.png");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read plane texture");
        }
        mPointCloud.createOnGlThread(/*context=*/this);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mDisplayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);

        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = mSession.update();
            Camera camera = frame.getCamera();
            ArrayList sentTap = queuedSentTaps.poll();
            if (sentTap != null && camera.getTrackingState() == TrackingState.TRACKING) {
                MotionEvent tap = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, sentTap.indexOf(0), sentTap.indexOf(1), 0);
                for (HitResult hit : frame.hitTest(tap)) {
                    // Check if any plane was hit, and if it was hit inside the plane polygon
                    Trackable trackable = hit.getTrackable();
                    // Creates an anchor if a plane or an oriented point was hit.
                    if ((trackable instanceof Plane
                            && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                            && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                            || (trackable instanceof Point
                            && ((Point) trackable).getOrientationMode()
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                        Log.d(TAG, "onDrawFrame: INSIDE IF");
                        // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (virtualObjects.size() >= 250) {
                            virtualObjects.get(0).getAnchor().detach();
                            virtualObjects.remove(0);
                        }
                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor is created on the Plane to place the 3D model
                        // in the correct position relative both to the world and to the plane.
                        virtualObjects.add(new VirtualObject(placeholderRenderer, hit.createAnchor(), mScaleFactor, 1));
                        break;
                    }
                }
            }

            // Draw background.
            mBackgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

            if (isShowPointCloud()) {
                // Visualize tracked points.
                PointCloud pointCloud = frame.acquirePointCloud();
                mPointCloud.update(pointCloud);
                mPointCloud.draw(viewmtx, projmtx);

                // Application is responsible for releasing the point cloud resources after
                // using it.
                pointCloud.release();
            }

            // Check if we detected at least one plane. If so, hide the loading message.
            if (mMessageSnackbar != null) {
                for (Plane plane : mSession.getAllTrackables(Plane.class)) {
                    if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                            && plane.getTrackingState() == TrackingState.TRACKING) {
                        hideLoadingMessage();
                        break;
                    }
                }
            }

            if (isShowPlane()) {
                // Visualize planes.
                mPlaneRenderer.drawPlanes(
                        mSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);
            }

            // Visualize anchors created by touch.

            for (VirtualObject virtualObject : virtualObjects) {
                if (virtualObject.getAnchor().getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }

                // Update and draw the model and its shadow.
                virtualObject.render(viewmtx, projmtx, lightIntensity);
            }

            sendARViewMessage();
        }catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    //********** AR HELPING METHODS **********//
    private void sendARViewMessage() {
        final Bitmap outBitmap = Bitmap.createBitmap(mSurfaceView.getWidth(), mSurfaceView.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(mSurfaceView, outBitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int copyResult) {
                if (copyResult == PixelCopy.SUCCESS) {
                    sendARView(outBitmap);
                } else {
                    CustomUtilities.showMessage(context, "Pixel copy failed.");
                }
            }
        }, mSenderHandler);
    }

    private void sendARView(Bitmap bitmap) {
        if (bitmap == null) return;

        //if (mSource.getConsumer() == null) return;

        //Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888,true);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int size = bitmap.getRowBytes() * bitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        bitmap.copyPixelsToBuffer(byteBuffer);
        byte[] data = byteBuffer.array();

        //mSource.getConsumer().consumeByteArrayFrame(data, MediaIO.PixelFormat.RGBA.intValue(), width, height, 0, System.currentTimeMillis());

        AgoraVideoFrame agoraVideoFrame = new AgoraVideoFrame();
        agoraVideoFrame.buf = data;
        agoraVideoFrame.stride = width;
        agoraVideoFrame.height = height;
        agoraVideoFrame.format = AgoraVideoFrame.FORMAT_RGBA;
        agoraVideoFrame.timeStamp = 0;
        agoraEngine.pushExternalVideoFrame(agoraVideoFrame);

    }

    private void showPointCloud(ImageButton button) {
        button.setImageResource((mHidePoint = !mHidePoint) ? R.drawable.ic_baseline_visibility_off_24 : R.drawable.ic_baseline_visibility_24);
    }

    private void showPlane(Button button) {
        button.setText((mHidePlane = !mHidePlane) ? "Show plane" :
                "Hide plane");
    }

    private boolean isShowPointCloud() {
        return !mHidePoint;
    }

    private boolean isShowPlane() {
        return !mHidePlane;
    }

    private void showLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showSnackbarMessage("Searching for surfaces...", false);
            }
        });
    }

    private void hideLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mMessageSnackbar != null) {
                    mMessageSnackbar.dismiss();
                }
                mMessageSnackbar = null;
            }
        });
    }

    private void showSnackbarMessage(String message, boolean finishOnDismiss) {
        mMessageSnackbar = Snackbar.make(
                ClientActivity.this.findViewById(android.R.id.content),
                message, Snackbar.LENGTH_INDEFINITE);
        mMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        if (finishOnDismiss) {
            mMessageSnackbar.setAction(
                    "Dismiss",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mMessageSnackbar.dismiss();
                        }
                    });
            mMessageSnackbar.addCallback(
                    new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            super.onDismissed(transientBottomBar, event);
                            finish();
                        }
                    });
        }
        mMessageSnackbar.show();
    }

    //********** CONTROL PANEL METHODS **********//
    public void initUI(){
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        ActionBar ab = getSupportActionBar();
        setContentView(R.layout.activity_client);
        if (ab != null) {
            ab.hide();
        }

        mMuteBtn = findViewById(R.id.btn_mute);
        mSurfaceView = findViewById(R.id.local_video_view_container);
        mDisplayRotationHelper = new DisplayRotationHelper(this);

        mHidePlane = true;
        mHidePoint = true;

        ImageButton hidePointButton = findViewById(R.id.btn_show_point_cloud);
        hidePointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPointCloud((ImageButton) v);
            }
        });

        //get device screen size
        mWidth= this.getResources().getDisplayMetrics().widthPixels;
        mHeight= this.getResources().getDisplayMetrics().heightPixels;


        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);// Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    // TODO: fix mute button
    public void onLocalAudioMuteClicked(View view) {
        isMuted = !isMuted;
        agoraEngine.muteLocalAudioStream(isMuted);
        mMuteBtn.setImageResource(isMuted ? R.drawable.ic_baseline_mic_off_24 : R.drawable.ic_baseline_mic_24);
    }
}