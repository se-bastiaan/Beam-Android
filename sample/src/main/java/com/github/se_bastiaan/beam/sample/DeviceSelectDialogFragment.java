package com.github.se_bastiaan.beam.sample;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import com.github.se_bastiaan.beam.BeamManager;
import com.github.se_bastiaan.beam.device.BeamDevice;

import java.util.ArrayList;
import java.util.List;

public class DeviceSelectDialogFragment extends DialogFragment {

    private Listener listener;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final BeamManager manager = BeamManager.getInstance();
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.select_dialog_singlechoice);
        for (BeamDevice device : manager.getDevices().values()) {
            arrayAdapter.add(device.getName());
        }

        builder.setNegativeButton(
                "cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.onResult(new ArrayList<>(manager.getDevices().values()).get(which));
                    }
                });

        return builder.create();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {

        /**
         * @param device {@link BeamDevice}
         */
        void onResult(BeamDevice device);

    }

}