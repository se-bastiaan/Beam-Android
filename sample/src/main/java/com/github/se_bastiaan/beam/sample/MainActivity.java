package com.github.se_bastiaan.beam.sample;

import android.annotation.SuppressLint;
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
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.device.DLNADevice;

public class MainActivity extends AppCompatActivity {

    private static final String BEAM = "Beam";

    long duration;
    long position;

    TextView durationText;
    TextView positionText;
    TextView playStateText;
    Button connectButton;
    Button loadMediaButton;
    Button playButton;
    Button pauseButton;
    Button seekForwardButton;
    Button seekBackwardButton;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        durationText = (TextView) findViewById(R.id.duration_text);
        positionText = (TextView) findViewById(R.id.position_text);
        playStateText = (TextView) findViewById(R.id.play_state_text);
        connectButton = (Button) findViewById(R.id.connect_button);
        loadMediaButton = (Button) findViewById(R.id.load_button);
        playButton = (Button) findViewById(R.id.play_button);
        pauseButton = (Button) findViewById(R.id.pause_button);
        seekForwardButton = (Button) findViewById(R.id.seek_forward_button);
        seekBackwardButton = (Button) findViewById(R.id.seek_backward_button);

        final BeamManager manager = BeamManager.init(this);

        connectButton.setText(manager.isConnected() ? "Disconnect" : "Connect");

        manager.addDiscoveryListener(new BeamDiscoveryListener() {
            @Override
            public void onDeviceAdded(BeamManager manager, BeamDevice device) {
                Toast.makeText(MainActivity.this, "Device added: " + device.getName(), Toast.LENGTH_SHORT).show();
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
                connectButton.setText("Disconnect");
                Log.d(BEAM, "Device connected: " + device.getName());
            }

            @Override
            public void onDisconnected(BeamManager manager) {
                connectButton.setText("Connect");
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

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (manager.isConnected()) {
                    manager.disconnect();
                } else {
                    DeviceSelectDialogFragment fragment = new DeviceSelectDialogFragment();
                    fragment.setListener(new DeviceSelectDialogFragment.Listener() {
                        @Override
                        public void onResult(BeamDevice device) {
                            manager.connect(device);
                        }
                    });
                    fragment.show(getSupportFragmentManager(), "device_select");
                }
            }
        });

        loadMediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputDialogFragment dialogFragment = new InputDialogFragment();
                dialogFragment.setListener(new InputDialogFragment.Listener() {
                    @Override
                    public void onResult(String result) {
                        MediaData data = new MediaData();
                        data.videoId = "1000";
                        data.title = "Beam-Android";
                        data.videoLocation = result;
                        manager.loadMedia(data);
                    }
                });
                dialogFragment.show(getSupportFragmentManager(), "input");
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
