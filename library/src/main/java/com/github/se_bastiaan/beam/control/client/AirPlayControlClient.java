package com.github.se_bastiaan.beam.control.client;

import android.content.Context;

import com.github.se_bastiaan.beam.MediaData;
import com.github.se_bastiaan.beam.control.ControlClient;
import com.github.se_bastiaan.beam.control.ControlClientListener;
import com.github.se_bastiaan.beam.control.airplay.PropertyListParser;
import com.github.se_bastiaan.beam.device.AirPlayDevice;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.logger.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

public class AirPlayControlClient implements ControlClient {

    private final String TAG = getClass().getCanonicalName();
    private static final String USER_AGENT = "MediaControl/1.0";
    private static final MediaType TYPE_PARAMETERS = MediaType.parse("text/parameters");
    private static final String AUTH_USERNAME = "Airplay";

    private CopyOnWriteArrayList<ControlClientListener> clientListeners;

    private OkHttpClient httpClient;

    private AirPlayDevice currentDevice;
    private Timer timer;
    private String sessionId = null, password = null;

    public AirPlayControlClient(Context context) {
        clientListeners = new CopyOnWriteArrayList<>();

        httpClient = new OkHttpClient.Builder()
                .authenticator(new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        if (response.request().header("Authorization") != null && (password == null || password.isEmpty())) {
                            // TODO: Ask for password
                        }

                        String responseHeader = response.header("WWW-Authenticate");
                        Map<String, String> params = getAuthParams(responseHeader);
                        String credentials = makeAuthorizationHeader(params, response.request().method(), response.request().url().uri().toString());
                        return response.request().newBuilder().header("Authorization", credentials).build();
                    }
                }).build();
    }

    @Override
    public boolean canHandleDevice(BeamDevice device) {
        return device.getClass() == AirPlayDevice.class;
    }

    @Override
    public void loadMedia(MediaData mediaData) {
        if(currentDevice == null) {
            return;
        }
        Logger.d(TAG, String.format("Session ID: %s", sessionId));

        stop();

        String content = "Content-Location: " + mediaData.videoLocation + "\nStart-Position: 0\n";

        RequestBody body = RequestBody.create(TYPE_PARAMETERS, content);

        Request playRequest = requestBuilder("play")
                .post(body)
                .build();

        httpClient.newCall(playRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, String.format("Failed to load mediaData: %s", e.getMessage()));
//                listener.onCommandFailed("play", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Logger.d(TAG, String.format("Load mediaData response: %s", body));
                if (response.isSuccessful()) {
                    startTimer();
                    Logger.d(TAG, "Load mediaData successful");
                } else {
                    Logger.d(TAG, "Failed to play mediaData");
//                    listener.onCommandFailed("play", "Failed to play mediaData");
                }
            }
        });
    }

    @Override
    public void connect(BeamDevice device) {
        if (!(device instanceof AirPlayDevice)) {
            throw new Error("Provided device is not of the right type");
        }

        currentDevice = (AirPlayDevice) device;
        sessionId = UUID.randomUUID().toString();

        for (ControlClientListener listener : clientListeners) {
            listener.onConnected(this, currentDevice);
        }
    }

    @Override
    public void disconnect() {
        stopTimer();
        stop();
        currentDevice = null;

        for (ControlClientListener listener : clientListeners) {
            listener.onDisconnected(this);
        }
    }

    @Override
    public void play() {
        Request playRequest = requestBuilder("rate?value=1.000000")
                .post(getEmptyRequestBody())
                .build();

        httpClient.newCall(playRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in play request");
                // Ignore, playback info will be obtained and so will the result
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful play request");
                // Ignore, playback info will be obtained and so will the result
            }
        });
    }

    @Override
    public void pause() {
        Request pauseRequest = requestBuilder("rate?value=0.000000")
                .post(getEmptyRequestBody())
                .build();

        httpClient.newCall(pauseRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in pause request");
                // Ignore, playback info will be obtained and so will the result
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful pause request");
                // Ignore, playback info will be obtained and so will the result
            }
        });
    }

    /**
     * Seek to position in playback
     * @param position Relative seek position
     */
    @Override
    public void seek(long position) {
        float positionInSeconds = (float) position / 1000f;
        Request playRequest = requestBuilder("scrub?position=" + positionInSeconds)
                .post(getEmptyRequestBody())
                .build();

        httpClient.newCall(playRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in seek request");
                // Ignore, playback info will be obtained and so will the result
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful seek request");
                // Ignore, playback info will be obtained and so will the result
            }
        });
    }

    @Override
    public void stop() {
        Request stopRequest = requestBuilder("stop")
                .post(getEmptyRequestBody())
                .build();

        httpClient.newCall(stopRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.d(TAG, "Failure in stop request");
                //listener.onCommandFailed("stop", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Logger.d(TAG, "Successful stop request");
                if (!response.isSuccessful()) {
                    //listener.onCommandFailed("stop", "Cannot stop");
                } else {
                    stopTimer();
                }
            }
        });
    }

    @Override
    public void setVolume(float volume) {
        // Can't control volume (yet), so do nothing
    }

    @Override
    public boolean canControlVolume() {
        return false;
    }

    @Override
    public void addListener(ControlClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(ControlClientListener listener) {
        clientListeners.remove(listener);
    }

    /**
     * Generate MD5 for Digest Authentication
     * @param input {@link java.lang.String}
     * @return {@link java.lang.String}
     */
    private String md5Digest(String input) {
        byte[] source;
        try {
            source = input.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            source = input.getBytes();
        }

        String result = null;
        char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(source);

            byte temp[] = md.digest();
            char str[] = new char[16 * 2];
            int k = 0;
            for (int i = 0; i < 16; i++) {
                byte byte0 = temp[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }

            result = new String(str);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Make Authorization header for HTTP request
     * @param params {@link java.util.Map}
     * @param method {@link java.lang.String}
     * @param uri {@link java.lang.String}
     * @return {@link java.lang.String}
     */
    private String makeAuthorizationHeader(Map params, String method, String uri) {
        String realm = (String) params.get("realm");
        String nonce = (String) params.get("nonce");
        String ha1 = md5Digest(AUTH_USERNAME + ":" + realm + ":" + password);
        String ha2 = md5Digest(method + ":" + uri);
        String response = md5Digest(ha1 + ":" + nonce + ":" + ha2);
        return "Digest username=\"" + AUTH_USERNAME + "\", "
                + "realm=\"" + realm + "\", "
                + "nonce=\"" + nonce + "\", "
                + "uri=\"" + uri + "\", "
                + "response=\"" + response + "\"";
    }

    private Map<String, String> getAuthParams(String authString) {
        Map<String, String> params = new HashMap<>();
        int firstSpace = authString.indexOf(' ');
        String rest = authString.substring(firstSpace + 1).replaceAll("\r\n", " ");
        String[] lines = rest.split("\", ");
        for (int i = 0; i < lines.length; i++) {
            int split = lines[i].indexOf("=\"");
            String key = lines[i].substring(0, split);
            String value = lines[i].substring(split + 2);
            if (value.charAt(value.length() - 1) == '"') {
                value = value.substring(0, value.length() - 1);
            }
            params.put(key, value);
        }
        return params;
    }

    private void getPlaybackInfo() {
        Request infoRequest = requestBuilder("playback-info").build();

        httpClient.newCall(infoRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //listener.onCommandFailed("playback-info", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {

                        Map<String, Object> rootDict = PropertyListParser.parse(response.body().string());
                        if(rootDict.containsKey("readyToPlay") && (boolean) rootDict.get("readyToPlay")) {
                            long position = (long) (((Double) rootDict.get("position")) * 1000);
                            long duration = (long) (((Double) rootDict.get("duration")) * 1000);

                            double rate = (Double) rootDict.get("rate");
                            boolean playing = false;
                            if (rate > 0) {
                                playing = true;
                            }

                            Logger.d(TAG, "PlaybackInfo: playing: " + playing + ", rate: " + rate + ", position: " + position + ", duration: " + duration);

                            for (ControlClientListener listener : clientListeners) {
                                listener.onPlayBackChanged(AirPlayControlClient.this, playing, position, duration);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
//                            for (ControlClientListener listener : clientListeners) {
//                                listener.onCommandFailed("playback-info", "Cannot get playback info");
//                            }
                }
            }
        });
    }

    /**
     * We send periodically a command to keep connection alive and for avoiding
     * stopping media session
     *
     * Fix for https://github.com/ConnectSDK/Connect-SDK-Cordova-Plugin/issues/5
     */
    private void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                getPlaybackInfo();
            }
        }, 0, PLAYBACK_POLL_INTERVAL);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

    private RequestBody getEmptyRequestBody() {
        return RequestBody.create(MediaType.parse(""), "");
    }

    private Request.Builder requestBuilder(String method) {
        Request.Builder builder = new Request.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .url(currentDevice.getUrl() + method);

        if (sessionId != null) {
            builder.addHeader("X-Apple-Session-ID", sessionId);
        }

        return builder;
    }

}
