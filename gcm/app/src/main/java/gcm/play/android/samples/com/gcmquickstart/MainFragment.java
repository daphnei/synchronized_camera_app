/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gcm.play.android.samples.com.gcmquickstart;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.Context;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntegerRes;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    public static final String START_RECORDING_MESSAGE = "start";
    public static final String STOP_RECORDING_MESSAGE = "stop";
    public static final String UPDATE_FOCUS_MESSAGE = "focus";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private static final String TAG = "MainFragment";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final double DEFAULT_RECORD_INTERVAL = 120;
    private static final double DEFAULT_RECORD_LENGTH = 20;
    private static final int DEFAULT_TIMES_TO_RECORD = 10;
    private static final double LENGTH_OF_FLASH_SYNC = 0.5;


    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private TextureView mTextureView;

    /**
     *  Button that trigger the camera to automatically refocus on the central object.
     */
    private Button mButtonRecofus;

    private CheckBox mCheckboxLeader;

    private SeekBar mSeekBar;

    private TextView focusValueText;

    /** The last focus length used to set the focus on the camera of this device.
     *  This is cached so that the info can be shared with other devices.
     */
    private float mLastFocusLength;

    /** Callback for video capture, used to listen for changes in focus state.
     * */
    CameraCaptureSession.CaptureCallback mCaptureCallback;

    /**
     * A refernce to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * Used to find the next media file to save to.
     */
    private FileMaker fileMaker;

    /**
     * A reference to the current {@link CameraCaptureSession} for
     * preview.
     */
    private CameraConstrainedHighSpeedCaptureSession mPreviewSession;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    public static String TOGGLE_PLAYBACK_INTENT = "togglePlaybackIntent";
    public static String RECONNECT_TO_SERVER_INTENT = "reconnectToServerIntent";

    private ProgressBar mRegistrationProgressBar;
    private TextView mInformationTextView;
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private BroadcastReceiver mReconnectBroadcastReceiver;
    private BroadcastReceiver mTogglePlaybackReceiver;

    private boolean isReceiverRegistered;

    private boolean mIsFlashOn;
    private boolean mIsLeader;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.d("dei", "SURFACE TEXUTRE DESTROYED!");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link Size} of video recording.
     */
    private Size mVideoSize;

    /**
     * Camera preview.
     */
    private CaptureRequest.Builder mPreviewBuilder;

    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;

    /**
     * Used to play the two sounds.
     */
    private MediaPlayer mBeepPlayer;
    private MediaPlayer mCanaryPlayer;
    private Handler mCanaryStopSoundHandler;
    private Handler mRepeatRecordingHandler;

    private final int[] mCanarySoundOffsets = {2547, 20802, 38491, 56604, 74600, 92600, 110700, 128600, 146500, 165000};

    private int mNextCanarySoundIndex = 0;

    /**
     * The file that will get written to.
     */
    private File mOutputFile;

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo;

    /**
     * Whether or not app is in cycle of recording every so many seconds.
     */
    private boolean mInRecordingcycle;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * The characteristics of the camera being used.
     */
    private CameraCharacteristics mCharacteristics;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    private static Size getOptVideoSize() {
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_HIGH);
        return new Size(profile.videoFrameWidth, profile.videoFrameHeight);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        fileMaker = new FileMaker();

        mTextureView = (TextureView) view.findViewById(R.id.texture);

        mButtonRecofus = (Button) view.findViewById(R.id.focus);
        mCheckboxLeader = (CheckBox) view.findViewById(R.id.checkBoxIsLeader);
        mSeekBar = (SeekBar) view.findViewById(R.id.seekBar);
        focusValueText = (TextView) view.findViewById(R.id.focusValue);

        mButtonRecofus.setOnClickListener(this);
        mCheckboxLeader.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
           @Override
           public void onCheckedChanged(CompoundButton compoundButton, boolean checkBoxSet) {
               mIsLeader = checkBoxSet;
           }
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                updateFocus(i);
                resetPreviewSession();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
