package net.andrey_zabrodin.showserver;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;

/**
 * Created by azabrodi on 2017-10-09.
 */

public class MediaPlayerExt {
    private MediaPlayer mp=new MediaPlayer();
    private String imageFile=null;
    private SurfaceHolder surfaceHolder;

    public MediaPlayerExt() {
        super();
    }

    public synchronized void reset() {
        if (mp!=null) {
            mp.reset();
        }
        imageFile=null;
    }

    public synchronized void release() {
        if (mp!=null) {
            mp.release();
        }
        imageFile=null;
        surfaceHolder=null;
    }

    public synchronized void stop() {
        if (imageFile==null && mp!=null) {
            mp.stop();
        }
    }

    public synchronized void pause() {
        if (imageFile==null && mp!=null) {
            mp.pause();
        }
    }

    public synchronized void start() {
        if (imageFile!=null) {
            if (mp!=null) {
                mp.setDisplay(null);
                mp.reset();
            }
            redrawImage();
        } else if (mp!=null) {
            mp.setDisplay(surfaceHolder);
            mp.start();
        }
    }

    public synchronized void setVolume(float v1, float v2) {
        if (mp!=null) {
            mp.setVolume(v1, v2);
        }
    }

    public synchronized void setSourceFile(String fileName) throws IOException {
        if (
            fileName.endsWith(".jpg") ||
            fileName.endsWith(".png") ||
            fileName.endsWith(".gif")
         ) {
            imageFile=fileName;
            mp.setDisplay(null);
        } else {
            imageFile=null;
            mp.setDisplay(surfaceHolder);
            mp.setDataSource(String.valueOf(Uri.fromFile(new File(fileName))));
        }
    }

    public synchronized void prepare() throws IOException {
        if (mp!=null && imageFile==null) {
            mp.prepare();
        }
    }

    private synchronized void redrawImage() {
        if (mp!=null) {
            mp.setDisplay(null);
            mp.reset();
        }
        if (imageFile!=null && surfaceHolder!=null) {
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas!=null) {
                Rect dst = canvas.getClipBounds();
                Bitmap bmp = BitmapFactory.decodeFile(imageFile);
                canvas.drawBitmap(bmp, null, dst, null);
                surfaceHolder.unlockCanvasAndPost(canvas);
                bmp.recycle();
            }
        }
    }

    public synchronized void setDisplay(SurfaceHolder sh) {
        surfaceHolder = sh;
        if (imageFile!=null) {
            mp.setDisplay(null);
            redrawImage();
        } else if (mp != null) {
            mp.setDisplay(sh);
        }
    }
}
