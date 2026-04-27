package kr.ssapi.services;

import kr.ssapi.services.api.ApiResponse;
import kr.ssapi.services.api.ErrorReason;

import java.util.HashMap;
import java.util.Map;

/**
 * API 응답 오류를 사용자 노출 메시지 문자열로 변환.
 *
 * <p>시스템 오류(네트워크/타임아웃/인증 등)는 messages.yml 의 고정 키로,
 * 비즈니스 오류(errorCode)는 {@code api.connect.error.<code>} 키로 우선 조회.
 */
public class ApiErrorMapper {
    private final MessageService messages;

    public ApiErrorMapper(MessageService messages) {
        this.messages = messages;
    }

    // ApiResponse 의 reason/errorCode 를 분석해 사용자에게 보여줄 메시지 반환
    public String resolveReason(ApiResponse<?> response) {
        if (response.reason == ErrorReason.NETWORK) return messages.getRaw("api.error.network");
        if (response.reason == ErrorReason.TIMEOUT) return messages.getRaw("api.error.timeout");
        if (response.reason == ErrorReason.AUTH)    return messages.getRaw("api.error.auth");
        if (response.reason == ErrorReason.SCHEMA)  return messages.getRaw("api.error.schema");
        if (response.reason == ErrorReason.HTTP_ERROR) {
            Map<String, String> ph = new HashMap<>();
            ph.put("code", String.valueOf(response.httpStatus));
            return formatRaw("api.error.server", ph);
        }

        if (response.errorCode != null) {
            String key = "api.connect.error." + response.errorCode.toLowerCase();
            if (messages.has(key)) return messages.getRaw(key);
        }

        return response.message != null ? response.message : "";
    }

    private String formatRaw(String key, Map<String, String> placeholders) {
        String raw = messages.getRaw(key);
        if (raw.isEmpty()) return "";
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            raw = raw.replace("<" + e.getKey() + ">", e.getValue() == null ? "" : e.getValue());
        }
        return raw;
    }
}
