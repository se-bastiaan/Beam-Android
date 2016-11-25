package com.github.se_bastiaan.beam.sample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.github.se_bastiaan.beam.BeamDiscoveryListener;
import com.github.se_bastiaan.beam.device.BeamDevice;
import com.github.se_bastiaan.beam.BeamListener;
import com.github.se_bastiaan.beam.BeamManager;
import com.github.se_bastiaan.beam.discovery.DiscoveryManager;
import com.github.se_bastiaan.beam.discovery.DiscoveryManagerListener;

public class MainActivity extends AppCompatActivity {

    private static final String BEAM = "Beam";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BeamManager.init(this).addDiscoveryListener(new BeamDiscoveryListener() {
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
