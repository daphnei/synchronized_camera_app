package gcm.play.android.samples.com.gcmquickstart;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by root on 4/3/16.
 */
public class FileMaker {
    private int id = 0;

    public FileMaker() {
        // Find the first available ID
        File mediaStorageDir = getDir();
        if (mediaStorageDir == null) {
            Log.d("dei", "Can't access Movies directory.");
            return;
        }

        File mediaFile;

        // Create a media file name
        do {
            this.id++;
            mediaFile = new File(fileNameFromId());
        } while (mediaFile.exists());
    }

    /**
     * Creates a media file with the specified ID in the name.
     * @param id The ID to create the next file for.
     * @return
     */
    public File createFile(int id) {
        this.id = id;

        return this.getOutputMediaFile();
    }

    public File getTempFile() {
        File mediaStorageDir = getDir();
        if (mediaStorageDir == null) {
            return null;
        }

        File tempFile = new File(mediaStorageDir.getPath() + File.separator + "tmp.mp4");

        return tempFile;
    }


    /**
     * Creates a media file in the {@code Environment.DIRECTORY_PICTURES} directory. The directory
     * is persistent and available to other applications like gallery.
     *
     * @return A file object pointing to the newly created file.
     */
    private File getOutputMediaFile() {
        File mediaStorageDir = getDir();
        if (mediaStorageDir == null) {
            return null;
        }

        File mediaFile = new File(fileNameFromId());

        if (mediaFile.exists()) {
            Log.d("dei", "File already exists on this phone: " + mediaFile.toString());
        }
        return mediaFile;
    }

    private File getDir() {
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

        return mediaStorageDir;
    }

    private String fileNameFromId() {
        String timeStamp = new SimpleDateFormat("MM_dd_kk_mm").format(new Date());
        String paddedID = String.format("%03d", id);
        return getDir().getPath() + File.separator +
                "VID_" + timeStamp + "_" + paddedID + ".mp4";
    }

    public int getNextId() {
        return id++;
    }

}
