package net.andrey_zabrodin.showserver;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;

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
    private MediaPlayer mplayer;
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
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
        } finally {
            if (mplayer!=null) {
                mplayer.reset();
                mplayer.release();
                mplayer=null;
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
    private void handle(Socket socket) throws IOException {
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

            ReplyContent reply = loadContent(route);
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
    private ReplyContent loadContent(String path) throws IOException {
        if (path.startsWith("?")) {
            int eqi = path.indexOf('=');
            String pn, pv;
            if (eqi<0) {
                pn = path.substring(1);
                pv = "";
            } else {
                pn = path.substring(1, eqi);
                pv = URLDecoder.decode(path.substring(eqi + 1), "UTF-8");
            }
            switch (pn) {
                case "mfilelist":
                    return getFileList(pv);

                case "pauseVideo": {
                    ReplyContent reply = new ReplyContent();
                    try {
                        final Intent intent = new Intent();
                        intent.setDataAndType(null, "pauseVideo");
                        mhandler.post(new Runnable() {
                            @Override
                            public void run() {
                                FullscreenActivity.activateFullScreenActiity(mcontext,intent);
                            }
                        });
                    } catch (Exception e) {
                        reply.code = 500;
                        reply.bytes = e.toString().getBytes();
                    }
                    return reply;
                }
                case "continueVideo":{
                    ReplyContent reply = new ReplyContent();
                    try {
                        final Intent intent = new Intent();
                        intent.setDataAndType(null, "continueVideo");
                        mhandler.post(new Runnable() {
                            @Override
                            public void run() {
                                FullscreenActivity.activateFullScreenActiity(mcontext,intent);
                            }
                        });
                    } catch (Exception e) {
                        reply.code = 500;
                        reply.bytes = e.toString().getBytes();
                    }
                    return reply;
                }
                case "stopVideo":
                    return onPlayMediaRequest("video","");
                case "pauseAudio":{
                    ReplyContent reply=new ReplyContent();
                    if (mplayer != null) {
                        try {
                            mplayer.pause();
                        } catch (Exception e) {
                            reply.code=400;
                            reply.bytes=e.toString().getBytes();
                        }
                    }
                    return reply;
                }
                case "continueAudio": {
                    ReplyContent reply=new ReplyContent();
                    if (mplayer != null) {
                        try {
                            mplayer.start();
                        } catch (Exception e) {
                            reply.code=400;
                            reply.bytes=e.toString().getBytes();
                        }
                    }
                    return reply;
                }
                case "stopAudio":
                    return onPlayMediaRequest("audio","");
                case "volumeAudio": {
                    ReplyContent reply=new ReplyContent();
                    if (mplayer != null) {
                        try {
                            Float v = Float.valueOf(pv);
                            mplayer.setVolume(v, v);
                        } catch (Exception e) {
                            reply.code=400;
                            reply.bytes=e.toString().getBytes();
                        }
                    }
                    return reply;
                }
                case "volumeVideo":{
                    ReplyContent reply = new ReplyContent();
                    reply.code=500;
                    reply.bytes="Not implemented".getBytes();
                    return reply;
                }
                case "volume": {
                    ReplyContent reply = new ReplyContent();
                    try {
                        if (audioManager == null) audioManager = (AudioManager) mcontext.getSystemService(Context.AUDIO_SERVICE);
                        Float v = Float.valueOf(pv);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*v),FLAG_SHOW_UI);
                    } catch (Exception e) {
                        reply.code = 400;
                        reply.bytes = e.toString().getBytes();
                    }
                    return reply;
                }

                case "video":
                case "audio":
                case "image":
                    return onPlayMediaRequest(pn,pv);
                default: {
                    ReplyContent reply = new ReplyContent();
                    reply.code=400;
                    reply.bytes="Wrong media type".getBytes();
                    return reply;
                }
            }
        } else {
            if (path.equals("")) path="index.html";
            return loadFile(path);
        }
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

    private ReplyContent onPlayMediaRequest(String action, String fileIndex) {
        ReplyContent reply=new ReplyContent();
        if (cached_filelist==null) {
            String err = buid_filelist();
            if (err != null) {
                reply.code = 404;
                reply.bytes = err.getBytes();
                return reply;
            }
            if (cached_filelist == null) {
                reply.code = 500;
                reply.bytes = "Can't build list of files".getBytes();
                return reply;
            }
        }

        File fileToPlay=null;
        if (!fileIndex.equals("")) {
            Integer fi;
            try {
                fi = Integer.valueOf(fileIndex);
            } catch (NumberFormatException e) {
                reply.code = 400;
                reply.bytes = e.toString().getBytes();
                return reply;
            }
            if (fi < 0 || fi >= cached_filelist.length) {
                reply.code = 404;
                reply.bytes = "Wrong index value".getBytes();
                return reply;
            }
            fileToPlay = cached_filelist[fi];
        }

        switch (action) {
            case "image":
            case "video":
                try {
                    final Intent intent = new Intent();
                    if (fileToPlay!=null) {
                        intent.setDataAndType(Uri.fromFile(fileToPlay), action);
                    } else {
                        intent.setDataAndType(null, action);
                    }
                    mhandler.post(new Runnable() {
                        @Override
                        public void run() {
                            FullscreenActivity.activateFullScreenActiity(mcontext,intent);
                        }
                    });
                    reply.bytes = "Activity started".getBytes();
                } catch (Exception e) {
                    reply.code = 500;
                    reply.bytes = e.toString().getBytes();
                }
                break;
            case "audio" : {
                if (mplayer==null) mplayer=new MediaPlayer();
                mplayer.reset();
                if (fileToPlay!=null) {
                    try {
                        mplayer.setDataSource(mcontext, Uri.fromFile(fileToPlay));
                        mplayer.prepare();
                        mplayer.start();
                    } catch (IOException e) {
                        reply.code = 404;
                        reply.bytes = e.toString().getBytes();
                        return reply;
                    }
                }
                reply.bytes = "Activity started".getBytes();
                break;
            }
            default:
                reply.code=400;
                reply.bytes="Wrong action".getBytes();
                return reply;
        }

        return reply;
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