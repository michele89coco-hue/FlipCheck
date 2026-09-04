package com.flipcheck.nativebeta;

import org.junit.Test;

/** Makes the main-style deterministic replay gates executable by Gradle/JUnit. */
public final class V133ReleaseGateJUnitTest {
    @Test
    public void v132AndV133DeterministicGatesPass() throws Exception {
        V132UniversalIdentityEngineV2RegressionTest.main(new String[0]);
        V133LiveValidatedGlobalResolverRegressionTest.main(new String[0]);
    }
}
