/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.soonami;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * Displays information about a single earthquake.
 */
@SuppressLint("SimpleDateFormat")
public class MainActivity extends AppCompatActivity {

    /**
     * Tag for the log messages
     */
    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    /**
     * URL to query the USGS dataset for earthquake information
     */
    // private static final String USGS_REQUEST_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2012-01-01&endtime=2012-12-01&minmagnitude=6";
    // URL #2
    private static final String USGS_REQUEST_URL = "http://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2014-01-01&endtime=2014-12-01&minmagnitude=7";
    // Known bad URL
    // private static final String USGS_REQUEST_URL = "http://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2014-01-01&endtime=2014-01-02asdfasdf";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FutureTask<Event> task = new FutureTask<>(new ThreadTemplate());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(task);
        try {
            updateUi(task.get());
        } catch (ExecutionException err) {
            Log.d(LOG_TAG, "Task Execution Exception!", err);
        } catch (InterruptedException err) {
            Log.d(LOG_TAG, "Task Interrupted!", err);
        }
    }

    /**
     * Update the screen to display information from the given {@link Event}.
     */
    private void updateUi(Event earthquake) {
        // Display the earthquake title in the UI
        TextView titleTextView = findViewById(R.id.title);
        titleTextView.setText(earthquake.title);

        // Display the earthquake date in the UI
        TextView dateTextView = findViewById(R.id.date);
        dateTextView.setText(getDateString(earthquake.time));

        // Display whether or not there was a tsunami alert in the UI
        TextView tsunamiTextView = findViewById(R.id.tsunami_alert);
        tsunamiTextView.setText(getTsunamiAlertString(earthquake.tsunamiAlert));
    }

    /**
     * Returns a formatted date and time string for when the earthquake happened.
     */
    private String getDateString(long timeInMilliseconds) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy 'at' HH:mm:ss z");
        return formatter.format(timeInMilliseconds);
    }

    /**
     * Return the display string for whether or not there was a tsunami alert for an earthquake.
     */
    private String getTsunamiAlertString(int tsunamiAlert) {
        switch (tsunamiAlert) {
            case 0:
                return getString(R.string.alert_no);
            case 1:
                return getString(R.string.alert_yes);
            default:
                return getString(R.string.alert_not_available);
        }
    }

    public static class ThreadTemplate implements Callable<Event> {
        private final URL url = createUrl();

        public ThreadTemplate() {
            Log.d("ThreadTemplate", "Thread made.");
        }


        public Event call() throws IOException {
            // Perform HTTP request to the URL and receive a JSON response back
            String jsonResponse = "";
            if (url != null) {
                try { jsonResponse = makeHttpRequest(url); }
                catch (IOException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            // Extract relevant fields from the JSON response and create an {@link Event} object

            // Return the {@link Event} object as the result fo the {@link TsunamiAsyncTask}
            Log.d("ThreadTemplate", "Thread Done.");
            return extractFeatureFromJson(jsonResponse);
        }


        /**
         * Returns new URL object from the given string URL.
         */
        @Nullable
        private URL createUrl() {
            URL url;
            try {
                url = new URL(USGS_REQUEST_URL);
            } catch (MalformedURLException exception) {
                Log.e(LOG_TAG, "Error with creating URL", exception);
                return null;
            }
            return url;
        }

        /**
         * Make an HTTP request to the given URL and return a String as the response.
         */
        private String makeHttpRequest(@NonNull URL url) throws IOException {
            String jsonResponse = "";
            HttpURLConnection urlConnection;
            InputStream inputStream = null;
            int returnCode;
            URL useURL;
            if (url.getProtocol().equals("http")) {
                useURL = new URL("https" + url.toString().substring(4));
            } else {
                useURL = url;
            }
            urlConnection = (HttpURLConnection) useURL.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setReadTimeout(10000 /* milliseconds */);
            urlConnection.setConnectTimeout(15000 /* milliseconds */);
            urlConnection.setInstanceFollowRedirects(true);
            try { urlConnection.connect(); }
            catch(IOException e) {
                e.printStackTrace();
                Log.d(LOG_TAG, "IO exception encountered!!", e);
            }
            returnCode = urlConnection.getResponseCode();
            if (returnCode == 200) {
                try {
                    inputStream = urlConnection.getInputStream();
                    jsonResponse = readFromStream(inputStream);
                } finally {
                    if (inputStream != null) {
                        // function must handle java.io.IOException here
                        inputStream.close();
                    }
                    urlConnection.disconnect();
                }
            } else if (returnCode == 400) {
                Log.d(LOG_TAG, "HTTP400 error returned. Malformed request.");
                urlConnection.disconnect();
            }
            return jsonResponse;
        }

        private String readFromStream(InputStream inputStream) throws IOException {
            StringBuilder output = new StringBuilder();
            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader);
                String line = reader.readLine();
                while (line != null) {
                    output.append(line);
                    line = reader.readLine();
                }
            }
            return output.toString();
        }

        /**
         * Return an {@link Event} object by parsing out information
         * about the first earthquake from the input earthquakeJSON string.
         */
        private Event extractFeatureFromJson(String earthquakeJSON) {
            if (TextUtils.isEmpty(earthquakeJSON)) {
                return new Event();
            }
            try {
                JSONObject baseJsonResponse = new JSONObject(earthquakeJSON);
                JSONArray featureArray = baseJsonResponse.getJSONArray("features");

                // If there are results in the features array
                if (featureArray.length() > 0) {
                    // Extract out the first feature (which is an earthquake)
                    JSONObject firstFeature = featureArray.getJSONObject(0);
                    JSONObject properties = firstFeature.getJSONObject("properties");

                    // Extract out the title, time, and tsunami values
                    String title = properties.getString("title");
                    long time = properties.getLong("time");
                    int tsunamiAlert = properties.getInt("tsunami");

                    // Create a new {@link Event} object
                    Log.d("ThreadTemplate", "Event title: " + title + ", Time: " + time + ", Alert: " + tsunamiAlert);
                    return new Event(title, time, tsunamiAlert);
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Problem parsing the earthquake JSON results", e);
            }
            return null;
        }
    }
}
