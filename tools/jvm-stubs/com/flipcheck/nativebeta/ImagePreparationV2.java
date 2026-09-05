package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;

/** JVM-only shape stub; Android tests and APK builds compile the production cropper. */
final class ImagePreparationV2 {
    static final class Prepared {
        final List<String> images = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        String cropId = "jvm-replay";
    }

    private ImagePreparationV2() {}
    static Prepared reviewAll(List<String> originals,DomainProfileRouterV2.Profile profile){
        return focused(originals,profile,"review154");
    }

    static Prepared focused(List<String> originals, DomainProfileRouterV2.Profile profile, String discriminator) {
        Prepared prepared = new Prepared();
        if (originals != null) prepared.images.addAll(originals);
        prepared.trace.add("jvm_replay_no_bitmap_transform");
        return prepared;
    }
}
