package com.github.DenFade.gdlistener;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.github.DenFade.gdlistener.event.AbstractEvent;
import com.github.DenFade.gdlistener.event.AwardedLevelslUpdateEvent;
import com.github.DenFade.gdlistener.event.FollowedUserLevelsUpdateEvent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class EventLoopService extends Service {

    private final Timer timer = new Timer();
    private EventLoop loop;
    private boolean toggleToast = false;

    public EventLoopService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();


        startForeground(1, new NotificationCompat.Builder(this, getString(R.string.service_channel_id))
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("EventLoopService is now running...")
                .setContentText("Started At: " + new Date().toString())
                .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        );
        Collection<AbstractEvent<?>> list = new ArrayList<>();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int period = Integer.parseInt(Objects.requireNonNull(sp.getString("loopDelay", "30000")));
        toggleToast = sp.getBoolean("withToast", false);
        if(sp.getBoolean("followedSwitch", false)) list.add(new FollowedUserLevelsUpdateEvent());
        if(sp.getBoolean("awardedSwitch", true)) list.add(new AwardedLevelslUpdateEvent());

        loop = new EventLoop(list);

        timer.schedule(loop, 5000, period);
        Log.d("Timer", "schedule 호출");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("Timer", "cancel 호출: "+loop.cancel());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class EventLoop extends TimerTask {

        private Handler handler;
        private Collection<AbstractEvent<?>> events;

        EventLoop(Collection<AbstractEvent<?>> events){
            this.events = events;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void run() {
            Log.d("Event", "start event!");
            Handler handler = new Handler(Looper.getMainLooper());

            try{
                Gson gson = new Gson();
                for(AbstractEvent event : events){
                    event.dbInit(); //when db not exists
                    List items = event.run(getApplicationContext());
                    if(items == null) return;
                    List updated = event.filter(event.dbLoad(), items);
                    event.dbUpdateAndNotify(updated, EventLoopService.this);
                }
                if(toggleToast){
                    handler.post(() -> Toast.makeText(EventLoopService.this, "Event: Loop successfully", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e){
                if(toggleToast){
                    handler.post(() -> Toast.makeText(EventLoopService.this, "Event: An error occurred\n" + e.getClass().getName() + ": " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
                }
                e.printStackTrace();
            }
            Log.d("Event", "end event!");
        }
    }


}
