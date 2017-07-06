package com.tsm.me.tp2_gps;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class StartActivity extends AppCompatActivity {
    private LocationManager locationManager;
    private Spinner spinner;
    private Button start;
    private Button sync;
    private EditText route;
    private EditText username;
    private EditText password;
    private ProgressDialog progress;

    SharedPreferences prefs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        // Spinner element
        spinner = (Spinner) findViewById(R.id.routes_spinner);

        // Spinner click listener
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view,
                                       int position, long id) {
                //Object item = adapterView.getItemAtPosition(position);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // TODO Auto-generated method stub

            }
        });

        // Loading spinner data from database
        loadSpinnerData();

        start = (Button) findViewById(R.id.startButton);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trackingRequested();
            }
        });

        sync = (Button) findViewById(R.id.syncButton);
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncingRequested();
            }
        });

        route = (EditText) findViewById(R.id.routes_name);
        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);


        prefs = getSharedPreferences("com.tsm.me.tp2_gps", MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println(prefs.getLong("sendSMS",69000));
        if (prefs.getBoolean("firstrun", true)) {
            // Do first run stuff here then set 'firstrun' as false
            // using the following line to edit/commit prefs
            prefs.edit().putBoolean("firstrun", false).apply();
            prefs.edit().putLong("fetchLocation", 10000).apply();
            prefs.edit().putLong("sendSMS", 60000).apply();
        }
    }

    /**
     * Function to load the spinner data from SQLite database
     */
    private void loadSpinnerData() {

        // database handler
        DatabaseHandler db = new DatabaseHandler(getApplicationContext());

        // Spinner Drop down elements
        List<String> labels = db.getAllRouteNames();

        if (labels.isEmpty()) {
            labels = new ArrayList<>();
            labels.add("No default route");
        }

        // Creating adapter for spinner
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(StartActivity.this,
                android.R.layout.simple_spinner_item, labels);

        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // attaching data adapter to spinner
        spinner.setAdapter(dataAdapter);
    }

    private void trackingRequested() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i("GPS", "OFF");
            requestGPSPermit();
        } else {
            Log.i("GPS", "ON");
            validateRoute();
        }
    }

    private void requestGPSPermit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.gps_disabled)
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void validateRoute() {
        if(prefs.contains("user")){
            Intent intent = new Intent(this, MainActivity.class);
            String rt = route.getText().toString();
            if(rt.isEmpty() && spinner.getSelectedItem().toString().equals("Select route...")) {
                route.setError("Name your path or select one!");
            } else if(rt.isEmpty() && !spinner.getSelectedItem().toString().equals("Select route...")){
                intent.putExtra("path", spinner.getSelectedItem().toString());
                intent.putExtra("default", true);
                startActivity(intent);
            } else {
                intent.putExtra("path", rt);
                intent.putExtra("default", false);
                startActivity(intent);
            }
        } else {
            Toast.makeText(this,"Syncronization required!",Toast.LENGTH_SHORT).show();
        }
    }

    private void syncingRequested() {
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if(activeNetwork != null && activeNetwork.isConnectedOrConnecting()){
            String user = username.getText().toString();
            String pw = password.getText().toString();
            boolean aux = true;
            if(user.isEmpty()) {
                username.setError("Required field!");
                aux = false;
            }
            if(pw.isEmpty()){
                username.setError("Required field!");
                aux = false;
            }
            if(aux){
                SyncTask syn = new SyncTask();
                syn.execute(user, pw);
                prefs.edit().putString("user", user).apply();
            }
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(StartActivity.this).create();
            alertDialog.setTitle("Oops");
            alertDialog.setMessage("Seems like you don't have internet access. Please connect and try again.");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
        }
    }

    private class SyncTask extends AsyncTask<String, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progress = ProgressDialog.show(StartActivity.this, "Synchronization", "Downloading data...", true);
        }

        @Override
        protected Void doInBackground(String... params) {
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            OkHttpClient client = new OkHttpClient();
            String url = "https://31bc678a.ngrok.io/defaults";
            String json = "{\"username\":\"" + params[0] + "\","
                    + "\"password\":\"" + params[1] + "\"}";
            RequestBody body = RequestBody.create(JSON, json);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String jsonData = null;
            try {
                jsonData = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }
            JSONArray arrays = null;
            try {
                arrays = new JSONArray(jsonData);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            DatabaseHandler db = new DatabaseHandler(getApplicationContext());
            Map<String,String> labels = db.getAllRoutesInfo();
            List<String> foo = new ArrayList<>();

            for (int i = 0; i < arrays.length(); i++) {
                JSONObject obj = null;
                try {
                    obj = arrays.getJSONObject(i);
                    foo.add(obj.getString("name"));
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                    Date date = dateFormat.parse(obj.getString("date"));
                    SimpleDateFormat dateformat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.UK);
                    String datetime = dateformat1.format(date);
                    if(labels.containsKey(obj.getString("name"))){
                        System.out.println("containsRow");
                        if(!labels.get(obj.getString("name")).equals(datetime)){
                            System.out.println("updateRow");
                            db.updateRow(obj.getString("name"),obj.getString("latitude"),obj.getString("longitude"),datetime);
                        }
                    } else {
                        System.out.println("newRow");
                        prefs.edit().putInt("id", obj.getInt("idUser")).apply();
                        db.insertRoute(obj.getString("name"),obj.getString("latitude"),obj.getString("longitude"),obj.getInt("idUser"),datetime);
                    }
                } catch (JSONException | ParseException e) {
                    e.printStackTrace();
                }
            }

            for(String s : labels.keySet()){
                if(!foo.contains(s)){
                    db.deleteRow(s);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            loadSpinnerData();
            progress.dismiss();
            username.setText("");
            password.setText("");
        }
    }
}


