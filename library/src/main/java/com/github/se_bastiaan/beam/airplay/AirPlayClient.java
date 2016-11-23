package com.github.se_bastiaan.beam.airplay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.widget.EditText;

import com.github.se_bastiaan.beam.BaseBeamClient;
import com.github.se_bastiaan.beam.BeamDevice;
import com.github.se_bastiaan.beam.BeamListener;
import com.github.se_bastiaan.beam.airplay.plist.PropertyListBuilder;
import com.github.se_bastiaan.beam.airplay.plist.PropertyListParser;
import com.github.se_bastiaan.beam.logger.Logger;
import com.github.se_bastiaan.beam.model.Playback;
import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import static org.fourthline.cling.binding.xml.Descriptor.Device.ELEMENT.url;

/**
 * AirPlayClient.java
 *
 * CastingClient for AirPlay. Using {@link com.github.se_bastiaan.beam.airplay.AirPlayDiscovery} to discover devices.
 */
public class AirPlayClient extends BaseBeamClient implements ServiceListener {

    private final String TAG = getClass().getCanonicalName();
    private static final String USER_AGENT = "MediaControl/1.0";
    private static final MediaType TYPE_PARAMETERS = MediaType.parse("text/parameters");
    private static final String AUTH_USERNAME = "Airplay";
    private static final long KEEP_ALIVE_PERIOD = 15000;

    private Map<String, ServiceInfo> discoveredServices = new HashMap<String, ServiceInfo>();

    private Context context;
    private AirPlayDiscovery discovery;
    private Handler pingHandler;
    private Handler handler;
    private BeamListener listener;
    private OkHttpClient httpClient = new OkHttpClient();
    private AirPlayDevice currentDevice;
    private Timer timer;
    private String currentState = "stopped", sessionId = null, password = null;

