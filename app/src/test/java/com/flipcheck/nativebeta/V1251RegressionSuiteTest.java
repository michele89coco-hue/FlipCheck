package com.flipcheck.nativebeta;

import org.junit.Test;

/** Runs all 126 preserved and universal catalog-evidence regressions. */
public final class V1251RegressionSuiteTest {
    @Test
    public void allDeclaredRegressionsPass() throws Exception {
        run("v122",()->V122PhotographicIdentityArchitectureRegressionTest.main(new String[0]));
        run("v123",()->V123CanonicalProfileRegressionTest.main(new String[0]));
        run("v124",()->V124ProductionAliasEndToEndRegressionTest.main(new String[0]));
        run("v125",()->V125HierarchicalRecoveryEndToEndRegressionTest.main(new String[0]));
        run("v126",()->V126SemanticDecisionEndToEndRegressionTest.main(new String[0]));
        run("v127",()->V127CatalogCompatibilityEndToEndRegressionTest.main(new String[0]));
        run("v128",()->V128ExactCatalogResolutionEndToEndRegressionTest.main(new String[0]));
        run("v129",()->V129UniversalCatalogEvidenceRegressionTest.main(new String[0]));
    }
    private static void run(String name,Checked task)throws Exception{try{task.run();}catch(Throwable t){
        System.err.println("REGRESSION_SUITE_FAILED="+name+"; reason="+t.getMessage());
        throw new AssertionError(name+": "+t.getMessage(),t);
    }}
    private interface Checked{void run()throws Exception;}
}
