package com.example.mygcs;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;


public class AlertDialogActivity extends Dialog implements View.OnClickListener {

    private Context mContext;

    private TextView btn_cancel;
    private TextView btn_ok;

    public AlertDialogActivity(@NonNull Context context) {
        super(context);
        mContext = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alert);


        btn_cancel = (TextView) findViewById(R.id.btnConfirm);
        btn_ok = (TextView) findViewById(R.id.btnConfirm);
        btn_cancel.setOnClickListener(this);
        btn_ok.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCancel:
                dismiss();
                break;
            case R.id.btnConfirm:
                ((MainActivity) mContext).finish();
                break;
        }
    }
}
