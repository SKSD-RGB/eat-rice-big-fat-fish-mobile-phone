package com.xiaoman.standalone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 定时消息到点了。
 * 开机/闹钟到点时后拉起 MainActivity，由它往聊天里插一句。
 */
public class DueReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("due", true);
            ctx.startActivity(i);
        } catch (Throwable ignored) {}
    }
}