//                new SendMessageTask(getContext()).execute(
//                    MainFragment.UPDATE_FOCUS_MESSAGE,
//                    "-1",
//                    Float.toString(mLastFocusLength));
            }
        });

        setupCaptureCallback();

        setUpSoundPlayers();

        mRepeatRecordingHandler = new Handler();

        //// FOR COMMUNICATION
        // Registering BroadcastReceiver

        mRegistrationProgressBar = (ProgressBar) view.findViewById(R.id.registrationProgressBar);
        mRegistrationBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mRegistrationProgressBar.setVisibility(ProgressBar.GONE);
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(context);
                boolean sentToken = sharedPreferences
                        .getBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false);
                if (sentToken) {
                    mInformationTextView.setText(getString(R.string.gcm_send_message));
                } else {
                    mInformationTextView.setText(getString(R.string.token_error_message));
                }
            }
        };
        mTogglePlaybackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getExtras().get("message").equals(START_RECORDING_MESSAGE)) {
                    // Start recording.
                    toggleVideoRecording(true, intent.getExtras().getInt("id"));
                }
                else if (intent.getExtras().get("message").equals(STOP_RECORDING_MESSAGE)){
                    toggleVideoRecording(false, intent.getExtras().getInt("id"));
                } else {
                    updateFocus(intent.getExtras().getFloat("focus"));
                    resetPreviewSession();
                }
            }
        };

        mReconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // No longer allow recording until connection is reestablished.
                mInformationTextView.setText("Record failed. Trying to reconnect to server.");

                // Try reestablishing connection.
                intent = new Intent(MainFragment.this.getActivity(), RegistrationIntentService.class);
                MainFragment.this.getActivity().startService(intent);
            }
        };

        mInformationTextView = (TextView) view.findViewById(R.id.informationTextView);

        mIsFlashOn = false;
        mIsLeader = false;

        registerReceiver();

        if (checkPlayServices()) {
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this.getActivity(), RegistrationIntentService.class);
            this.getActivity().startService(intent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        registerReceiver();
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();

        LocalBroadcastManager.getInstance(this.getActivity()).unregisterReceiver(
                mReconnectBroadcastReceiver);
        LocalBroadcastManager.getInstance(this.getActivity()).unregisterReceiver(
                mTogglePlaybackReceiver);
        LocalBroadcastManager.getInstance(this.getActivity()).
                unregisterReceiver(mRegistrationBroadcastReceiver);
        isReceiverRegistered = false;

        super.onPause();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.focus) {
            //mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

            if (mPreviewBuilder.get(CaptureRequest.CONTROL_AF_MODE) ==
                    CameraMetadata.CONTROL_AF_MODE_AUTO) {
                Log.d("dei", "Turning off autofocus");
                this.mSeekBar.setEnabled(true);
                this.mButtonRecofus.setText(R.string.switch_autofocus);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            }
            else if (mPreviewBuilder.get(CaptureRequest.CONTROL_AF_MODE) ==
                    CameraMetadata.CONTROL_AF_MODE_OFF) {
                Log.d("dei", "Turning on autofocus");
                this.mButtonRecofus.setText(R.string.switch_manfocus);
                this.mSeekBar.setEnabled(false);
                this.mLastFocusLength = -1;
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            }


            //updatePreview();
            resetPreviewSession();
        }
    }

    public void setupCaptureCallback() {
        mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
             @Override
             public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                           @NonNull CaptureRequest request,
                                                           @NonNull CaptureResult partialResult) {
                //Log.d("dei", "HERE");
            }


            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
                //Log.d("dei", "HERE 2");
            }
         };
    }

    public void doRecordLoop(
            final double lengthToRecord, final double intervalToRecord, final int numberOfRecordings) {
        // Start the recording.
        new SendMessageTask(MainFragment.this.getContext()).execute(
                MainFragment.START_RECORDING_MESSAGE, Integer.toString(fileMaker.getNextId()));

        // Automatically stop recording after the specified amount of time.
        mRepeatRecordingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //mRecordingLock.acquireUninterruptibly();

                Log.d("dei", "In callback to stop recording");
                new SendMessageTask(MainFragment.this.getContext()).execute(
                        MainFragment.STOP_RECORDING_MESSAGE, Integer.toString(fileMaker.getNextId()));

                if (numberOfRecordings - 1 > 0) {
                    mRepeatRecordingHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                        //mRecordingLock.acquireUninterruptibly();
                        //if (mInRecordingcycle) {
                            doRecordLoop(lengthToRecord, intervalToRecord, numberOfRecordings - 1);
                        //}
                        //mRecordingLock.release();
                        }
                    }, (long) ((intervalToRecord + LENGTH_OF_FLASH_SYNC) * 1000));
                } else {
                    cancelRecordingCycle();
                }

                //mRecordingLock.release();
            }
        }, (long) (lengthToRecord * 1000));
    }

    public void cancelRecordingCycle() {
        mInRecordingcycle = false;
    }

    public void updateFocus(float focusSliderValue) {
        if (focusSliderValue >= 0) {
            mLastFocusLength = focusSliderValue;

            float minimumLens = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            float focus = (((float) focusSliderValue) * minimumLens / 100);

            Log.d("dei", "focus：" + focusSliderValue);

            mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus);
            focusValueText.setText(String.format("%.2f", focus));
        }
    }
    public void toggleVideoRecording(boolean toStartRecording, int id) {
        if (mIsRecordingVideo) {
            if (!toStartRecording) {
                // If I am a leader, turn on the flash.
                // Leader or not, pause for some amount of time before actually stopping recording,
                // so that the change in flash can be recorded.
                if (mIsLeader) {
                    toggleFlash(true);
                }

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsLeader) {
                            stopSounds();
                            toggleFlash(false);
                        }

                        stopRecordingVideo();

                        // Allow leader status to be changed again.
                        mCheckboxLeader.setEnabled(true);
                    }
                }, (long) (LENGTH_OF_FLASH_SYNC * 1000));
            }
        } else {
            if (toStartRecording) {
                // Set the output file as specified in the intent.
                mOutputFile = fileMaker.createFile(id);

                // Don't allow leader status to be changed while recording.
                this.mCheckboxLeader.setEnabled(false);

                AsyncTask<Void, Void, Boolean> resetAndStartAsyncTask = new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... voids) {
                        startRecordingVideo();

                        return true;
                    }

                    @Override
                    protected void onPostExecute(Boolean aBoolean) {
                        super.onPostExecute(aBoolean);

                        if (mIsLeader) {
                            playSounds();
                         }

                        mInformationTextView.setText("RECORDING...");
                    }
                };

                resetAndStartAsyncTask.execute();
            }
        }
    }

    public void toggleFlash(boolean turnOn) {
        mPreviewBuilder.set(CaptureRequest.FLASH_MODE,
                turnOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

        resetPreviewSession();
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            isReceiverRegistered = true;

            LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(
                    mRegistrationBroadcastReceiver,
                    new IntentFilter(QuickstartPreferences.REGISTRATION_COMPLETE));

            LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(
                    mTogglePlaybackReceiver, new IntentFilter(MainFragment.TOGGLE_PLAYBACK_INTENT));

            LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(
                    mReconnectBroadcastReceiver, new IntentFilter(MainFragment.RECONNECT_TO_SERVER_INTENT));
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this.getActivity());
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this.getActivity(), resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.i(TAG, "This device is not supported.");
                this.getActivity().finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            mCharacteristics = manager.getCameraCharacteristics(cameraId);
//            StreamConfigurationMap map = characteristics
//                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mVideoSize = getOptVideoSize();
            mPreviewSize = getOptVideoSize();

//            int orientation = getResources().getConfiguration().orientation;
//            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//            } else {
//                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
//            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {

        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            updateFocus(mLastFocusLength);

            mCameraDevice.createConstrainedHighSpeedCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = (CameraConstrainedHighSpeedCaptureSession) cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        setUpCaptureRequestBuilder(mPreviewBuilder);
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        resetPreviewSession();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void resetPreviewSession() {
        try {
            mPreviewSession.stopRepeating();
            mPreviewSession.setRepeatingBurst(
                    mPreviewSession.createHighSpeedRequestList(mPreviewBuilder.build()),
                    mCaptureCallback, mBackgroundHandler);
//            mPreviewSession.setRepeatingRequest(
//                    mPreviewBuilder.build(),
//                    mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(240, 240));
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        //builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth()) ;
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpSoundPlayers() {
        mBeepPlayer = MediaPlayer.create(this.getContext(), R.raw.beep_silent);
        mCanaryPlayer = MediaPlayer.create(this.getContext(), R.raw.canary);

        //mBeepPlayer.setNextMediaPlayer(mCanaryPlayer);
        mBeepPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d("dei", "Starting to play at " + mCanarySoundOffsets[mNextCanarySoundIndex]);
                mCanaryPlayer.seekTo(mCanarySoundOffsets[mNextCanarySoundIndex++]);

                if (mNextCanarySoundIndex == mCanarySoundOffsets.length-1) {
                    mNextCanarySoundIndex = 0;
                }

                mCanaryPlayer.start();

                mCanaryStopSoundHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mCanaryPlayer.isPlaying())
                            mCanaryPlayer.pause();
                    }
                }, 35000);
            }
        });

        mCanaryStopSoundHandler = new Handler();
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setMaxFileSize(0);
        //mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mOutputFile = fileMaker.createFile(fileMaker.getNextId());

        mMediaRecorder.setOutputFile(mOutputFile == null ? null : mOutputFile.getAbsolutePath());

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_HIGH);
        mMediaRecorder.setProfile(profile);

        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);

        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int i, int i1) {
                Log.d("dei", "ERROR IN RECORDING");
            }
        });


        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    private void playSounds() {
        mBeepPlayer.start();
        // The canary sound will automatically start when the beep sound is finished.
    }

    private void stopSounds() {
        try {
            mCanaryStopSoundHandler.removeCallbacksAndMessages(null);

            if (mCanaryPlayer.isPlaying()) {
                mCanaryPlayer.pause();
                //mBeepPlayer.stop();
            } else if (mBeepPlayer.isPlaying()) {
                mBeepPlayer.stop();

                mBeepPlayer.prepare();
            }
        } catch (IOException e) {
            Log.e(e.toString(), "stopSounds");
        }
    }

    private void startRecordingVideo() {
        //mRecordingLock.acquireUninterruptibly();

        try {
            // UI
            mIsRecordingVideo = true;

            // Start recording
            Log.d("dei", "About to START mediarecorder recording");
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);

            mMediaRecorder.start();
            Log.d("dei", "STARTING mediarecorder recording.");
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        //mRecordingLock.release();
    }

    private void stopRecordingVideo() {
        //mRecordingLock.acquireUninterruptibly();

        // UI
        mIsRecordingVideo = false;
        // Stop recording

        try {
            mCameraOpenCloseLock.acquire();
            mCameraDevice.close();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }

        mMediaRecorder.stop();

        Log.d("dei", "Finish with STOP mediarecorder recording.");

        mMediaRecorder.reset();

        try {
            CameraManager manager = (CameraManager) this.getActivity().getSystemService(Context.CAMERA_SERVICE);
            manager.openCamera(manager.getCameraIdList()[0], mStateCallback, null);
        } catch (CameraAccessException e){
            Toast.makeText(this.getActivity(), "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            this.getActivity().finish();
        }

        Activity activity = getActivity();
        if (null != activity) {
            mInformationTextView.setText("Video saved: " + mOutputFile.getAbsolutePath());
            mInformationTextView.setText("Video saved.");
        }
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }

}