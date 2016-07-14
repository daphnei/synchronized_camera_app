package gcm.play.android.samples.com.gcmquickstart;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Sends a message to all connected devices on the server.
 *
 * This needs to be handled as a background task -- performing this logic on the
 * main thread will throw a runtime exception due to Android restrictions around
 * networking.
 * <p/>
 * User: daphnei
 * Date: 2016-04-10
 * Time: 2:25 PM
 */
public class SendMessageTask extends AsyncTask<String, Void, Void> {
    public static final String API_KEY = "AIzaSyAYd5xaRxHrSuWzljlNPjdiIiYoja91vMw";

    // Whether or not sending the message was successful.
    private boolean mSuccess;
    private Context mContext;

    public SendMessageTask(Context context) {
        this.mContext = context;
    }

    @Override
    protected Void doInBackground(String... params) {
        // Whether or not this server message should start recording or stop recording.
        String stopOrStart = params[0];
        String id = params[1];

        try {
            // Prepare JSON containing the GCM message content. What to send and where to send.
            JSONObject jGcmData = new JSONObject();

            JSONObject jData = new JSONObject();
            jData.put("message", stopOrStart);
            Log.d("dei", "Message sent with id: " + id);
            jData.put("id", id);

            jGcmData.put("to", "/topics/global");
            jGcmData.put("data", jData);

            // Create connection to send GCM Message request.
            URL url = new URL("https://android.googleapis.com/gcm/send");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "key=" + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            Log.d("dei", "\nAbout to send a message of type: " + stopOrStart);

            // Send GCM message content.
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(jGcmData.toString().getBytes());

            // Need to consume the response, or the message doesn't seem to get sent properly.
            InputStream inputStream = conn.getInputStream();
            String resp = IOUtils.toString(inputStream);

            mSuccess = true;
        } catch (IOException | JSONException e) {
            // For some reason, the connection to the server is dead. Send out an intent
            // to the main process to try and reconnect.
            mSuccess = false;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);

        if (!mSuccess) {
            Intent intent = new Intent(MainFragment.RECONNECT_TO_SERVER_INTENT);
            LocalBroadcastManager.getInstance(this.mContext).sendBroadcast(intent);
        }
    }
}
