package io.sast.demo.cameraIntrinsic;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.sast.demo.cameraIntrinsic.helpers.CameraPermissionHelper;
import io.sast.demo.cameraIntrinsic.helpers.DisplayRotationHelper;
import io.sast.demo.cameraIntrinsic.helpers.FullScreenHelper;
import io.sast.demo.cameraIntrinsic.helpers.TapHelper;
import io.sast.demo.cameraIntrinsic.renderer.BackgroundRenderer;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    GLSurfaceView surfaceView;
    final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    DisplayRotationHelper displayRotationHelper;
    TapHelper tapHelper;

    Runnable runnable;
    Handler handler;

    //ARCore
    Session session;
    private boolean installRequested;

    TextView txView;

    float[] translation, rotation, focalLength, principalPoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        txView = findViewById(R.id.txView);

        displayRotationHelper = new DisplayRotationHelper(this);
        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        setTextViewHandler();
    }

    private void setTextViewHandler() {


        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txView.setText("");
                        if (focalLength != null && focalLength.length == 2) {
                            txView.append("fx: " + focalLength[0] + "  fy: " + focalLength[1] + "\n");
                        }
                        if (principalPoint != null && principalPoint.length == 2) {
                            txView.append("px: " + principalPoint[0] + "  py: " + principalPoint[1] + "\n");
                        }
                        if (rotation != null) {
                            for (int i = 0; i < rotation.length; i++) {
                                String value = "";
                                switch (i) {
                                    case 0:
                                        value = "x";
                                        break;
                                    case 1:
                                        value = "y";
                                        break;
                                    case 2:
                                        value = "z";
                                        break;
                                    case 3:
                                        value = "w";
                                        break;
                                }
                                txView.append("r_" + value + " : " + rotation[i] + "  ");
                            }
                            txView.append("\n");
                        }
                        if (translation != null) {
                            for (int i = 0; i < translation.length; i++) {
                                String value = "";
                                switch (i) {
                                    case 0:
                                        value = "x";
                                        break;
                                    case 1:
                                        value = "y";
                                        break;
                                    case 2:
                                        value = "z";
                                        break;
                                }
                                txView.append("t_" + value + " : " + translation[i] + "  ");
                            }
                            txView.append("\n");
                        }
                    }
                });
                handler.postDelayed(runnable, 1000);
            }
        };
        runnable.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session == null) {
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

                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);

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
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                return;
            }
        }

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        try {
            backgroundRenderer.createOnGlThread(/*context=*/ this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (session == null) {
            return;
        }
        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame = null;
        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            frame = session.update();
            Camera camera = frame.getCamera();
            translation = camera.getPose().getTranslation();
            rotation = camera.getPose().getRotationQuaternion();

            focalLength = camera.getImageIntrinsics().getFocalLength();
            principalPoint = camera.getImageIntrinsics().getPrincipalPoint();

            backgroundRenderer.draw(frame);
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }
}
