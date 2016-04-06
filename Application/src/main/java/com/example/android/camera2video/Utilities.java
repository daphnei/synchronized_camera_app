package com.example.android.camera2video;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by root on 4/3/16.
 */
public class Utilities {
    private static int id = 1;

    /**
     * Creates a media file in the {@code Environment.DIRECTORY_PICTURES} directory. The directory
     * is persistent and available to other applications like gallery.
     *
     * @return A file object pointing to the newly created file.
     */
    public static File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return  null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "BirdVideos");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()) {
                Log.d("BirdVideos", "failed to create directory");
                return null;
            }
        }

        File mediaFile;
        do {
            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
            String paddedID = String.format("%03d", id++);
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + "_" + paddedID + ".mp4");
        } while (mediaFile.exists());
        return mediaFile;
    }
}
