package com.beattheheat.beatthestreet.Networking.OC_API;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.beattheheat.beatthestreet.FileManagement.FileToStrings;
import com.beattheheat.beatthestreet.FileManagement.Unzipper;
import com.beattheheat.beatthestreet.Networking.ByteRequest;
import com.beattheheat.beatthestreet.Networking.SCallable;
import com.beattheheat.beatthestreet.Networking.VolleyRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Matt on 24-Oct-17.
 *
 * GTFS container class.
 *
 * Also contains loading functions.
 *  - will check for existence of GTFS files on disk, and then validate them against the server
 *  - if not valid, or not on disk, the GTFS will be downloaded and unzipped onto the disk
 */

public class GTFS {
    // Tables
    HashMap<Integer, OCRoute> routeTable;
    HashMap<String, OCTrip> tripTable;
    HashMap<String, OCStop> stopTable;

    // GTFS Tables (Tables for the main database in the API)
    private String[] gtfsTableNames = {"agency", "calendar", "calendar_dates", "routes", "stops", "stop_times", "trips"};
    // GTFS Zip URL (For smaller download)
    private static final String GTFS_ZIP_URL = "http://www.octranspo1.com/files/google_transit.zip";

    // Android Context
    private Context appCtx;

    // Volley queue
    private RequestQueue req;


    public GTFS(Context context) {
        routeTable = new HashMap<>(200);
        tripTable = new HashMap<>(18000);
        stopTable = new HashMap<>(5700);

        appCtx = context.getApplicationContext();

        req = VolleyRequest.getInstance(appCtx.getApplicationContext()).getRequestQueue();
    }

    // Starts the asynchronous load of the GTFS files
    // All callbacks will be alerted with a T/F when files have been loaded
    public void LoadGTFS(SCallable<Boolean> sCallables) {
        new GTFSDownload().execute(sCallables);
    }

    // Async task to load the GTFS files
    private class GTFSDownload extends AsyncTask<SCallable<Boolean>, Integer, Void> {
        @Override
        protected Void doInBackground(SCallable<Boolean>[] sCallables) {
            InternalLoadGTFS();

            // Callbacks
            for (SCallable<Boolean> callback : sCallables) {
                callback.call(true);
            }

            return null;
        }
    }

    // Retrieves the GTFS file if on disk and not out of date, or downloads the current one otherwise
    private void InternalLoadGTFS() {
        /* Check if the gtfs files exist on disk
         * Check for: "calendar.txt"
         */
        boolean gtfsOnDisk = false;
        for (File f : appCtx.getFilesDir().listFiles()) {
            if (f.getName().equals("calendar.txt")) {
                gtfsOnDisk = true;
            }
        }

        // If GTFS is on disk, check date and if valid load all files
        if (gtfsOnDisk) {
            try {
                // Get the current start date from the calendar.txt file
                FileInputStream fis = appCtx.openFileInput("calendar.txt");
                String[] lines = (new FileToStrings(fis)).toStringArray();

                // Compare against server
                CheckGTFSDateValid(lines[1].split(",")[8], new SCallable<Integer>() {
                    @Override
                    public void call(Integer arg) {
                        // Valid GTFS
                        if (arg == 1) {
                            LoadGTFSFromDisk();
                        }
                        // Invalid/corrupt/non-existent GTFS
                        else if (arg == 0 || arg == -1) {
                            LoadGTFSFromNet();
                        }
                    }
                });
            } catch(Exception e) {
                e.printStackTrace();
            }
        } else {
            LoadGTFSFromNet();
        }
    }