    public AirPlayClient(Context context, BeamListener listener) {
        this.listener = listener;
        handler = new Handler(context.getApplicationContext().getMainLooper());
        pingHandler = new Handler(context.getApplicationContext().getMainLooper());
        discovery = AirPlayDiscovery.getInstance(context, this);
        discovery.start();

        httpClient.setAuthenticator(new Authenticator() {
            @Override
            public Request authenticate(Proxy proxy, Response response) throws IOException {
                if (response.request().header("Authorization") != null && (password == null || password.isEmpty())) {
                    openAuthorizationDialog();
                }

                String responseHeader = response.header("WWW-Authenticate");
                Map<String, String> params = getAuthParams(responseHeader);
                String credentials = makeAuthorizationHeader(params, response.request().method(), response.request().uri().toString());
                return response.request().newBuilder().header("Authorization", credentials).build();
            }

            @Override
            public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
                return null; // not needed
            }
        });
    }

    /**
     * Call on close
     */
    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        discovery.stop();
    }

    /**
     * @param context New context for future use
     */
    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * @return {@link com.github.se_bastiaan.beam.airplay.AirPlayDiscovery} used by this client
     */
    public AirPlayDiscovery getDiscovery() {
        return discovery;
    }

    /**
     * @param device Device to connect to
     */
    public void connect(BeamDevice device) {
        if (currentDevice != device) {
            disconnect();
        }
        currentDevice = (AirPlayDevice) device;
        Logger.d(TAG, String.format("Connecting to airplay device: %s - %s", device.getId(), currentDevice.getIpAddress().toString()));

        sessionId = UUID.randomUUID().toString();
        Logger.d(TAG, String.format("Session ID: %s", sessionId));

        listener.onDeviceSelected(currentDevice);
        startTimer();
    }

    /**
     * Disconnect from device and stop all runnables
     */
    public void disconnect() {
        if(currentDevice == null) return;

        stop();
        listener.onDisconnected();
    }

    /**
     * Load playback to device and start playing
     * @param playback Playback object used for MetaData
     * @param location Location of playback that is supposed to be played
     * @param position Start position of playback
     */
    @Override
    public void loadMedia(Playback playback, String location, float position) {
        if(currentDevice == null) return;
        Logger.d(TAG, String.format("Session ID: %s", sessionId));

        stop();

        PropertyListBuilder builder = new PropertyListBuilder();
        builder.putString("Content-Location", location);
        builder.putReal("Start-Position", 0);

        RequestBody body = RequestBody.create(TYPE_PARAMETERS, builder.toString());

        Request playRequest = requestBuilder("play")
                .addHeader("X-Apple-AssetKey", UUID.randomUUID().toString())
                .post(body)
                .build();

        startTimer();

        httpClient.newCall(playRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                e.printStackTrace();
                Logger.d(TAG, String.format("Failed to load playback: %s", e.getMessage()));
                listener.onCommandFailed("play", e.getMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                String body = response.body().string();
                Logger.d(TAG, String.format("Load playback response: %s", body));
                if (response.isSuccessful()) {
                    Logger.d(TAG, "Load playback successful");
                } else {
                    Logger.d(TAG, "Failed to play playback");
                    listener.onCommandFailed("play", "Failed to play playback");
                }
            }
        });
    }

    /**
     * Pause playback when paused
     */
    @Override
    public void play() {
        if (currentState.equals("paused")) {
            Request playRequest = requestBuilder("rate?value=1.000000")
                    .build();

            httpClient.newCall(playRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    // Ignore, playback info will be obtained and so will the result
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    // Ignore, playback info will be obtained and so will the result
                }
            });
        }
    }

    /**
     * Pause playback when playing
     */
    @Override
    public void pause() {
        if (currentState.equals("playing")) {
            Request pauseRequest = requestBuilder("rate?value=0.000000")
                    .build();

            httpClient.newCall(pauseRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    // Ignore, playback info will be obtained and so will the result
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    // Ignore, playback info will be obtained and so will the result
                }
            });
        }
    }

    /**
     * Seek to position in playback
     * @param position Relative seek position
     */
    @Override
    public void seek(float position) {
        Request playRequest = requestBuilder("scrub?position=" + position)
                .build();

        httpClient.newCall(playRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                // Ignore, playback info will be obtained and so will the result
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // Ignore, playback info will be obtained and so will the result
            }
        });
    }

    /**
     * Completely stop playback
     */
    @Override
    public void stop() {
        Request stopRequest = requestBuilder("stop").build();

        httpClient.newCall(stopRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                listener.onCommandFailed("stop", e.getMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onCommandFailed("stop", "Cannot stop");
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

    private Request.Builder requestBuilder(String method) {
        Request.Builder builder = new Request.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .url(currentDevice.getUrl() + method);

        if (sessionId != null) {
            builder.addHeader("X-Apple-Session-ID", sessionId);
        }

        return builder;
    }

    @Override
    public void serviceAdded(final ServiceEvent event) {
        Logger.d(TAG, "Found AirPlay service: " + event.getName());
        discoveredServices.put(event.getInfo().getKey(), event.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        Logger.d(TAG, "Removed AirPlay service: " + event.getName());
        discoveredServices.remove(event.getInfo().getKey());
        AirPlayDevice removedDevice = new AirPlayDevice(event.getInfo());
        listener.onDeviceRemoved(removedDevice);
        if (currentDevice != null && currentDevice.getId().equals(removedDevice.getId())) {
            stopTimer();
            listener.onDisconnected();
            currentDevice = null;
        }
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        Logger.d(TAG, "Resolved AirPlay service: " + event.getName() + " @ " + event.getInfo().getURL());
        AirPlayDevice device = new AirPlayDevice(event.getInfo());
        listener.onDeviceDetected(device);
        discoveredServices.put(event.getInfo().getKey(), event.getInfo());
    }

    /**
     * Open Dialog to enter password or pin used for AirPlay
     */
    public void openAuthorizationDialog() {
        final AtomicBoolean wait = new AtomicBoolean(true);

        handler.post(new Runnable() {
            @Override
            public void run() {
                // TODO: better layout
                final EditText editText = new EditText(context);

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context)
                        .setTitle("Enter pincode")
                        .setView(editText)
                        .setPositiveButton("Enter", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                password = editText.getText().toString();
                                wait.set(false);
                                dialog.dismiss();
                            }
                        });

                dialogBuilder.show();
            }
        });

        while (wait.get()) {
            // Block network thread to wait for input
        }
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
                Request infoRequest = requestBuilder("playback-info").build();

                httpClient.newCall(infoRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        listener.onCommandFailed("playback-info", e.getMessage());
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        if (response.isSuccessful()) {
                            try {

                                Map<String, Object> rootDict = PropertyListParser.parse(response.body().string());
                                if(rootDict.containsKey("position")) {
                                    float position = (Float) rootDict.get("position");
                                    float duration = (Float) rootDict.get("duration");

                                    float rate = (Float) rootDict.get("rate");
                                    boolean playing = false;
                                    if (rate > 0) {
                                        playing = true;
                                    }
                                    boolean readyToPlay = false;
                                    if (rootDict.containsKey("readyToPlay")) {
                                        readyToPlay = (Boolean) rootDict.get("readyToPlay");
                                    }

                                    Logger.d(TAG, "PlaybackInfo: playing: " + playing + ", rate: " + rate + ", position: " + position + ", ready: " + readyToPlay);

                                    if (readyToPlay) {
                                        listener.onReady();
                                    }

                                    listener.onPlayBackChanged(playing, position);

                                    if (position == duration) return;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            listener.onCommandFailed("playback-info", "Cannot get playback info");
                        }
                    }
                });
            }
        }, KEEP_ALIVE_PERIOD, KEEP_ALIVE_PERIOD);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

}
