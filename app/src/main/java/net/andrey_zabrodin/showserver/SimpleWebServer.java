package net.andrey_zabrodin.showserver;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.Objects;

import static android.media.AudioManager.FLAG_SHOW_UI;
import static java.lang.Thread.sleep;

class SimpleWebServer implements Runnable {

    private static final String TAG = "SimpleWebServer";

    /**
     * The port number we listen to
     */
    private final int mPort;

    /**
     * {@link android.content.res.AssetManager} for loading files to serve.
     */
    private final String mrootdir;
    private final Context mcontext;
    private final Handler mhandler;

    /**
     * True if the server is running.
     */
    private boolean mIsRunning;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;
    private File[] cached_filelist=null;
    private MediaPlayerExt[] mplayers=new MediaPlayerExt[2];
    private AudioManager audioManager;

    SimpleWebServer(Context context, Handler handler, int port, String rootdir) {
        mPort = port;
        mrootdir = rootdir;
        mcontext=context;
        mhandler=handler;
    }

    void start() {
        mIsRunning = true;
        new Thread(this).start();
    }

    void stop() {
        try {
            mIsRunning = false;
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mPort);
            while (mIsRunning) {
                Socket socket = mServerSocket.accept();
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
        } catch (Exception e) {
            Log.e(TAG, "Web server error.", e);
        } finally {
            for (int i=0; i<mplayers.length; i++) {
                MediaPlayerExt mplayer = mplayers[i];
                if (mplayer != null) {
                    mplayer.reset();
                    mplayer.release();
                    mplayers[i] = null;
                }
            }
        }
    }

    private class ReplyContent {
        int code=200;
        String codeStr="OK";
        byte[] bytes;
        String contentType="text/plain";
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void handle(Socket socket) throws Exception {
        BufferedReader reader = null;
        PrintStream output = null;
        try {
            String route = null;

            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                if (line.startsWith("GET /")) {
                    int start = line.indexOf('/') + 1;
                    int end = line.indexOf(' ', start);
                    route = line.substring(start, end);
                    break;
                }
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());

            // Prepare the content to send.
            if (null == route) {
                writeServerError(output);
                return;
            }

            ReplyContent reply = proceedRequest(route);
            if (null == reply) {
                writeServerError(output);
                return;
            }

            // Send out the content.
            output.println(String.format(Locale.US,"HTTP/1.0 %d %s", reply.code, reply.codeStr));
            output.println("Content-Type: " + reply.contentType);
            output.println("Content-Length: " + (reply.bytes != null ? reply.bytes.length : 0));
            output.println();
            if (reply.bytes != null) output.write(reply.bytes);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }

    /**
     * Writes a server error response (HTTP/1.0 500) to the given output stream.
     *
     * @param output The output stream.
     */
    private void writeServerError(PrintStream output) {
        output.println("HTTP/1.0 500 Internal Server Error");
        output.flush();
    }

