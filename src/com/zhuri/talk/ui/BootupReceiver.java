package com.zhuri.talk.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;

import com.zhuri.talk.TalkService;
import com.zhuri.talk.PstcpService;

public class BootupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
		context.startService(PstcpService.serviceIntent(context));
		context.startService(TalkService.serviceIntent(context));
		return;
    }
}
