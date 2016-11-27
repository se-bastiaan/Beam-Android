package com.github.se_bastiaan.beam.control.client;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;

import com.github.se_bastiaan.beam.MediaData;
import com.github.se_bastiaan.beam.control.ControlClient;
import com.github.se_bastiaan.beam.control.ControlClientListener;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.device.GoogleCastDevice;
import com.github.se_bastiaan.beam.logger.Logger;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.LaunchOptions;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.WebImage;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

public class GoogleCastControlClient implements ControlClient {

    private final String TAG = getClass().getCanonicalName();

    private CopyOnWriteArrayList<ControlClientListener> clientListeners;

    private Context context;

    private MediaRouter mediaRouter;
    private GoogleApiClient googleApiClient;
    private RemoteMediaPlayer remoteMediaPlayer;
    private boolean waitingForReconnect = false;

    private Timer timer;

    private GoogleCastDevice currentDevice;

    private GoogleApiClient.ConnectionCallbacks connectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(Bundle bundle) {
            Logger.d(TAG, "Connected " + bundle);
            if (waitingForReconnect) {
                waitingForReconnect = false;
                reconnectChannels();
            } else {
                for (ControlClientListener listener : clientListeners) {
                    listener.onConnected(GoogleCastControlClient.this, currentDevice);
                }
                try {
                    LaunchOptions launchOptions = new LaunchOptions.Builder().setRelaunchIfRunning(false).build();
                    Cast.CastApi.launchApplication(googleApiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, launchOptions)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {
                                                reconnectChannels();
                                            }
                                        }
                                    });

