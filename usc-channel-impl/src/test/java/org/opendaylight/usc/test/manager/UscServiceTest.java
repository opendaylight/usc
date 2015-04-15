/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONTokener;
import org.junit.Test;

public class UscServiceTest {
    // @Test
    public void test() {
        // creating a new URL with the request
        String restUrl = "http://localhost:8181/restconf/operations/usc:usc-topology";
        // String restUrl =
        // "http://localhost:8080/restconf/config/toaster:toaster";
        URL url;
        try {
            url = new URL(restUrl);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        // attaching authentication information
        String authString = "admin:admin";
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);

        // creating the URLConnection
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Basic "
                    + authStringEnc);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.connect();

            // getting the result, first check response code
            Integer httpResponseCode = connection.getResponseCode();
            if (httpResponseCode > 299)
                System.out.print("ResponseCode = " + httpResponseCode);
            // get the result string from the inputstream.
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is,
                    Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            System.out.print("Content = " + sb.toString());
            JSONTokener jt = new JSONTokener(sb.toString());
            try {
                @SuppressWarnings("unused")
                JSONObject json = new JSONObject(jt);
                // test that the resulting name and subnet matches what was
                // expected
                // in variables name1, subnet1
                // Assert.assertEquals(name1, json.getString("@name"));
                // Assert.assertEquals(subnet1, json.getString("@subnet"));
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            is.close();
            connection.disconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void createEvent() {
        sendEvent("UscChannelCreateEvent", 3);
        sendEvent("UscSessionCreateEvent", 10);
        sendEvent("UscChannelErrorEvent", 3);
        sendEvent("UscSessionErrorEvent", 6);
        sendEvent("UscSessionTransactionEvent", 5);
    }

    @Test
    public void closeEvent() {
        sendEvent("UscChannelCloseEvent", 3);
        sendEvent("UscSessionCloseEvent", 6);
    }

    private void sendEvent(String type, int num) {
        // building request body for inserting a static route
        String baseURL = "http://localhost:8181/restconf/operations/usc:monitor-event";
        String result = null;
        for (int i = 0; i < num; i++) {
            result = sendPost(baseURL, getEventRequestBody(type));
            System.out.println("Send " + type + " event,result = " + result);
        }
    }

    private String getEventRequestBody(String type) {
        return "{\"input\":{\"event-type\":\"" + type + "\"}}";
    }

    @Test
    public void sendTopologyPost() {
        // building request body for inserting a static route
        String baseURL = "http://localhost:8181/restconf/operations/usc:usc-topology";
        String requestBody = "{\"input\":{\"usc:topology-id\":\"usc1\"}}";
        String result = sendPost(baseURL, requestBody);
        JSONTokener jt = new JSONTokener(result);
        try {
            @SuppressWarnings("unused")
            JSONObject json = new JSONObject(jt);
            // test that the resulting name and subnet matches what was
            // expected in variables name1, subnet1
            // Assert.assertEquals("", json.getString("@name"));
            // Assert.assertEquals("", json.getString("@subnet"));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
    }

    public String sendPost(String baseURL, String requestBody) {
        // attaching authentication information
        String authString = "admin:admin";
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        URL url;
        StringBuilder sb = new StringBuilder();

        try {
            url = new URL(baseURL);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return e.getMessage();
        }
        // creating the URLConnection
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Basic "
                    + authStringEnc);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length",
                    requestBody.length() + "");
            connection.setRequestProperty("Accept", "application/json");
            // now add the request body
            connection.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(
                    connection.getOutputStream());
            wr.write(requestBody);
            wr.flush();
            connection.connect();

            // getting the result, first check response code
            Integer httpResponseCode = connection.getResponseCode();
            if (httpResponseCode > 299)
                System.out.println("ResponseCode = " + httpResponseCode);
            // get the result string from the inputstream.
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is,
                    Charset.forName("UTF-8")));

            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            System.out.println("Content = " + sb.toString());
            is.close();
            connection.disconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
            return e.getMessage();
        }
        return sb.toString();
    }

}
