package net.andrey_zabrodin.showserver;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.app.*;
import android.support.v4.app.NotificationCompat;
import java.io.File;
import java.util.Locale;

public class ShowServerService extends Service {
    private NotificationManager mNM;
    private boolean started=false;
    private SimpleWebServer webServer=null;

    @Override
    public void onCreate() {
        super.onCreate();
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }


    public boolean getRunStatus() {
        return started;
    }

    public void start(CharSequence rootPath, int port) {
        stop();
        String rootdir = new File((String) rootPath).getAbsolutePath();
        webServer = new SimpleWebServer(getApplicationContext(), new Handler(), port, rootdir);
        webServer.start();
        startForeground(1,createNotification(0));
    }

    public void stop() {
        started=false;
        if (webServer!=null) {
            webServer.stop();
            webServer = null;
        }
        mNM.cancel(1);
    }

    class LocalBinder extends Binder {
        ShowServerService getService() {
            return ShowServerService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    private void onChangeState(int state) {
        mNM.notify(1,createNotification(state));
    }

    private Notification createNotification(int state) {
        int icon= R.drawable.ic_error;
        switch (state) {
            case 0: icon=R.drawable.ic_idle; break;
            case 1: icon=R.drawable.ic_ready; break;
            case 2: icon=R.drawable.ic_connected; break;
            case 3: icon=R.drawable.ic_error; break;
        }
        return new NotificationCompat.Builder(this)
                .setContentTitle(String.format(Locale.US,"Show Server %d", state))
                .setContentText(String.format(Locale.US,"Show Server %d", state))
                .setSmallIcon(icon).build();
    }

    private void showNotification() {
    }

}
