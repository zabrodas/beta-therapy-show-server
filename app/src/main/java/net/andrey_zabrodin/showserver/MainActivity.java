package net.andrey_zabrodin.showserver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.view.View;
import android.widget.*;

public class MainActivity extends AppCompatActivity {

    ShowServerService mService = null;

    private EditText rootPathView;
    private EditText portView;
    private TextView statusView;
    private Button startButton,stopButton;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            statusView.setText("Service bound");
            mService = ((ShowServerService.LocalBinder)binder).getService();
            boolean rs = mService.getRunStatus();
            indicateServiceStatus();
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            statusView.setText("Service disconnected");
        }
    };

    private void readSettings() {
        SharedPreferences settings = getSharedPreferences("Settings", MODE_PRIVATE);
        String rootPath = settings.getString("rootPath", "/mnt/sd_card/showserver");
        int port = settings.getInt("port", 2222);
        rootPathView.setText(rootPath);
        portView.setText(String.valueOf(port));
    }

    private void indicateServiceStatus() {
        if (mService!=null) {
            boolean rs = mService.getRunStatus();
            if (rs) {
                statusView.setText("Service running");
            } else {
                statusView.setText("Service stopped");
            }
        } else {
            statusView.setText("No service bound");
        }
    }

    class ParcedSettings {
        String rootPath;
        int port;
    }

    private ParcedSettings verifySettings() {
        ParcedSettings p = new ParcedSettings();
        try {
            p.rootPath = String.valueOf(rootPathView.getText());
            p.port = Integer.valueOf(String.valueOf(portView.getText()));
            if (p.port<1 || p.port>65535) return null;
            return p;
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private void saveSettings() {
        ParcedSettings p=verifySettings();
        if (p==null) return;
        SharedPreferences.Editor editor = getSharedPreferences("Settings", MODE_PRIVATE).edit();
        editor.putString("rootPath", p.rootPath);
        editor.putInt("port", p.port);
        editor.apply();
    };

    private View.OnClickListener onClickStart=new View.OnClickListener() {
        @Override
        public void onClick(View v) {
                saveSettings();
                ParcedSettings p = verifySettings();
                if (p==null) return;
                if (mService!=null) {
                    mService.start(p.rootPath, p.port);
                }
                indicateServiceStatus();
        }
    };
    private View.OnClickListener onClickStop = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mService != null) {
                mService.stop();
            }
            indicateServiceStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusView = (TextView) findViewById(R.id.textView6);
        rootPathView = (EditText) findViewById(R.id.editText3);
        portView = (EditText) findViewById(R.id.editText6);
        startButton = (Button) findViewById(R.id.button2);
        stopButton = (Button) findViewById(R.id.button);
        startButton.setOnClickListener(onClickStart);
        stopButton.setOnClickListener(onClickStop);
     }

    @Override
    protected void onPause() {
        unbindService(mConnection);
        // if (mService!=null && !mService.getRunStatus()) stopService(new Intent(this, ShowServerService.class));
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusView.setText("Binding service");
        startService(new Intent(this, ShowServerService.class));
        boolean ec = bindService(new Intent(this, ShowServerService.class), mConnection, Context.BIND_AUTO_CREATE);
        if (!ec) {
            statusView.setText("No service bound");
        }
        readSettings();
        indicateServiceStatus();
        onClickStart.onClick(startButton);
    }

}
