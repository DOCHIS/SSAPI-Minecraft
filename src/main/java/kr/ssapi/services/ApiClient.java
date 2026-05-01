package kr.ssapi.services;

import kr.ssapi.services.api.ApiResponse;
import kr.ssapi.services.api.ErrorReason;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SSAPI REST API 클라이언트 — GET / PUT / DELETE / POST 를 래핑.
 *
 * <p>연결 타임아웃 5초, 읽기 타임아웃 8초. 모든 오류는 ApiResponse 로 래핑해 반환.
 * 인증은 Bearer 토큰(api.key) 사용.
 */
public class ApiClient {
    private static final Logger LOGGER = Logger.getLogger("SSApi-ApiClient");
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    private final String baseUrl;
    private final String apiKey;
    private final FileLogService fileLogs;

    public ApiClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, null);
    }

    public ApiClient(String baseUrl, String apiKey, FileLogService fileLogs) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.fileLogs = fileLogs;
    }

    public ApiResponse<JSONObject> get(String path) {
        return request("GET", path, null);
    }

    public ApiResponse<JSONObject> put(String path, JSONObject body) {
        return request("PUT", path, body);
    }

    public ApiResponse<JSONObject> delete(String path, JSONObject body) {
        return request("DELETE", path, body);
    }

    public ApiResponse<JSONObject> post(String path, JSONObject body) {
        return request("POST", path, body);
    }

    // HTTP 요청 공통 처리: 헤더 설정, body 전송, 응답 파싱, 오류 분류
    private ApiResponse<JSONObject> request(String method, String path, JSONObject body) {
        HttpURLConnection conn = null;
        long start = System.nanoTime();
        int status = 0;
        String responseBody = "";
        try {
            URL url = new URI(baseUrl + path).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            if (body != null) {
                conn.setDoOutput(true);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }

            status = conn.getResponseCode();

            // 4xx/5xx 시 errorStream 사용
            InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
            responseBody = readAll(is);

            // HTTP 상태 분기
            if (status == 401 || status == 403) {
                return finish(method, path, body, start, responseBody,
                    ApiResponse.systemError(status, ErrorReason.AUTH, "auth failed"));
            }
            if (status >= 500) {
                return finish(method, path, body, start, responseBody,
                    ApiResponse.systemError(status, ErrorReason.HTTP_ERROR, "server error " + status));
            }

            // body 파싱
            JSONObject json;
            try {
                json = new JSONObject(responseBody == null ? "{}" : responseBody);
            } catch (JSONException e) {
                LOGGER.log(Level.WARNING, "ApiClient: schema error - response not JSON: "
                        + (responseBody == null ? "(null)" : responseBody.substring(0, Math.min(200, responseBody.length()))));
                return finish(method, path, body, start, responseBody,
                    ApiResponse.systemError(status, ErrorReason.SCHEMA, "non-json response"));
            }

            // SSAPI 응답 형식: { error: 0, ... } 성공 / { error: <code>, error_code: "...", message: "..." } 실패
            int err = json.optInt("error", 0);
            if (err == 0) {
                return finish(method, path, body, start, responseBody,
                    ApiResponse.success(status, json, json));
            }

            String errorCode = json.optString("error_code", null);
            String message = json.optString("message", "");
            return finish(method, path, body, start, responseBody,
                ApiResponse.businessError(status, errorCode, message, json));

        } catch (SocketTimeoutException e) {
            return finish(method, path, body, start, responseBody,
                ApiResponse.systemError(status, ErrorReason.TIMEOUT, e.getMessage()));
        } catch (UnknownHostException e) {
            return finish(method, path, body, start, responseBody,
                ApiResponse.systemError(status, ErrorReason.NETWORK, e.getMessage()));
        } catch (IOException e) {
            // ConnectException 등 네트워크 오류
            return finish(method, path, body, start, responseBody,
                ApiResponse.systemError(status, ErrorReason.NETWORK, e.getMessage()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return finish(method, path, body, start, responseBody,
                ApiResponse.systemError(status, ErrorReason.UNKNOWN, "invalid url: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ApiClient unexpected error", e);
            return finish(method, path, body, start, responseBody,
                ApiResponse.systemError(status, ErrorReason.UNKNOWN, e.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private ApiResponse<JSONObject> finish(String method, String path, JSONObject body, long start,
                                           String responseBody, ApiResponse<JSONObject> response) {
        if (fileLogs != null) {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            fileLogs.logApiCall(method, baseUrl, path, body, response, durationMs, responseBody);
        }
        return response;
    }

    // InputStream 의 내용을 UTF-8 문자열로 읽어 반환
    private String readAll(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
