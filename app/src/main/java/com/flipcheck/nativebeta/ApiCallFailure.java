package com.flipcheck.nativebeta;

import org.json.JSONObject;

/** Safe transport diagnostics: never retain response messages, bodies or credentials. */
final class ApiCallFailure extends Exception {
    final int httpStatus;
    final String errorCode;
    ApiCallFailure(int status, String code) {
        super("HTTP_" + status + ":" + allowedCode(code));
        httpStatus = status;
        errorCode = allowedCode(code);
    }
    static ApiCallFailure fromResponse(int status, String body) {
        String code = "";
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error != null) code = error.optString("code", "");
        } catch (Exception ignored) { /* Non-JSON errors still preserve HTTP status. */ }
        return new ApiCallFailure(status, code);
    }
    String domain() {
        if ("insufficient_quota".equals(errorCode)) return "API_QUOTA_EXHAUSTED";
        if (httpStatus == 401) return "API_AUTHENTICATION_ERROR";
        if (httpStatus == 403) return "API_PERMISSION_ERROR";
        if (httpStatus == 429) return "API_RATE_LIMIT";
        if (httpStatus >= 500) return "API_SERVER_ERROR";
        return "API_REQUEST_ERROR";
    }
    boolean retryable() {
        return !"insufficient_quota".equals(errorCode)
                && (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500);
    }
    private static String allowedCode(String code) {
        if (code == null) return "unspecified";
        switch (code) {
            case "insufficient_quota": case "rate_limit_exceeded": case "invalid_api_key":
            case "invalid_json_schema": case "model_not_found": case "unsupported_parameter":
            case "invalid_value": case "server_error": return code;
            default: return "unspecified";
        }
    }
}
