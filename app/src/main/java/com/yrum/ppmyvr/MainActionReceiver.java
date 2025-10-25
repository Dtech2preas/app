package com.yrum.ppmyvr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MediaActionReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Media action received: " + action);

        // Forward the action to MainActivity
        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.setAction(action);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(activityIntent);
    }
}