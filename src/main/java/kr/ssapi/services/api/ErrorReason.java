package kr.ssapi.services.api;

/**
 * API 호출 결과의 분류 (네트워크/시스템/비즈니스).
 *
 * <p>BUSINESS_ERROR 는 서버가 의도한 비즈니스 에러 (예: STREAMER_LIMIT_EXCEEDED).
 * 그 외는 통신/시스템 레벨의 에러.
 */
public enum ErrorReason {
    /** 네트워크 도달 불가 (UnknownHost, ConnectException 등) */
    NETWORK,

    /** 응답 시간 초과 (SocketTimeoutException 등) */
    TIMEOUT,

    /** 인증/권한 실패 (HTTP 401/403) */
    AUTH,

    /** HTTP 5xx 서버 오류 */
    HTTP_ERROR,

    /** JSON 파싱 실패 / 예상 필드 없음 */
    SCHEMA,

    /** 서버 측 의도된 비즈니스 에러 (error_code 보유) */
    BUSINESS_ERROR,

    /** 분류 불가 */
    UNKNOWN
}
