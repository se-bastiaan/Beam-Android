package com.github.se_bastiaan.beam.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.se_bastiaan.beam.BeamControlListener;
import com.github.se_bastiaan.beam.BeamDiscoveryListener;
import com.github.se_bastiaan.beam.BeamManager;
import com.github.se_bastiaan.beam.MediaData;
import com.github.se_bastiaan.beam.device.AirPlayDevice;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.device.DLNADevice;
import com.github.se_bastiaan.beam.device.GoogleCastDevice;

public class MainActivity extends AppCompatActivity {

    private static final String BEAM = "Beam";

    long duration;
    long position;

    TextView durationText;
    TextView positionText;
    TextView playStateText;
    Button playButton;
    Button pauseButton;
    Button seekForwardButton;
    Button seekBackwardButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        durationText = (TextView) findViewById(R.id.duration_text);
        positionText = (TextView) findViewById(R.id.position_text);
        playStateText = (TextView) findViewById(R.id.play_state_text);
        playButton = (Button) findViewById(R.id.play_button);
        pauseButton = (Button) findViewById(R.id.pause_button);
        seekForwardButton = (Button) findViewById(R.id.seek_forward_button);
        seekBackwardButton = (Button) findViewById(R.id.seek_backward_button);

        final BeamManager manager = BeamManager.init(this);

        manager.addDiscoveryListener(new BeamDiscoveryListener() {
            @Override
            public void onDeviceAdded(BeamManager manager, BeamDevice device) {
                Toast.makeText(MainActivity.this, "Device added: " + device.getName(), Toast.LENGTH_SHORT).show();
                if (device instanceof DLNADevice && device.getName().contains("Kodi")) {
                    manager.connect(device);
                }
            }

            @Override
            public void onDeviceRemoved(BeamManager manager, BeamDevice device) {
                Log.d(BEAM, "Device removed");
            }

            @Override
            public void onDeviceUpdated(BeamManager manager, BeamDevice device) {
                Log.d(BEAM, "Device updated");
            }
        });

        manager.addControlListener(new BeamControlListener() {
            @Override
            public void onConnected(BeamManager manager, BeamDevice device) {
                Log.d(BEAM, "Device connected: " + device.getName());
                MediaData data = new MediaData();
                data.videoLocation = "http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_30fps_normal.mp4";
                manager.loadMedia(data);
            }

            @Override
            public void onDisconnected(BeamManager manager) {
                Log.d(BEAM, "Device disconnected");
            }

            @Override
            public void onVolumeChanged(BeamManager manager, double value, boolean isMute) {
                Log.d(BEAM, "Device volume changed");
            }

            @Override
            public void onPlayBackChanged(BeamManager manager, boolean isPlaying, long position, long duration) {
                Log.d(BEAM, "Device playback changed, position: " + position + ", duration: " + duration + ", isPlaying: " + isPlaying);
                MainActivity.this.duration = duration;
                MainActivity.this.position = position;
                durationText.setText(Long.toString(duration));
                positionText.setText(Long.toString(position));
                playStateText.setText(isPlaying ? "Playing" : "Not Playing");
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manager.play();
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manager.pause();
            }
        });

        seekForwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manager.seek(position + 10000);
            }
        });

        seekBackwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manager.seek(position - 10000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