    private void LoadGTFSFromDisk() {
        /*************************
         *    OCROUTE LOADING    *
         *************************/
        // Load the "trips.txt" file
        try (FileInputStream fis = appCtx.openFileInput("trips.txt")) {
            List<String> lines = (new FileToStrings(fis).toStringList());
            for (int i = 1; i < lines.size(); i++) {
                OCRoute.LoadRoute(this, lines.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("GTFS", "Error opening 'trips.txt'");
        }


        /************************
         *    OCTRIP LOADING    *
         ************************/
        // Load the "stop_times.txt" file
        try (FileInputStream fis = appCtx.openFileInput("stop_times.txt")) {
            List<String> lines = (new FileToStrings(fis).toStringList());
            for (int i = 1; i < lines.size(); i++) {
                OCTrip.LoadTrip(this, lines.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("GTFS", "Error opening 'trips.txt'");
        }


        /************************
         *    OCSTOP LOADING    *
         ************************/
        // Load the "stops.txt" file
        try (FileInputStream fis = appCtx.openFileInput("stops.txt")) {
            List<String> lines = (new FileToStrings(fis).toStringList());
            for (int i = 1; i < lines.size(); i++) {
                OCStop.LoadStop(this, lines.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("GTFS", "Error opening 'trips.txt'");
        }
    }

    private void LoadGTFSFromNet() {
        // Delete old GTFS files, if present
        appCtx.deleteFile("GTFS.zip");
        for (String s : gtfsTableNames) {
            appCtx.deleteFile(s + ".txt");
        }

        // Make a byte request to download the GTFS zip file
        ByteRequest bReq = new ByteRequest(
                Request.Method.GET,
                GTFS_ZIP_URL,
                new Response.Listener<Byte[]>() {
                    @Override
                    public void onResponse(Byte[] response) {
                        try {
                            // Write the bytes to a file in internal storage
                            FileOutputStream os;
                            String fileName = "GTFS.zip";

                            os = appCtx.openFileOutput(fileName, Context.MODE_PRIVATE);

                            // Convert from Byte[] to byte[]
                            byte[] bytes = new byte[response.length];
                            for (int i = 0; i < response.length; i++)
                                bytes[i] = response[i];

                            os.write(bytes);
                            os.close();

                            // Extract the files contents to the internal storage
                            Unzipper uz = new Unzipper(appCtx, fileName, appCtx.getFilesDir().getPath());
                            uz.Unzip();

                            // Delete the zip file
                            appCtx.deleteFile(fileName);

                            // Load the contents from disk now
                            LoadGTFSFromDisk();

                        } catch (Exception e) {
                            Log.e("OC_ERR", "Error with callback response: " + e.toString());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("OC_ERR", "Error with OC_API GET request: " + error.toString());
                    }
                }
        );

        req.add(bReq);
    }

    /** Checks if the GTFS needs to be updated
     *
     * @param oldStartDate - String, needs to be in format "YYYYMMDD"
     *
     * @param callback
     *  - will place a -1 in the Integer argument if the operation to get date info failed
     *  - will place a 0 in the Integer argument if the date is invalid
     *  - will place a 1 in the Integer argument if the date is valid
     */
    public void CheckGTFSDateValid(final String oldStartDate, final SCallable<Integer> callback) {
        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("appID", OCTranspo.appID);
        params.put("apiKey", OCTranspo.apiKey);
        params.put("table", "calendar");
        params.put("format", "json");

        // Make a request to the OC API
        StringRequest jReq = new StringRequest(
                Request.Method.POST,
                OCTranspo.apiURLs[OCTranspo.OC_TYPE.GTFS.ordinal()],
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            // Parse returned text into a json
                            JSONObject calendarJSON = new JSONObject(response);

                            // Get the end_date of the current schedule
                            String start_date = calendarJSON.getJSONArray("Gtfs").getJSONObject(0).getString("start_date");

                            // Call the callback and give it a return value based on whether we've validated
                            Integer returnCode = oldStartDate.equals(start_date) ? 1 : 0;
                            callback.call(returnCode);

                        } catch (JSONException e) {
                            e.printStackTrace();

                            // Give the callback the error code
                            Integer returnCode = -1;
                            callback.call(returnCode);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("OC_ERR", "Error with OC_API POST request: " + error.toString());
                    }
                }
        ) {
            /**
             * Passing some request parameters
             */
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                return params;
            }
        };

        req.add(jReq);
    }
}