package net.andrey_zabrodin.showserver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.VideoView;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    private View mContentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        self=this;
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mContentView = findViewById(R.id.LinearLayout);
        hideSystemUi();

        setUrl(getIntent());
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


    @Override
    protected void onDestroy() {
        super.onDestroy();
        self=null;
    }

    static public void activateFullScreenActiity(Context context, Intent intent) {
        if (self==null) {
            intent.setClass(context, FullscreenActivity.class);
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } else {
            self.setUrl(intent);
            self.setVisible(true);
        }
    }

    private void setUrl(Intent intent) {
        ImageView iv = (ImageView) findViewById(R.id.imageView);
        VideoView vv = (VideoView) findViewById(R.id.videoView);

        String it = intent.getType();
        switch (it) {
            case "image": {
                Uri uri = intent.getData();
                if (uri!=null) {
                    iv.setImageURI(uri);
                    vv.stopPlayback();
                    vv.setVisibility(View.GONE);
                    iv.setVisibility(View.VISIBLE);
                }
                break;
            }
            case "video": {
                Uri uri = intent.getData();
                if (uri!=null) {
                    vv.setVideoURI(uri);
                    vv.setVisibility(View.VISIBLE);
                    iv.setVisibility(View.GONE);
                    vv.start();
                } else {
                    vv.pause();
                }
                break;
            }
            case "pauseVideo": {
                vv.pause();
                break;
            }
            case "continueVideo": {
                vv.start();
                break;
            }
            default:
                break;
        }
    }

}
