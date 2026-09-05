package com.flipcheck.nativebeta;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class V142ApiFailureTest {
 @Test public void quotaIsNotTransientRateLimit() {
  ApiCallFailure quota=ApiCallFailure.fromResponse(429,"{\"error\":{\"code\":\"insufficient_quota\"}}");
  assertEquals("API_QUOTA_EXHAUSTED",quota.domain()); assertFalse(quota.retryable());
  assertTrue(new ApiCallFailure(429,"rate_limit_exceeded").retryable());
 }
 @Test public void nonJsonHttpErrorKeepsStatusWithoutBody() {
  ApiCallFailure f=ApiCallFailure.fromResponse(502,"<html>private gateway text</html>");
  assertEquals(502,f.httpStatus);assertEquals("API_SERVER_ERROR",f.domain());
  assertFalse(f.getMessage().contains("private"));
 }
 @Test public void responseCannotInjectSecretsIntoDiagnostics() {
  ApiCallFailure f=ApiCallFailure.fromResponse(401,"{\"error\":{\"code\":\"secret_marker\",\"message\":\"secret_marker\"}}");
  assertFalse(f.getMessage().contains("secret_marker"));assertFalse(f.retryable());
 }
 @Test public void invalidSchemaIsNotRetriedUnchanged() {
  assertFalse(new ApiCallFailure(400,"invalid_json_schema").retryable());
 }
 static final class FailingClient extends OpenAiClient {
  int calls;
  final ApiCallFailure failure;
  FailingClient(ApiCallFailure failure){super("");this.failure=failure;}
  @Override Response observe(List<String> images,String prompt)throws Exception{calls++;throw failure;}
  @Override Response observeTechnicalRecovery(List<String> images,String prompt)throws Exception{calls++;throw failure;}
 }
 @Test public void pipelinePreservesQuotaAndSkipsFutileRetry()throws Exception {
  FailingClient c=new FailingClient(new ApiCallFailure(429,"insufficient_quota"));
  Models.Identification id=UniversalIdentityEngineV2.identify(null,Arrays.asList("test-image"),"",c,new Models.Usage());
  assertEquals(1,c.calls);assertEquals(0,id.technicalRetryCount);
  assertEquals("API_QUOTA_EXHAUSTED",id.pipelineFailureDomain);
  assertTrue(id.v2CallReasons.contains("HTTP_429:insufficient_quota"));assertFalse(id.identityConfirmed);
 }
 @Test public void transientFailureGetsOnlyOneRecordedRetry()throws Exception {
  FailingClient c=new FailingClient(new ApiCallFailure(503,"server_error"));
  Models.Identification id=UniversalIdentityEngineV2.identify(null,Arrays.asList("test-image"),"",c,new Models.Usage());
  assertEquals(2,c.calls);assertEquals(1,id.technicalRetryCount);
  assertTrue(id.v2CallReasons.contains("technical_retry:API_SERVER_ERROR"));
 }
}