    private String buid_filelist() {
        try {
            File rd = new File(mrootdir);
            if (!rd.isDirectory()) return String.format("%s is not a directory", mrootdir);
            cached_filelist = rd.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif") ||
                            name.endsWith(".wav") || name.endsWith(".mp3") ||
                            name.endsWith(".mp4");
                }
            });
            return null;
        } catch (Exception e) {
            return e.toString();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private ReplyContent proceedRequest(String path)  {
        try {
            if (path.startsWith("?")) {
                int eqi = path.indexOf('=');
                String pn, pv;
                if (eqi < 0) {
                    pn = path.substring(1);
                    pv = "";
                } else {
                    pn = path.substring(1, eqi);
                    pv = URLDecoder.decode(path.substring(eqi + 1), "UTF-8");
                }
                switch (pn) {
                    case "mfilelist":
                        return getFileList(pv);

                    case "setMainVolume":
                        return doSetMainVolume(pv);
                    case "getMainVolume":
                        return doGetMainVolume();

                    case "play": // ?play=<channel>,<fileindex>
                        return doPlayFile(pv);

                    case "stop":
                    case "pause":
                    case "continue":
                    case "setVolume":
                    case "getVolume":
                        return doCommandOnChannel(pn, pv);
                    default:
                        throw new Exception("Unknown request");
                }
            } else {
                if (path.equals("")) path = "index.html";
                return loadFile(path);
            }
        } catch (Exception e) {
            ReplyContent reply = new ReplyContent();
            reply.code = 400;
            reply.bytes = e.toString().getBytes();
            return reply;
        }
    }

    @NonNull
    private ReplyContent doGetMainVolume() {
        ReplyContent reply = new ReplyContent();
        try {
            if (audioManager == null) audioManager = (AudioManager) mcontext.getSystemService(Context.AUDIO_SERVICE);
            float v = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            v/=audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            reply.bytes=String.format(Locale.US,"{\"volume\": %f}",v).getBytes();
        } catch (Exception e) {
            reply.code = 400;
            reply.bytes = e.toString().getBytes();
        }
        return reply;
    }

    @Nullable
    private ReplyContent doSetMainVolume(String pv) {
        try {
            if (audioManager == null) audioManager = (AudioManager) mcontext.getSystemService(Context.AUDIO_SERVICE);
            Float v = Float.valueOf(pv);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*v+0.5),FLAG_SHOW_UI);
        } catch (Exception e) {
            ReplyContent reply = new ReplyContent();
            reply.code = 400;
            reply.bytes = e.toString().getBytes();
            return reply;
        }
        return doGetMainVolume();
    }

    @NonNull
    private ReplyContent doCommandOnChannel(String pn, String pv) {
        ReplyContent reply = new ReplyContent();
        try {
            int channel;
            float volume= (float) 0.0;
            if (pn.equals("setVolume")) {
                String[] pvs = pv.split(",");
                if (pvs.length != 2) throw new Exception("Wrong request");
                channel = Integer.valueOf(pvs[0]);
                volume = Float.valueOf(pvs[1]);
            } else {
                channel=Integer.valueOf(pv);
            }
            if (channel<0 || channel>=mplayers.length) throw new Exception("Wrong channel number");
            MediaPlayerExt mplayer = getMediaPlayer(channel);
            switch (pn) {
                case "stop":
                    mplayer.stop();
                    break;
                case "pause":
                    mplayer.pause();
                    break;
                case "continue":
                    mplayer.start();
                    break;
                case "setVolume":
                    mplayer.setVolume(volume,volume);
                    break;
                case "getVolume":
                    throw new Exception("Not implemented");
            }
            reply.bytes = "Activity started".getBytes();
        } catch (Exception e) {
            reply.code = 500;
            reply.bytes = e.toString().getBytes();
        }
        return reply;
    }

    private MediaPlayerExt getMediaPlayer(int channel) {
        MediaPlayerExt mplayer = mplayers[channel];
        if (mplayer==null) {
            mplayer=mplayers[channel]=new MediaPlayerExt();
        }
        return mplayer;
    }

    SurfaceHolder surfaceHolder=null;
    boolean surfaceCreated=false;
    MediaPlayerExt awaitingForSurface=null;
    FullscreenActivity.SurfaceHolderEventListener surfaceHolderEventListener= new FullscreenActivity.SurfaceHolderEventListener() {
        @Override
        public void onAvailable(SurfaceHolder sh) {
            synchronized (SimpleWebServer.this) {
                // if (surfaceHolder != sh) {
                    surfaceHolder = sh;
                    surfaceHolder.addCallback(surfaceEventListener);
                // }
            }
        }
        @Override
        public void onDestroyed() {
            synchronized (SimpleWebServer.this) {
                if (surfaceHolder!=null) {
                    surfaceHolder.removeCallback(surfaceEventListener);
                    surfaceHolder = null;
                    surfaceCreated=false;
                    if (awaitingForSurface != null) {
                        awaitingForSurface.setDisplay(null);
                    }
                }
            }
        }
    };
    SurfaceHolder.Callback surfaceEventListener=new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            synchronized (SimpleWebServer.this) {
                surfaceCreated=true;
                if (awaitingForSurface != null) {
                    awaitingForSurface.setDisplay(surfaceHolder);
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            synchronized (SimpleWebServer.this) {
                surfaceCreated=true;
                if (awaitingForSurface != null) {
                    awaitingForSurface.setDisplay(surfaceHolder);
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surfaceCreated=false;
            if (awaitingForSurface != null) {
                awaitingForSurface.setDisplay(null);
            }
        }
    };

    void assignSurface(MediaPlayerExt mp) {
        mhandler.post(new Runnable() {
            @Override
            public void run() {
                FullscreenActivity.getSurfaceHolder(mcontext,surfaceHolderEventListener);
            }
        });
        synchronized (SimpleWebServer.this) {
            if (awaitingForSurface==mp) return;
            if (awaitingForSurface != null) {
                awaitingForSurface.setDisplay(null);
                awaitingForSurface = null;
            }
            awaitingForSurface = mp;
            if (surfaceHolder!=null && surfaceCreated) {
                awaitingForSurface.setDisplay(surfaceHolder);
            } else {
                awaitingForSurface.setDisplay(null);
            }
        }
    }

    @NonNull
    private ReplyContent doPlayFile(String pv) {
        ReplyContent reply = new ReplyContent();
        try {
            String[] pvs = pv.split(",");
            if (pvs.length != 2) throw new Exception("Wrong request");
            int channel = Integer.valueOf(pvs[0]);
            int fileIndex = Integer.valueOf(pvs[1]);
            if (cached_filelist==null) {
                String err = buid_filelist();
                if (err != null) throw new Exception(err);
                if (cached_filelist == null) throw new Exception("Can't build list of files");
            }
            if (fileIndex< 0 || fileIndex>= cached_filelist.length) throw new Exception("Wrong index value");
            File fileToPlay = cached_filelist[fileIndex];
            final MediaPlayerExt mplayer = getMediaPlayer(channel);
            mplayer.reset();
            String filename = fileToPlay.toString();
            mplayer.setSourceFile(filename);
            if (filename.endsWith(".mp4") || filename.endsWith("jpg") || filename.endsWith("png") || filename.endsWith("bmp")) {
                assignSurface(mplayer);
            }
            mplayer.prepare();
            mplayer.start();
        } catch (Exception e) {
            reply.code = 400;
            reply.bytes = e.toString().getBytes();
        }
        return reply;
    }

    private ReplyContent getFileList(String pv) {
        ReplyContent reply = new ReplyContent();
        String err = buid_filelist();
        if (err!=null) {
            reply.code=404;
            reply.bytes=err.getBytes();
            return reply;
        }
        if (cached_filelist==null) {
            reply.code=500;
            reply.bytes="Can't build list of files".getBytes();
            return reply;
        }
        try {
            String[] files=new String[cached_filelist.length];
            for (int i = 0; i < files.length; i++) {
                files[i]=cached_filelist[i].getName();
            }
            JSONArray fl=new JSONArray(files);
            if (pv.equals("")) {
                reply.contentType="application/json; charset=utf-8";
                reply.bytes = fl.toString().getBytes();
            } else {
                reply.contentType="application/javascript; charset=utf-8";
                reply.bytes = (pv+"("+fl.toString()+")").getBytes();
            }
            return reply;
        } catch (JSONException e) {
            reply.code=500;
            reply.bytes=e.toString().getBytes();
            return reply;
        }
    }

    private ReplyContent loadFile(String filename) throws IOException {
        ReplyContent reply=new ReplyContent();

        FileReader input = null;
        String filename1 = filename.replaceAll("[^A-Za-z0-9\\-_.]", "");
        if (Objects.equals(filename1, ".") || Objects.equals(filename1, "..")) {
            reply.code=400;
            reply.bytes="Wrong filename".getBytes();
        }
        filename1=mrootdir+'/'+filename1;
        try {
            File sf=new File(filename1);
            long len = sf.length();
            if (len<0 || len>100000) {
                reply.code=500;
                reply.bytes="Too big file".getBytes();
                return reply;
            }
            input = new FileReader(sf);
            char[] buffer = new char[(int) len];
            int actlen=input.read(buffer);
            input.close();
            reply.bytes=new String(buffer,0,actlen).getBytes();
            reply.contentType=detectMimeType(filename1);
            return reply;
        } catch (FileNotFoundException e) {
            reply.code=404;
            reply.bytes="Not found".getBytes();
            return reply;
        } finally {
            if (null != input) {
                input.close();
            }
        }
    }

    /**
     * Detects the MIME type from the {@code fileName}.
     *
     * @param fileName The name of the file.
     * @return A MIME type.
     */
    private String detectMimeType(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return null;
        } else if (fileName.endsWith(".html")) {
            return "text/html";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else {
            return "application/octet-stream";
        }
    }

}