                    attachMediaPlayer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            waitingForReconnect = true;
            detachMediaPlayer();
        }
    };

    private GoogleApiClient.OnConnectionFailedListener connectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            detachMediaPlayer();
            waitingForReconnect = false;
            googleApiClient = null;
            currentDevice = null;
        }
    };


    private Cast.Listener castListener = new Cast.Listener() {
        @Override
        public void onVolumeChanged() {
            for (ControlClientListener listener : clientListeners) {
                listener.onVolumeChanged(GoogleCastControlClient.this, Cast.CastApi.getVolume(googleApiClient), Cast.CastApi.isMute(googleApiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int i) {
            super.onApplicationDisconnected(i);
            disconnect();
        }

        @Override
        public void onApplicationStatusChanged() {
            if (googleApiClient != null) {
                Logger.d(TAG, "onApplicationStatusChanged: "
                        + Cast.CastApi.getApplicationStatus(googleApiClient));
            }

        }
    };

    private MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteSelected(router, route);

            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(currentDevice.getCastDevice(), castListener);


            googleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(connectionCallbacks)
                    .addOnConnectionFailedListener(connectionFailedListener)
                    .build();

            googleApiClient.connect();

        }
    };

    public GoogleCastControlClient(Context context) {
        this.context = context.getApplicationContext();
        clientListeners = new CopyOnWriteArrayList<>();

        MediaRouteSelector mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build();
        mediaRouter = MediaRouter.getInstance(this.context);
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback);
    }

    @Override
    public boolean canHandleDevice(BeamDevice device) {
        return device.getClass() == GoogleCastDevice.class;
    }

    @Override
    public void loadMedia(MediaData info) {
        if(currentDevice != null && googleApiClient != null && googleApiClient.isConnected()) {
            MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

            if (info.title != null) {
                mediaMetadata.putString(MediaMetadata.KEY_TITLE, info.title);
            }

            if (info.image != null) {
                mediaMetadata.addImage(new WebImage(Uri.parse(info.image)));
            }

            MediaInfo mediaInfo = new MediaInfo.Builder(info.videoLocation)
                    .setContentType("video/mp4")
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(mediaMetadata)
                    .build();

            try {
                remoteMediaPlayer.load(googleApiClient, mediaInfo, true)
                        .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(@NonNull RemoteMediaPlayer.MediaChannelResult result) {
                                if (result.getStatus().isSuccess()) {
                                    Logger.d(TAG, "Playback loaded successfully");
                                }
                            }
                        });
            } catch (IllegalStateException e) {
                Logger.e(TAG, "Problem occurred with playback during loading", e);
            } catch (Exception e) {
                Logger.e(TAG, "Problem opening playback during loading", e);
            }
        }

    }

    @Override
    public void connect(BeamDevice device) {
        if (currentDevice != device) {
            try {
                disconnect();
            } catch (IllegalStateException e) {
                // Thrown when no MediaClient is available, safe to ignore
            }
        }

        currentDevice = (GoogleCastDevice) device;
        mediaRouter.selectRoute(currentDevice.routeInfo);
    }

    @Override
    public void disconnect() {
        if (currentDevice == null || googleApiClient == null || !googleApiClient.isConnected()) {
            throw new IllegalStateException("Not connected");
        }

        stop();
        waitingForReconnect = false;
        Cast.CastApi.leaveApplication(googleApiClient);
        googleApiClient.disconnect();
        googleApiClient = null;
        currentDevice = null;
        mediaRouter.selectRoute(mediaRouter.getDefaultRoute());

        for (ControlClientListener listener : clientListeners) {
            listener.onDisconnected(this);
        }
    }

    @Override
    public void play() {
        if (currentDevice == null || googleApiClient == null || !googleApiClient.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        remoteMediaPlayer.play(googleApiClient);
    }

    @Override
    public void pause() {
        if (currentDevice == null || googleApiClient == null || !googleApiClient.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        remoteMediaPlayer.pause(googleApiClient);
    }

    @Override
    public void seek(long position) {
        if (currentDevice == null || googleApiClient == null || !googleApiClient.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        remoteMediaPlayer.seek(googleApiClient, position);
    }

    @Override
    public void stop() {
        stopTimer();
        if (currentDevice == null || googleApiClient == null || !googleApiClient.isConnected()) {
            throw new IllegalStateException("Not connected");
        }

        if(currentDevice != null && googleApiClient != null && googleApiClient.isConnected()) {
            try {
                remoteMediaPlayer.stop(googleApiClient);
            } catch (IllegalStateException e) {
                // Not able to stop because there was nothing playing. Just leave it.
            }
        }

    }

    @Override
    public void setVolume(float volume) {
        if (currentDevice == null || googleApiClient == null || !googleApiClient.isConnected()) {
            throw new IllegalStateException("Not connected");
        }

        try {
            if (volume == 0) {
                Cast.CastApi.setMute(googleApiClient, true);
            } else {
                Cast.CastApi.setMute(googleApiClient, false);
                Cast.CastApi.setVolume(googleApiClient, volume);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean canControlVolume() {
        return currentDevice != null && currentDevice.routeInfo.getVolumeHandling() == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    }

    @Override
    public void addListener(ControlClientListener listener) {
        clientListeners.add(listener);
    }

    @Override
    public void removeListener(ControlClientListener listener) {
        clientListeners.remove(listener);
    }

    private void attachMediaPlayer() {
        if (remoteMediaPlayer != null) {
            return;
        }

        remoteMediaPlayer = new RemoteMediaPlayer();
        remoteMediaPlayer.setOnStatusUpdatedListener(new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                MediaStatus mediaStatus = remoteMediaPlayer.getMediaStatus();
                if(mediaStatus != null) {
                    boolean isPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;

                    if (isPlaying) {
                        startTimer();
                    } else {
                        stopTimer();
                    }

                    for (ControlClientListener listener : clientListeners) {
                        listener.onPlayBackChanged(GoogleCastControlClient.this, isPlaying, remoteMediaPlayer.getApproximateStreamPosition(), remoteMediaPlayer.getStreamDuration());
                    }
                }
            }
        });

        if (googleApiClient != null) {
            try {
                Cast.CastApi.setMessageReceivedCallbacks(googleApiClient, remoteMediaPlayer.getNamespace(), remoteMediaPlayer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void detachMediaPlayer() {
        if (remoteMediaPlayer != null && googleApiClient != null) {
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(googleApiClient, remoteMediaPlayer.getNamespace());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        stopTimer();
        remoteMediaPlayer = null;
    }

    private void reconnectChannels() {
        try {
            Cast.CastApi.setMessageReceivedCallbacks(googleApiClient, remoteMediaPlayer.getNamespace(), remoteMediaPlayer);
            remoteMediaPlayer.requestStatus(googleApiClient);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                boolean isPlaying = remoteMediaPlayer.getMediaStatus().getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
                for (ControlClientListener listener : clientListeners) {
                    listener.onPlayBackChanged(GoogleCastControlClient.this, isPlaying, remoteMediaPlayer.getApproximateStreamPosition(), remoteMediaPlayer.getStreamDuration());
                }
            }
        }, PLAYBACK_POLL_INTERVAL, PLAYBACK_POLL_INTERVAL);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

}
