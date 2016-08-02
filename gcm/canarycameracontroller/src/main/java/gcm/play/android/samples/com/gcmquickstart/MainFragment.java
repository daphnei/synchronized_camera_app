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
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
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
     * Button to record video
     */
    private Button mButtonVideo;

    /**
     * Button to keep recording video every time_interval.
     */
    private Button mButtonAutoVideo;

    /**
     *  Button that trigger the camera to automatically refocus on the central object.
     */
    private Button mButtonRecofus;

    private CheckBox mCheckboxLeader;

    private SeekBar mSeekBar;

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
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo;

    /**
     * Whether or not app is in cycle of recording every so many seconds.
     */
    private boolean mInRecordingcycle;

    private Handler mRepeatRecordingHandler;

    private float mLastFocusValue;


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
    private EditText mIntervalToRecordText;
    private EditText mLengthToRecordText;
    private EditText mNumberOfRecordingsText;

    private boolean isReceiverRegistered;


    public static MainFragment newInstance() {
        return new MainFragment();
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
        mIntervalToRecordText = (EditText) view.findViewById(R.id.intervalToRecord);
        mLengthToRecordText = (EditText) view.findViewById(R.id.secondToRecord);
        mNumberOfRecordingsText = (EditText) view.findViewById(R.id.numRecordingsToMake);

        mButtonVideo = (Button) view.findViewById(R.id.video);
        mButtonAutoVideo = (Button) view.findViewById(R.id.video_repeat);

        mButtonAutoVideo.setOnClickListener(this);
        mButtonVideo.setOnClickListener(this);

        mSeekBar = (SeekBar) view.findViewById(R.id.seekBar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mLastFocusValue = new Float(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                new SendMessageTask(getContext()).execute(
                        MainFragment.UPDATE_FOCUS_MESSAGE,
                        "",
                        Float.toString(mLastFocusValue));
            }
        });

        mRepeatRecordingHandler = new Handler();

        // Enable when a connection to the server has been made.
        mButtonVideo.setEnabled(false);
        mButtonAutoVideo.setEnabled(false);

        // Set defaults for these.
        mIntervalToRecordText.setText(Double.toString(DEFAULT_RECORD_INTERVAL));
        mLengthToRecordText.setText(Double.toString(DEFAULT_RECORD_LENGTH));
        mNumberOfRecordingsText.setText(Integer.toString(DEFAULT_TIMES_TO_RECORD));

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
                    mButtonVideo.setEnabled(true);
                    mButtonAutoVideo.setEnabled(true);
                } else {
                    mInformationTextView.setText(getString(R.string.token_error_message));
                }
            }
        };
        mTogglePlaybackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("dei", "Message received intent HIT");

                if (intent.getExtras().get("message").equals(START_RECORDING_MESSAGE)) {
                    // Start recording.
                    toggleVideoRecording(true, intent.getExtras().getInt("id"));
                } else {
                    toggleVideoRecording(false, intent.getExtras().getInt("id"));
                }
            }
        };

        mReconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // No longer allow recording until connection is reestablished.
                mButtonVideo.setEnabled(false);
                mButtonAutoVideo.setEnabled(false);
                mInformationTextView.setText("Record failed. Trying to reconnect to server.");

                // Try reestablishing connection.
                intent = new Intent(MainFragment.this.getActivity(), RegistrationIntentService.class);
                MainFragment.this.getActivity().startService(intent);
            }
        };

        mInformationTextView = (TextView) view.findViewById(R.id.informationTextView);

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

        registerReceiver();
    }

    @Override
    public void onPause() {
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
        switch (view.getId()) {
            case R.id.video: {
                // Disable this button so that the user can't spam it.
                // Also disable the other record button.
                this.mButtonVideo.setEnabled(false);
                this.mButtonAutoVideo.setEnabled(false);

                String message;
                if (mIsRecordingVideo) {
                    message = MainFragment.STOP_RECORDING_MESSAGE;
                } else {
                    message = MainFragment.START_RECORDING_MESSAGE;
                }

                new SendMessageTask(this.getContext()).execute(
                        message, Integer.toString(fileMaker.getNextId()), "-1");

                break;
            }
            case R.id.video_repeat: {
                this.mButtonVideo.setEnabled(false);
                this.mButtonAutoVideo.setEnabled(false);
                this.mLengthToRecordText.setEnabled(false);
                this.mIntervalToRecordText.setEnabled(false);
                this.mNumberOfRecordingsText.setEnabled(false);

                final double lengthToRecord = Double.parseDouble(mLengthToRecordText.getText().toString());
                final double intervalToRecord = Double.parseDouble(mIntervalToRecordText.getText().toString());
                final int numberOfRecordings = Integer.parseInt(mNumberOfRecordingsText.getText().toString());

                if (mInRecordingcycle) {
                    // Cancel the current recording cycle.

                    // Clear all tasks posted to the handlers.
                    mRepeatRecordingHandler.removeCallbacksAndMessages(null);

                    // If currently recording, then halt it.
                    if (mIsRecordingVideo) {
                        new SendMessageTask(MainFragment.this.getContext()).execute(
                                MainFragment.STOP_RECORDING_MESSAGE,
                                Integer.toString(fileMaker.getNextId()),
                                "-1");
                    }

                    cancelRecordingCycle();
                } else {
                    mInRecordingcycle = true;
                    this.mButtonAutoVideo.setText(R.string.stop_automated);

                    doRecordLoop(lengthToRecord, intervalToRecord, numberOfRecordings);
                }
            }
        }
    }

    public void doRecordLoop(
            final double lengthToRecord, final double intervalToRecord, final int numberOfRecordings) {
        // Start the recording.
        new SendMessageTask(MainFragment.this.getContext()).execute(
                MainFragment.START_RECORDING_MESSAGE,
                Integer.toString(fileMaker.getNextId()),
                "-1");

        // Automatically stop recording after the specified amount of time.
        mRepeatRecordingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //mRecordingLock.acquireUninterruptibly();

                Log.d("dei", "In callback to stop recording");
                new SendMessageTask(MainFragment.this.getContext()).execute(
                        MainFragment.STOP_RECORDING_MESSAGE,
                        Integer.toString(fileMaker.getNextId()),
                        "-1");

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

        this.mButtonVideo.setEnabled(true);
        this.mButtonAutoVideo.setText(R.string.record_automated);
        this.mButtonAutoVideo.setEnabled(true);
        this.mLengthToRecordText.setEnabled(true);
        this.mIntervalToRecordText.setEnabled(true);
        this.mNumberOfRecordingsText.setEnabled(true);
    }

    public void toggleVideoRecording(boolean toStartRecording, int id) {
        if (mIsRecordingVideo) {
            if (!toStartRecording) {
                mInformationTextView.setText("Stopped recording.");
                this.mButtonVideo.setText("Record Once");
                mIsRecordingVideo = false;
            }
        } else {
            if (toStartRecording) {
                mInformationTextView.setText("RECORDING...");
                this.mButtonVideo.setText("STOP");
                mIsRecordingVideo = true;
            }
        }
        
        this.mButtonVideo.setEnabled(true);
        this.mButtonAutoVideo.setEnabled(true);
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


}
