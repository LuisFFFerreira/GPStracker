package com.tsm.me.tp2_gps;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        Handler handle = new Handler();
        handle.postDelayed(new Runnable() {
            @Override
            public void run() {
                showStartSceen();
            }
        }, 500);
    }

    private void showStartSceen(){
        Intent intent = new Intent(SplashActivity.this, StartActivity.class);
        startActivity(intent);
        finish();
    }
}
