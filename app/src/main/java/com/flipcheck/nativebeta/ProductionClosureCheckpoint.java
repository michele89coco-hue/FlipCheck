package com.flipcheck.nativebeta;

/** Persistent production diagnostics for the universal closure attempt. */
final class ProductionClosureCheckpoint {
    private ProductionClosureCheckpoint() {}

    static boolean attempt(Models.Identification id,String stage) {
        boolean closed=UniversalIdentityClosure.apply(id,stage);
        record(id,stage,closed);
        return closed;
    }

    static void record(Models.Identification id,String stage,boolean closed) {
        if(id==null)return;
        id.closureAttempt=true;
        id.closureResult=closed;
        id.closureStage=stage==null?"":stage.trim();
        id.closureMissingFields=closed?"":UniversalIdentityClosure.missingFields(id);
        add(id,"closure_attempt=true");
        add(id,"closure_result="+closed);
        add(id,"closure_stage="+id.closureStage);
        if(!closed)add(id,"closure_missing_fields="+id.closureMissingFields);
        add(id,"identity_status="+id.identityStatus);
        add(id,"closure_basis="+id.closureBasis);
        if(!id.blockingReason.isEmpty())add(id,"blocking_reason="+id.blockingReason);
        add(id,"missing_discriminative_fields="+id.missingDiscriminativeFields);
        add(id,"missing_nonblocking_fields="+id.missingNonblockingFields);
        ConsistencyInvariantChecker.enforce(id,"checkpoint_"+id.closureStage);
    }

    private static void add(Models.Identification id,String value){if(!id.observedEvidence.contains(value))id.observedEvidence.add(value);}
}
