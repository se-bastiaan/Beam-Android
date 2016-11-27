package com.github.se_bastiaan.beam.sample;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

public class InputDialogFragment extends DialogFragment {

    private Listener listener;

    EditText input;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_input, null);

        input = (EditText) v.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        builder.setView(v);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                listener.onResult(input.getText().toString());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        return builder.create();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Callback for the user's response.
     */
    public interface Listener {

        /**
         * @param result User's input
         */
        void onResult(String result);

    }

}