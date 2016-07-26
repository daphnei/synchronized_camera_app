/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gcm.play.android.samples.com.gcmquickstart;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import android.support.v4.content.LocalBroadcastManager;

public class MyGcmListenerService extends GcmListenerService {

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(String from, Bundle data) {
        String message = data.getString("message");
        String idAsString = data.getString("id");

        Log.d("dei", "Received a message of type: " + message);

        int id = Integer.valueOf(idAsString);

        //Log.d("dei", "From: " + from);
        //Log.d("dei", "Message: " + message);

        if (from.startsWith("/topics/")) {
            // message received from some topic.
        } else {
            // normal downstream message.
        }

        Intent intent = new Intent(MainFragment.TOGGLE_PLAYBACK_INTENT);

        // You can also include some extra data.
        intent.putExtra("message", message);
        intent.putExtra("id", id);

        Log.d("dei", "About to broadcast the received message intent.");

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Log.d("dei", "Finished broadcasting the received message intent.");

        // [END_EXCLUDE]
    }
    // [END receive_message]


}
