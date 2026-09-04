package com.flipcheck.nativebeta;

import org.junit.Test;

/** Runs all 106 preserved and exact-catalog regressions through Gradle's JVM test task. */
public final class V1251RegressionSuiteTest {
    @Test
    public void allDeclaredRegressionsPass() throws Exception {
        V122PhotographicIdentityArchitectureRegressionTest.main(new String[0]);
        V123CanonicalProfileRegressionTest.main(new String[0]);
        V124ProductionAliasEndToEndRegressionTest.main(new String[0]);
        V125HierarchicalRecoveryEndToEndRegressionTest.main(new String[0]);
        V126SemanticDecisionEndToEndRegressionTest.main(new String[0]);
        V127CatalogCompatibilityEndToEndRegressionTest.main(new String[0]);
        V128ExactCatalogResolutionEndToEndRegressionTest.main(new String[0]);
    }
}
