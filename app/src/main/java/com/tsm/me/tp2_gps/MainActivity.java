package com.tsm.me.tp2_gps;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    GoogleApiClient googleApiClient;
    LocationRequest locationRequest;
    private static final int PERMISSION_REQUEST_CODE_LOCATION = 1;
    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 0;
    private static final String PHONE_NUMBER = "913071183";
    private static final String SENT = "SMS_SENT";
    private static final String DELIVERED = "SMS_DELIVERED";

    int REQUEST_CHECK_SETTINGS = 1000;

    SharedPreferences prefs = null;

    // UI Widgets.
    private TextView mLastUpdateTimeTextView;
    private TextView mLatitudeTextView;
    private TextView mLongitudeTextView;
    private TextView mSmsTextView;
    private TextView mSmsSentTextView;
    private TextView mDefaultSmsTextView;
    private TextView mDefaultLocTextView;
    private Button mSos;
    private Button mStop;
    private EditText fetch;
    private EditText send;

    // Labels.
    private String mLatitudeLabel;
    private String mLongitudeLabel;
    private String mLastUpdateTimeLabel;
    private String mLastSmsLabel;
    private String mLastSmsTimeLabel;
    private String mDefaultSmsLabel;
    private String mDefaultLocLabel;

    private Location mLastLocation;
    private SmsManager sms;
    private ArrayList<PendingIntent> sendList;
    private ArrayList<PendingIntent> deliverList;


    Handler handler = new Handler();
    private Runnable periodicUpdate;

    List<Location> list = Collections.synchronizedList(new ArrayList<Location>());
    private int counter;

    private boolean started = false;
    private boolean sos = false;
    private boolean reset = false;

    private String pathName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            pathName = extras.getString("path");
            boolean bool = extras.getBoolean("default");
        }

        // Locate the UI widgets.
        mLatitudeTextView = (TextView) findViewById(R.id.lat);
        mLongitudeTextView = (TextView) findViewById(R.id.lng);
        mLastUpdateTimeTextView = (TextView) findViewById(R.id.time);
        mSmsTextView = (TextView) findViewById(R.id.sms);
        mSmsSentTextView = (TextView) findViewById(R.id.sms_time);
        mDefaultSmsTextView = (TextView) findViewById(R.id.default_sms);
        mDefaultLocTextView = (TextView) findViewById(R.id.default_loc);
        mSos = (Button) findViewById(R.id.sos);
        mStop = (Button) findViewById(R.id.stopButton);
        fetch = (EditText) findViewById(R.id.fetch);
        send = (EditText) findViewById(R.id.send);

        // Set labels.
        mLatitudeLabel = getResources().getString(R.string.latitude_label);
        mLongitudeLabel = getResources().getString(R.string.longitude_label);
        mLastUpdateTimeLabel = getResources().getString(R.string.last_update_time_label);
        mLastSmsLabel = getResources().getString(R.string.sms_label);
        mLastSmsTimeLabel = getResources().getString(R.string.sms_time_label);
        mDefaultSmsLabel = getResources().getString(R.string.sms_default_label);
        mDefaultLocLabel = getResources().getString(R.string.loc_default_label);

        prefs = getSharedPreferences("com.tsm.me.tp2_gps", MODE_PRIVATE);

        /*
        * Setting up the runtime permission. We should implement when we target  Android Marshmallow (API 23) as a target Sdk.
        * */
        requestPermission(PERMISSION_REQUEST_CODE_LOCATION, getApplicationContext(), this);

        buildGoogleApiClient(prefs.getLong("fetchLocation", 10000));

        mSos.setEnabled(false);
        mSos.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handler.removeCallbacks(periodicUpdate);
                sos = true;
                handler.post(periodicUpdate);
                mSos.setText(R.string.sosButtonActivated);
                mSos.setEnabled(false);
            }
        });

        mStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handler.removeCallbacks(periodicUpdate);
                setStopSms();
            }
        });

        fetch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if(v.getText().length() != 0){
                        handler.removeCallbacks(periodicUpdate);
                        reset = true;
                        prefs.edit().putLong("fetchLocation", Long.parseLong(v.getText().toString())*1000).apply();
                        if (googleApiClient != null) {
                            googleApiClient.disconnect();
                        }
                        buildGoogleApiClient(prefs.getLong("fetchLocation",10000));
                    }

                    handled = true;
                }

                return handled;
            }
        });

        send.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if(v.getText().length() != 0 ){
                        if(Long.parseLong(v.getText().toString())>prefs.getLong("fetchLocation",10000)/1000){
                            handler.removeCallbacks(periodicUpdate);
                            prefs.edit().putLong("sendSMS", Long.parseLong(v.getText().toString())*1000).apply();
                            mDefaultSmsTextView.setText(String.format(Locale.getDefault(), "%s: %d (s)",
                                    mDefaultSmsLabel, prefs.getLong("sendSMS", 0) / 1000));
                            handler.post(periodicUpdate);
                        } else {
                            v.setError("Sms timer needs to be > location timer!");
                        }
                    }

                    handled = true;
                }

                return handled;
            }
        });

        // Quando a SMS é enviada.
        this.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        setSmsLabels("Enviada");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        setSmsLabels("Falha genérica");
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        setSmsLabels("Sem Rede");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        setSmsLabels("PDU nulo");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        setSmsLabels("Desligado");
                        break;
                }
            }
        }, new IntentFilter(SENT));

        // Quando a SMS é entregue.
        this.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        setSmsLabels("Entregue");
                        break;
                    case Activity.RESULT_CANCELED:
                        setSmsLabels("Cancelada");
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));

        sms = SmsManager.getDefault();
        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(DELIVERED), 0);

        sendList = new ArrayList<>();
        sendList.add(sentPI);
        deliverList = new ArrayList<>();
        deliverList.add(deliveredPI);

        periodicUpdate = new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(periodicUpdate, prefs.getLong("sendSMS", 50000));
                if (!started) {
                    started = true;
                    mSos.setEnabled(true);
                    setStartSms();
                } else if (sos) {
                    setSosSms();
                } else {
                    setNormalSms();
                }
            }
        };

    }

    private void setStopSms() {
        String foo = "STOP@" + prefs.getInt("id", 0);
        if (counter < list.size()) {
            for (int i = counter; i < list.size(); i++) {
                foo += "@";
                foo += list.get(i).getLatitude();
                foo += "@";
                foo += list.get(i).getLongitude();
            }
        }
        System.out.println(foo);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                Toast.makeText(this, "We must need your permission in order to access your reporting location.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_SEND_SMS);
            }
        } else {
            sendSMS(foo);
        }
    }

    private void setNormalSms() {
        String foo = "N@" + prefs.getInt("id", 0);
        for (int i = counter; i < list.size(); i++) {
            foo += "@";
            foo += list.get(i).getLatitude();
            foo += "@";
            foo += list.get(i).getLongitude();
        }
        counter = list.size();
        System.out.println(foo);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                Toast.makeText(this, "We must need your permission in order to access your reporting location.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_SEND_SMS);
            }
        } else {
            sendSMS(foo);
        }
    }

    private void setStartSms() {
        String foo = "START@" + prefs.getInt("id", 0) + "@" + pathName + "@"
                + mLastLocation.getLatitude() + "@" + mLastLocation.getLongitude();

        System.out.println(foo);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                Toast.makeText(this, "We must need your permission in order to access your reporting location.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_SEND_SMS);
            }
        } else {
            sendSMS(foo);
        }
    }

    private void setSosSms() {
        String foo = "SOS@" + prefs.getInt("id", 0) + "@Our user <first> <last> activated the SOS signal! "
                + "Last known coordenates are Latitude: " + String.valueOf(mLastLocation.getLatitude()) + " and Longitude: "
                + String.valueOf(mLastLocation.getLongitude());
        System.out.println(foo);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                Toast.makeText(this, "We must need your permission in order to access your reporting location.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_SEND_SMS);
            }
        } else {
            sendSMS(foo);
        }
    }

    private synchronized void sendSMS(String msg) {
        ArrayList<String> parts = sms.divideMessage(msg);
        sms.sendMultipartTextMessage(PHONE_NUMBER, null, parts, sendList, deliverList);
    }

    private synchronized void setSmsLabels(String s) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.US);
        mSmsTextView.setText(String.format(Locale.getDefault(), "%s: %s",
                mLastSmsLabel, s));
        mSmsSentTextView.setText(String.format(Locale.getDefault(), "%s: %s",
                mLastSmsTimeLabel, format.format(new Date())));
    }


    protected synchronized void buildGoogleApiClient(long value) {
        try {
            googleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(MainActivity.this)
                    .addOnConnectionFailedListener(MainActivity.this)
                    .build();

            locationRequest = LocationRequest.create();

            locationRequest.setInterval(value);

            locationRequest.setFastestInterval(value / 2);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            checkForLocationReuqestSetting(locationRequest);
            googleApiClient.connect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    * onStart : Called when the activity is becoming visible to the user.
    * */
    @Override
    protected void onStart() {
        super.onStart();

    }

    /*
    * onStop : Called when the activity is no longer visible to the user
    * */
    @Override
    protected void onStop() {
        super.onStop();

        //Disconnect the google client api connection.
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
    }

    /*
    * onPause : Called when the system is about to start resuming a previous activity.
    * */
    @Override
    protected void onPause() {
        try {
            super.onPause();

            /*
            * Stop retrieving locations when we go out of the application.
            * */
            if (googleApiClient != null) {
                stopLocationUpdates();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    /*
    * Callback method of GoogleApiClient.ConnectionCallbacks
    * */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        try {

            //mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            mLatitudeTextView.setText(String.format(Locale.getDefault(), "%s: %s", mLatitudeLabel,
                    "Waiting signal.."));
            mLongitudeTextView.setText(String.format(Locale.getDefault(), "%s: %s", mLongitudeLabel,
                    "Waiting signal.."));
            mLastUpdateTimeTextView.setText(String.format(Locale.getDefault(), "%s: %s",
                    mLastUpdateTimeLabel, "None"));
            mSmsTextView.setText(String.format(Locale.getDefault(), "%s: %s",
                    mLastSmsLabel, "None"));
            mSmsSentTextView.setText(String.format(Locale.getDefault(), "%s: %s",
                    mLastSmsTimeLabel, "None"));
            mDefaultLocTextView.setText(String.format(Locale.getDefault(), "%s: %d (s)",
                    mDefaultLocLabel, prefs.getLong("fetchLocation", 0) / 1000));
            mDefaultSmsTextView.setText(String.format(Locale.getDefault(), "%s: %d (s)",
                    mDefaultSmsLabel, prefs.getLong("sendSMS", 0) / 1000));

            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);

        } catch (SecurityException ex) {
            ex.printStackTrace();
        }

    }

    /*
    * Callback method of GoogleApiClient.ConnectionCallbacks
    * */
    @Override
    public void onConnectionSuspended(int i) {
        System.out.println("onConnectionSuspended called...");
    }

    /*
    * Callback method of GoogleApiClient.OnConnectionFailedListener
    * */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        System.out.println("onConnectionFailed called.");
    }

    /*
    * Callback method of Locationlistener.
    * */
    @Override
    public void onLocationChanged(final Location location) {
        System.out.println("location changed " + location);
        mLastLocation = location;

        if (!started) {
            counter = 1;
            handler.post(periodicUpdate);
        }
        if(reset){
            reset = false;
            handler.post(periodicUpdate);
        }

        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.US);

        mLatitudeTextView.setText(String.format(Locale.getDefault(), "%s: %f", mLatitudeLabel,
                location.getLatitude()));
        mLongitudeTextView.setText(String.format(Locale.getDefault(), "%s: %f", mLongitudeLabel,
                location.getLongitude()));
        mLastUpdateTimeTextView.setText(String.format(Locale.getDefault(), "%s: %s",
                mLastUpdateTimeLabel, format.format(new Date())));

        list.add(location);

    }

    public static void requestPermission(int perCode, Context _c, Activity _a) {

        String fineLocationPermissionString = Manifest.permission.ACCESS_FINE_LOCATION;
        String coarseLocationPermissionString = Manifest.permission.ACCESS_COARSE_LOCATION;

        if (ContextCompat.checkSelfPermission(_a, fineLocationPermissionString) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(_a, coarseLocationPermissionString) != PackageManager.PERMISSION_GRANTED
                ) {

            //user has already cancelled the permission prompt. we need to advise him.
            if (ActivityCompat.shouldShowRequestPermissionRationale(_a, fineLocationPermissionString)) {
                Toast.makeText(_c, "We must need your permission in order to access your reporting location.", Toast.LENGTH_LONG).show();
            }

            ActivityCompat.requestPermissions(_a, new String[]{fineLocationPermissionString, coarseLocationPermissionString}, perCode);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        for (String per : permissions) {
            System.out.println("permissions are  " + per);
        }

        switch (requestCode) {

            case PERMISSION_REQUEST_CODE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, "Permission loaded...", Toast.LENGTH_SHORT).show();

                } else {

                    Toast.makeText(getApplicationContext(), "Permission Denied, You cannot access location data.", Toast.LENGTH_LONG).show();

                }
                break;
            case MY_PERMISSIONS_REQUEST_SEND_SMS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Permission loaded...", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission Denied, You cannot send sms.", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    /**
     * Google Fused API require Runtime permission. Runtime permission is available for Android Marshmallow
     * or Greater versions.
     *
     * @param locationRequest needed to check whether we need to prompt settings alert.
     */
    private void checkForLocationReuqestSetting(LocationRequest locationRequest) {
        try {
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest);

            PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());

            result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                    final Status status = locationSettingsResult.getStatus();
                    final LocationSettingsStates locationSettingsStates = locationSettingsResult.getLocationSettingsStates();

                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            // All location settings are satisfied. The client can
                            // initialize location requests here.
                            Log.d("MainActivity", "onResult: SUCCESS");
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            Log.d("MainActivity", "onResult: RESOLUTION_REQUIRED");
                            // Location settings are not satisfied, but this can be fixed
                            // by showing the user a dialog.
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                status.startResolutionForResult(
                                        MainActivity.this,
                                        REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            Log.d("MainActivity", "onResult: SETTINGS_CHANGE_UNAVAILABLE");
                            // Location settings are not satisfied. However, we have no way
                            // to fix the settings so we won't show the dialog.

                            break;
                    }

                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            Toast.makeText(this, "Setting has changed...", Toast.LENGTH_SHORT).show();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

}