package com.flipcheck.nativebeta;

import java.util.Locale;

/** Describes a discriminator but never authorizes an automatic remote retry. */
final class DiscriminativeVisionPolicy {
    private DiscriminativeVisionPolicy() {}
    static boolean shouldRun(Models.Identification id,Models.Usage usage){return false;}
    static boolean serialTarget(Models.Identification id){String x=safe(id==null?"":id.discriminativeField).toLowerCase(Locale.ROOT);return x.contains("serial")||x.contains("print_run")||x.contains("tiratura")||x.contains("parallel");}
    static String reason(Models.Identification id){return "two_or_more_materially_distinct_candidates; missing_discriminator="+safe(id.discriminativeField)+"; field_not_visible=true";}
    static String prompt(Models.Identification id){return "FOCUSED DISCRIMINATIVE VISION 2. Inspect the SAME supplied images only for MISSING_DISCRIMINATOR="+safe(id.discriminativeField)+". "
            +"The first observation reports at least two materially different candidates. Do not re-identify the object and do not use Web knowledge. "
            +"Return only physically visible evidence with precise image, side, location and semantic role. If the field is still not readable, leave it unresolved.\n"
            +PhysicalIdentityRecovery.prompt(id);}
    static String request(Models.Identification id){return PhotographicIdentityClosure.targetedPhotoRequest(id);}
    private static String safe(String x){return x==null?"":x.trim();}
}
