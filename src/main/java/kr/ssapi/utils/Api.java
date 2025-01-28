package kr.ssapi.utils;

import kr.ssapi.config.Config;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Api {
    private static final String API_URL;
    private static final String API_KEY;

    static {
        Config config = Config.getInstance();
        API_URL = config.getApiServer();
        API_KEY = config.getApiKey();
    }

    public static String getSoopUserNick(String id) throws Exception {
        URL url = new URL("https://chapi.sooplive.co.kr/api/" + id + "/station/");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Whale/3.28.266.11 Safari/537.36");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder responseBody = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            responseBody.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(responseBody.toString());
        return jsonResponse.getJSONObject("station").getString("user_nick");
    }

    public static String getChzzkChannelInfo(String id) throws Exception {
        URL url = new URL("https://api.chzzk.naver.com/service/v1/channels/" + id);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Whale/3.28.266.11 Safari/537.36");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder responseBody = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            responseBody.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(responseBody.toString());
        return jsonResponse.getJSONObject("content").getString("channelName");
    }

    public static Object connectApi(String platform, String userId) throws Exception {
        URL url = new URL(API_URL + "/room/user");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("PUT");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("Authorization", "Bearer " + API_KEY);
        con.setDoOutput(true);

        String requestBody = String.format("{\"platform\": \"%s\", \"user\": \"%s\"}", platform, userId);
        con.getOutputStream().write(requestBody.getBytes("UTF-8"));

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder responseBody = new StringBuilder();
        String inputLine;
        
        while ((inputLine = in.readLine()) != null) {
            responseBody.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(responseBody.toString());
        int error = jsonResponse.getInt("error");
        
        if (error == 0) {
            String successMessage = Config.getInstance().getMessageConnectSuccess();
            return successMessage.isEmpty() ? true : successMessage;
        } else {
            String errorMessage = jsonResponse.getString("message");
            String failMessage = Config.getInstance().getMessageConnectFail()
                    .replace("{message}", errorMessage);
            return failMessage.isEmpty() ? errorMessage : failMessage;
        }
    }

    public static Object disconnectApi(String platform, String userId) throws Exception {
        URL url = new URL(API_URL + "/room/user");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("DELETE");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setRequestProperty("Authorization", "Bearer " + API_KEY);
        con.setDoOutput(true);

        String requestBody = String.format("{\"platform\": \"%s\", \"user\": \"%s\"}", platform, userId);
        con.getOutputStream().write(requestBody.getBytes("UTF-8"));

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder responseBody = new StringBuilder();
        String inputLine;
        
        while ((inputLine = in.readLine()) != null) {
            responseBody.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(responseBody.toString());
        int error = jsonResponse.getInt("error");
        
        if (error == 0) {
            String successMessage = Config.getInstance().getMessageDisconnectSuccess();
            return successMessage.isEmpty() ? true : successMessage;
        } else {
            String errorMessage = jsonResponse.getString("message");
            String failMessage = Config.getInstance().getMessageDisconnectFail()
                    .replace("{message}", errorMessage);
            return failMessage.isEmpty() ? errorMessage : failMessage;
        }
    }
} 