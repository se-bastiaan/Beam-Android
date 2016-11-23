/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.se_bastiaan.beam.googlecast;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;

import com.github.se_bastiaan.beam.BaseBeamClient;
import com.github.se_bastiaan.beam.BeamDevice;
import com.github.se_bastiaan.beam.BeamListener;
import com.github.se_bastiaan.beam.logger.Logger;
import com.github.se_bastiaan.beam.model.Playback;
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
import java.util.HashSet;
import java.util.Set;

public class GoogleCastClient extends BaseBeamClient {
    
    private final String TAG = getClass().getCanonicalName();

    private final Set<MediaRouter.RouteInfo> mDiscoveredDevices = new HashSet<>();

    private Context context;
    private Handler handler;
    private BeamListener listener;
    private MediaRouter mediaRouter;
    private GoogleApiClient googleApiClient;
    private GoogleDevice currentDevice;
    private boolean waitingForReconnect = false, applicationStarted = false;
    private RemoteMediaPlayer remoteMediaPlayer;

    public GoogleCastClient(Context context, BeamListener listener) {
        this.context = context;
        this.listener = listener;
        handler = new Handler(context.getApplicationContext().getMainLooper());
        mediaRouter = MediaRouter.getInstance(context.getApplicationContext());

        MediaRouteSelector mediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent
                .DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build();
        mediaRouter.addCallback(mediaRouteSelector, mMediaRouterCallback,  MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

        remoteMediaPlayer = new RemoteMediaPlayer();
        remoteMediaPlayer.setOnStatusUpdatedListener(mMediaPlayerStatusListener);
    }

    /**
     * Call on close
     */
    public void destroy() {
        disconnect();
        handler.removeCallbacksAndMessages(null);
        mediaRouter.removeCallback(mMediaRouterCallback);
    }

    /**
     * @param context New context for future use
     */
    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public void loadMedia(Playback playback, String location, float position) {
        if(currentDevice != null && googleApiClient != null && googleApiClient.isConnected()) {
            MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
            mediaMetadata.putString(MediaMetadata.KEY_TITLE, playback.title);
            mediaMetadata.addImage(new WebImage(Uri.parse(playback.image)));
            MediaInfo mediaInfo = new MediaInfo.Builder(
                    location)
                    .setContentType("video/mp4")
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(mediaMetadata)
                    .build();
            try {
                remoteMediaPlayer.load(googleApiClient, mediaInfo, true)
                        .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
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
    public void play() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void seek(float position) {

    }

    @Override
    public void stop() {
        if(currentDevice != null && googleApiClient != null && googleApiClient.isConnected()) {
            try {
                remoteMediaPlayer.stop(googleApiClient);
            } catch (IllegalStateException e) {
                // Not able to stop because there was nothing playing. Just leave it.
            }
        }
    }

    /**
     * @param device Device to connect to
     */
    @Override
    public void connect(BeamDevice device) {
        if (currentDevice != device) {
            disconnect();
        }

        currentDevice = (GoogleDevice) device;
        mediaRouter.selectRoute(currentDevice.routeInfo);
        Logger.d(TAG, "Connecting to google cast device: " + device.getId() + " - " + currentDevice.getName());



        listener.onDeviceSelected(currentDevice);
        // initEvents();
    }

    @Override
    public void disconnect() {
        if (currentDevice != null && googleApiClient != null && googleApiClient.isConnected()) {
            try {
                Cast.CastApi.leaveApplication(googleApiClient);
                applicationStarted = false;
                googleApiClient.disconnect();
                googleApiClient = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        currentDevice = null;
        mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
        listener.onDisconnected();
    }

    @Override
    public void setVolume(float volume) {

    }

    @Override
    public boolean canControlVolume() {
        return currentDevice != null && currentDevice.routeInfo.getVolumeHandling() == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    }

    private void reconnectChannels() {
        try {
            Cast.CastApi.setMessageReceivedCallbacks(googleApiClient, remoteMediaPlayer.getNamespace(), remoteMediaPlayer);
            remoteMediaPlayer.requestStatus(googleApiClient);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private GoogleApiClient.ConnectionCallbacks mConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(Bundle bundle) {
            Logger.d(TAG, "Connected " + bundle);
            if (waitingForReconnect) {
                waitingForReconnect = false;
                reconnectChannels();
            } else {
                listener.onConnected(currentDevice);
                try {
                    LaunchOptions launchOptions = new LaunchOptions.Builder().setRelaunchIfRunning(false).build();
                    Cast.CastApi.launchApplication(googleApiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, launchOptions)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {
                                                applicationStarted = true;
                                                reconnectChannels();
                                            }
                                        }
                                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }
    };

    private GoogleApiClient.OnConnectionFailedListener mConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            listener.onConnectionFailed();
        }
    };

    private Cast.Listener mCastListener = new Cast.Listener() {
        @Override
        public void onVolumeChanged() {
            if (googleApiClient != null) {
                Logger.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(googleApiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
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

    private MediaRouter.Callback mMediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteSelected(router, route);
            currentDevice = new GoogleDevice(route);
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(currentDevice.getCastDevice(), mCastListener);

            googleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            googleApiClient.connect();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteUnselected(router, route);
            if (currentDevice != null && currentDevice.equals(new GoogleDevice(route))) {
                currentDevice = null;
                listener.onDisconnected();
            }
        }

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteAdded(router, route);
            GoogleDevice device = new GoogleDevice(route);
            if(mDiscoveredDevices.add(route))
                listener.onDeviceDetected(device);
        }

        @Override
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteRemoved(router, route);
            GoogleDevice device = new GoogleDevice(route);
            mDiscoveredDevices.remove(route);
            listener.onDeviceRemoved(device);
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteVolumeChanged(router, route);
            listener.onVolumeChanged((double) route.getVolume(), route.getVolume() == 0);
        }
    };

    private RemoteMediaPlayer.OnStatusUpdatedListener mMediaPlayerStatusListener = new RemoteMediaPlayer.OnStatusUpdatedListener() {
        @Override
        public void onStatusUpdated() {
            MediaStatus mediaStatus = remoteMediaPlayer.getMediaStatus();
            if(mediaStatus != null) {
                boolean isPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
                float position = (float) remoteMediaPlayer.getApproximateStreamPosition();
                listener.onPlayBackChanged(isPlaying, position);
            }
        }
    };

}
