package kr.ssapi.services.api;

import org.json.JSONObject;

/**
 * SSAPI 서버 응답 통합 모델.
 *
 * <p>성공 / 비즈니스 에러 / 통신 에러를 한 객체로 표현. 호출처는 isSuccess() 또는
 * reason 필드를 보고 분기.
 */
public class ApiResponse<T> {
    public final boolean success;
    public final int httpStatus;
    /** 서버가 내려준 비즈니스 에러 코드 (성공 시 null) */
    public final String errorCode;
    /** 서버 message (있는 경우) */
    public final String message;
    /** 응답 본문 객체 (성공 시) */
    public final T data;
    /** 에러 분류 (성공 시 null) */
    public final ErrorReason reason;
    /** 원본 응답 JSON (디버깅용) */
    public final JSONObject raw;

    public ApiResponse(boolean success, int httpStatus, String errorCode, String message,
                       T data, ErrorReason reason, JSONObject raw) {
        this.success = success;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
        this.reason = reason;
        this.raw = raw;
    }

    public boolean isSuccess() { return success; }

    public static <T> ApiResponse<T> success(int status, T data, JSONObject raw) {
        return new ApiResponse<>(true, status, null, null, data, null, raw);
    }

    public static <T> ApiResponse<T> businessError(int status, String errorCode, String message, JSONObject raw) {
        return new ApiResponse<>(false, status, errorCode, message, null, ErrorReason.BUSINESS_ERROR, raw);
    }

    public static <T> ApiResponse<T> systemError(int status, ErrorReason reason, String message) {
        return new ApiResponse<>(false, status, null, message, null, reason, null);
    }
}
