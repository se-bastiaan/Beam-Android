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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;

import com.github.se_bastiaan.beam.BaseBeamClient;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.BeamListener;
import com.github.se_bastiaan.beam.device.GoogleCastDevice;
import com.github.se_bastiaan.beam.logger.Logger;
import com.github.se_bastiaan.beam.Playback;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.util.HashSet;
import java.util.Set;

public class GoogleCastClient extends BaseBeamClient implements Application.ActivityLifecycleCallbacks {
    
    private final String TAG = getClass().getCanonicalName();

    private final Set<MediaRouter.RouteInfo> discoveredDevices = new HashSet<>();

    private MediaRouteSelector mediaRouterSelector;
    private MediaRouter mediaRouter;
    private CastContext castContext;
    private SessionManager sessionManager;
    private CastSession castSession;
    private RemoteMediaClient remoteMediaClient;
    private GoogleCastDevice currentDevice;

    private BeamListener listener;

    public GoogleCastClient(Context context, BeamListener listener) {
        this.listener = listener;

        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(this);

        castContext = CastContext.getSharedInstance(context.getApplicationContext());
        sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener);

        mediaRouter = MediaRouter.getInstance(context.getApplicationContext());
        mediaRouter.addCallback(castContext.getMergedSelector(), mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    public void setContext(Context context) {
        // do nothing
    }

    /**
     * Call on close
     */
    public void destroy() {
        disconnect();
        mediaRouter.removeCallback(mediaRouterCallback);
    }

    @Override
    public void loadMedia(Playback playback, float position) {
        if(currentDevice != null && remoteMediaClient != null && castSession != null) {
            MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

            movieMetadata.putString(MediaMetadata.KEY_TITLE, playback.title);
            movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, playback.subtitleLocation);
            movieMetadata.addImage(new WebImage(Uri.parse(playback.image)));

            MediaInfo mediaInfo = new MediaInfo.Builder(playback.videoLocation)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("video/mp4")
                    .setMetadata(movieMetadata)
                    .build();

            remoteMediaClient.load(mediaInfo, true, 0); // TODO position
        }
    }

    @Override
    public void play() {
        remoteMediaClient.play();
    }

    @Override
    public void pause() {
        remoteMediaClient.pause();
    }

    @Override
    public void seek(float position) {
        remoteMediaClient.seek((long) (remoteMediaClient.getStreamDuration() * position));
    }

    @Override
    public void stop() {
        if(currentDevice != null && remoteMediaClient != null && castSession != null) {
            try {
                remoteMediaClient.stop();
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

        currentDevice = (GoogleCastDevice) device;
        mediaRouter.selectRoute(currentDevice.routeInfo);
        Logger.d(TAG, "Connecting to google cast device: " + device.getId() + " - " + currentDevice.getName());



        listener.onDeviceSelected(currentDevice);
        // initEvents();
    }

    @Override
    public void disconnect() {
        if (currentDevice != null && remoteMediaClient != null && castSession != null) {
            sessionManager.endCurrentSession(true);
        }

        currentDevice = null;
        mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
        listener.onDisconnected();
    }

    @Override
    public void setVolume(float volume) {
        if (currentDevice != null && remoteMediaClient != null && castSession != null) {
            remoteMediaClient.setStreamVolume(volume);
        }
    }

    @Override
    public boolean canControlVolume() {
        return currentDevice != null && currentDevice.routeInfo.getVolumeHandling() == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    }

    private MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteSelected(router, route);
            currentDevice = new GoogleCastDevice(route);
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteUnselected(router, route);
            if (currentDevice != null && currentDevice.equals(new GoogleCastDevice(route))) {
                currentDevice = null;
                listener.onDisconnected();
            }
        }

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteAdded(router, route);
            GoogleCastDevice device = new GoogleCastDevice(route);
            if(discoveredDevices.add(route)) {
                listener.onDeviceDetected(device);
            }
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteChanged(router, route);
            GoogleCastDevice device = new GoogleCastDevice(route);
            if(discoveredDevices.add(route)) {
                listener.onDeviceDetected(device);
            }
        }

        @Override
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteRemoved(router, route);
            GoogleCastDevice device = new GoogleCastDevice(route);
            discoveredDevices.remove(route);
            listener.onDeviceRemoved(device);
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteVolumeChanged(router, route);
            listener.onVolumeChanged((double) route.getVolume(), route.getVolume() == 0);
        }
    };

    private RemoteMediaClient.ProgressListener mediaPlayerProgressListener = new RemoteMediaClient.ProgressListener() {
        @Override
        public void onProgressUpdated(long progressMs, long durationMs) {
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            if(mediaStatus != null) {
                boolean isPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
                float position = (float) progressMs / durationMs;
                listener.onPlayBackChanged(isPlaying, position);
            }
        }
    };

    private SessionManagerListener<Session> sessionManagerListener = new SessionManagerListener<Session>() {
        @Override
        public void onSessionStarting(Session session) {

        }

        @Override
        public void onSessionStarted(Session session, String s) {
            castSession = sessionManager.getCurrentCastSession();
            remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.addProgressListener(mediaPlayerProgressListener, 10);
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {

        }

        @Override
        public void onSessionEnding(Session session) {

        }

        @Override
        public void onSessionEnded(Session session, int i) {
            castSession = null;
            remoteMediaClient = null;
        }

        @Override
        public void onSessionResuming(Session session, String s) {

        }

        @Override
        public void onSessionResumed(Session session, boolean b) {

        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {

        }

        @Override
        public void onSessionSuspended(Session session, int i) {

        }
    };

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityStarted(Activity activity) {
        mediaRouter.removeCallback(mediaRouterCallback);
        mediaRouter.addCallback(castContext.getMergedSelector(), mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
        mediaRouter.removeCallback(mediaRouterCallback);
        mediaRouter.addCallback(castContext.getMergedSelector(), mediaRouterCallback);

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }
}
