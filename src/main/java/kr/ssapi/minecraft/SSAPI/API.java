package kr.ssapi.minecraft.SSAPI;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class API {

    private String SOCKET_URL = "https://api.ssapi.kr";
    private String SOCKET_API = "sack2022";

    public Object connectionAPI(String ID) throws Exception {
        URL url = new URL(SOCKET_URL + "/room/user");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + SOCKET_API);
        connection.setDoOutput(true);
        connection.getOutputStream().write(("{\"platform\": \"soop\", \"user\": \"" + ID + "\"}").getBytes("UTF-8"));

        BufferedReader buffer = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));

        String inputLine;
        StringBuffer responseBody = new StringBuffer();
        while ((inputLine = buffer.readLine()) != null) {
            responseBody.append(inputLine);
        }

        JSONObject jsonResponse = new JSONObject(responseBody.toString());

        return jsonResponse.getInt("error") == 0;
    }

    public Object disconnectAPI(String ID) throws Exception {
        URL url = new URL(SOCKET_URL + "/room/user");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + SOCKET_API);
        connection.setDoOutput(true);
        connection.getOutputStream().write(("{\"platform\": \"soop\", \"user\": \"" + ID + "\"}").getBytes("UTF-8"));

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        String inputLine;
        StringBuffer responseBody = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            responseBody.append(inputLine);
        }

        JSONObject jsonResponse = new JSONObject(responseBody.toString());

        return jsonResponse.getInt("error") == 0;
    }
}
