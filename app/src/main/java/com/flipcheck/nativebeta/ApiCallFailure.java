package com.flipcheck.nativebeta;

import org.json.JSONObject;

/** Safe transport diagnostics: never retain response messages, bodies or credentials. */
final class ApiCallFailure extends Exception {
    final int httpStatus;
    final String errorCode;
    long retryAfterMillis = 2000;
    ApiCallFailure(int status, String code) {
        super("HTTP_" + status + ":" + allowedCode(code));
        httpStatus = status;
        errorCode = allowedCode(code);
    }
    static ApiCallFailure fromResponse(int status, String body) {
        String code = "";
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error != null) {
                code = allowedCode(error.optString("code", ""));
                if ("unspecified".equals(code)) code = allowedCode(error.optString("type", ""));

            }
        } catch (Exception ignored) { /* Non-JSON errors still preserve HTTP status. */ }
        if ("unspecified".equals(allowedCode(code))) {
            // Also handles string-valued error, top-level message, or gateway text.
            // Only the category survives: never store any portion of the body.
            String message = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
            if (message.contains("exceeded your current quota") || message.contains("insufficient quota")
                    || message.contains("quota exceeded") || message.contains("credit balance")
                    || message.contains("insufficient credits") || message.contains("billing hard limit")) code="insufficient_quota";
            else if (message.contains("rate limit") || message.contains("rate_limit")
                    || message.contains("too many requests") || message.contains("tokens per minute")
                    || message.contains("requests per minute")) code="rate_limit_exceeded";
        }
        return new ApiCallFailure(status, code);
    }
    String domain() {
        if ("insufficient_quota".equals(errorCode)) return "API_QUOTA_EXHAUSTED";
        if (httpStatus == 401) return "API_AUTHENTICATION_ERROR";
        if (httpStatus == 403) return "API_PERMISSION_ERROR";
        if (httpStatus == 429) return "rate_limit_exceeded".equals(errorCode) ? "API_RATE_LIMIT" : "API_429_UNCLASSIFIED";
        if (httpStatus >= 500) return "API_SERVER_ERROR";
        return "API_REQUEST_ERROR";
    }
    boolean retryable() {
        return retryAfterMillis <= 30000 && !"insufficient_quota".equals(errorCode)
                && (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500);
    }
    ApiCallFailure withRetryAfter(String header) {
        if (header == null || header.trim().isEmpty()) return this;
        try {
            double seconds = Double.parseDouble(header.trim());
            if (Double.isFinite(seconds) && seconds >= 0) retryAfterMillis = (long)Math.ceil(Math.min(seconds, 86400) * 1000);
        } catch (NumberFormatException ignored) {
            try { retryAfterMillis = Math.max(0, java.time.ZonedDateTime.parse(header.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()); }
            catch (Exception invalidDate) { /* Keep bounded default for malformed header. */ }
        }
        return this;
    }
    static boolean stopsLiveSuite(String domain) { return domain != null && domain.startsWith("API_"); }
    private static String allowedCode(String code) {
        if (code == null) return "unspecified";
        switch (code) {
            case "billing_hard_limit_reached": case "usage_limit_reached": return "insufficient_quota";
            case "rate_limit_error": case "tokens": case "requests": return "rate_limit_exceeded";
            case "insufficient_quota": case "rate_limit_exceeded": case "invalid_api_key":
            case "invalid_json_schema": case "model_not_found": case "unsupported_parameter":
            case "invalid_value": case "server_error": return code;
            default: return "unspecified";
        }
    }
}
