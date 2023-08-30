package com.atakmap.android.ble_forwarder.takserver_facade;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import fi.iki.elonen.NanoHTTPD;

public class MyRestServer extends NanoHTTPD {

    private final static String TAG = MyRestServer.class.getSimpleName();

    public MyRestServer(int port) {
        super(port);
    }

    public static String generateTimestamp() {
        // Get the current date and time
        Date currentDate = new Date();

        // Create a SimpleDateFormat object with the desired format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'");

        // Set the time zone to UTC
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Format the current date and time using the SimpleDateFormat
        String formattedTimestamp = dateFormat.format(currentDate);

        return formattedTimestamp;
    }

    @Override
    public Response serve(IHTTPSession session) {
        // Handle incoming requests here and return appropriate responses
        Log.d(TAG, "Got request: " + session.getMethod() + " " + session.getUri());
        if (session.getUri().equals("/Marti/api/version/config")) {
            Log.d(TAG, "Responding to config request");
            return newFixedLengthResponse(Response.Status.OK, "application/json","{\"version\":\"3\",\"type\":\"ServerConfig\",\"data\":{\"version\":\"4.5.38-RELEASE\",\"api\":\"3\",\"hostname\":\"127.0.0.1\"},\"nodeId\":\"e6ec3550334a41aeb08b06e9578ea212\"}");
        } else if (session.getUri().equals("/Marti/api/clientEndPoints")) {
            Log.d(TAG, "Responding to client endpoints request");
            String currentTimestamp = generateTimestamp();
            return newFixedLengthResponse(Response.Status.OK, "application/json","{\"version\":\"3\",\"type\":\"com.bbn.marti.remote.ClientEndpoint\",\"data\":[{\"callsign\":\"GAINER\",\"uid\":\"ANDROID-357712080935181\",\"lastEventTime\":\"" + currentTimestamp + "\",\"lastStatus\":\"Connected\"},{\"callsign\":\"GRAND SLAM CTL\",\"uid\":\"ANDROID-5517dda15a8eca57\",\"lastEventTime\":\"" + currentTimestamp + "\",\"lastStatus\":\"Connected\"}],\"nodeId\":\"e6ec3550334a41aeb08b06e9578ea212\"}");
        } else if (session.getUri().equals("/Marti/sync/missionquery")) {
            Log.d(TAG, "Responding to missionquery request");
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html","<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" +
                    "<title>404 TAK Server resource not found</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<h3>404 TAK Server resource not found</h3>\n" +
                    "</body>\n" +
                    "</html>");
        }
        return newFixedLengthResponse("Hello from your REST endpoint!");
    }
}