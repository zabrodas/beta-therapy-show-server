package net.andrey_zabrodin.showserver;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    private View mContentView;
    private SurfaceView surfaceView;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_fullscreen);
            handler = new Handler();
            mContentView = findViewById(R.id.LinearLayout);
            surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
            hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized(FullscreenActivity.class) {
            if (self != null) {

            }
            self = this;
            if (mlistener!=null) mlistener.onAvailable(surfaceView.getHolder());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    private void hideSystemUi() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void showSystemMenu() {
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
    }

    static private FullscreenActivity self=null;
    static interface SurfaceHolderEventListener {
        void onAvailable(SurfaceHolder sh);
        void onDestroyed();
    }
    static SurfaceHolderEventListener mlistener=null;

    @Override
    protected void onPause() {
        synchronized(FullscreenActivity.class) {
            if (self!=null) {
                if (mlistener!=null) mlistener.onDestroyed();
                self = null;
            }
        }
        super.onPause();
    }

    static public void getSurfaceHolder(Context context, SurfaceHolderEventListener listener) {
        synchronized(FullscreenActivity.class) {
            mlistener=listener;
            //if (self == null) {
                Intent intent=new Intent();
                intent.setClass(context, FullscreenActivity.class);
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            //} else {
            //    mlistener.onAvailable(self.surfaceView.getHolder());
            //}
        }
    }
}
