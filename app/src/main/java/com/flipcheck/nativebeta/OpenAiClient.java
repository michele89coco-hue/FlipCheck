package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

class OpenAiClient {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-5.6-luna";
    private final String apiKey;

    OpenAiClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    boolean hasKey() {
        return !this.apiKey.isEmpty();
    }

    static final class Response {
        JSONObject payload;
        JSONObject raw;
        boolean complete = true;
        String incompleteReason = "";
        String parseError = "";
        String refusal = "";
        /** COMPLETED, INCOMPLETE_MAX_TOKENS, INVALID_JSON, TIMEOUT, NETWORK_ERROR or CONTENT_INSUFFICIENT. */
        String technicalStatus = "COMPLETED";
        final List<Models.Source> sources = new ArrayList();
        final List<String> queries = new ArrayList();
        Models.Usage usage = new Models.Usage();

        Response() {
        }
    }

    Response vision(List<String> imageDataUrls, String prompt) throws Exception {
        JSONArray content = new JSONArray().put(new JSONObject().put("type", "input_text").put("text", "FLIPCHECK UNIVERSAL EVIDENCE SEMANTICS. Treat every input as an arbitrary physical product. Keep manufacturer/brand, product family, exact model/reference, edition/variant and other attributes separate when the evidence supports them. Never create a category-specific field unless it is genuinely observed or returned by a grounded source. Missing information is UNKNOWN, not a match and not a contradiction. Literal OCR, logo, barcode and labelled identifiers are observations. Visual guesses are hypotheses only. A visual resemblance can establish family similarity but cannot by itself prove an exact variant/model when multiple variants share the same appearance.\n\n" + prompt));
        for (String image : imageDataUrls) {
            content.put(new JSONObject().put("type", "input_image").put("image_url", image).put("detail", "high"));
        }
        JSONArray input = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false).put("max_output_tokens", 1700).put("reasoning", new JSONObject().put("effort", "low")).put("input", input);
        return call(body, true);
    }

    /**
     * Single, schema-locked visual observation used by the v0.77 pipeline.
     * This is intentionally separate from the legacy free-form vision method:
     * retrieval must never start from a partial or shape-shifted payload.
     */
    Response observe(List<String> imageDataUrls, String prompt) throws Exception {
        return observeCompact(imageDataUrls, prompt, 1150, false);
    }

    /** Technical retry: same images, compact schema, larger output allowance, no Web. */
    Response observeTechnicalRecovery(List<String> imageDataUrls, String prompt) throws Exception {
        return observeCompact(imageDataUrls, prompt, 2300, true);
    }

    /** One discriminator-only visual pass over profile-specific crops. */
    Response observeFocusedV2(List<String> imageDataUrls, String prompt) throws Exception {
        return observeCompact(imageDataUrls, "FOCUSED DISCRIMINATOR ONLY. Do not restate the whole identity. "
                +"Transcribe a value as a fact only when you can provide its exact supplied image/crop and location. "
                +"A brand guessed from shape belongs only in candidates. "+prompt, 900, false);
    }

    private Response observeCompact(List<String> imageDataUrls, String prompt,
                                    int maxOutputTokens, boolean retry) throws Exception {
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text")
                .put("text", "FLIPCHECK COMPACT UNIVERSAL PHOTO OBSERVER. "
                        + (retry ? "This is an automatic TECHNICAL retry after a truncated or invalid response. " : "")
                        + "Inspect every supplied image of the same foreground object. Return only the compact JSON schema. "
                        + "Do not repeat facts in narrative fields. Every observed fact belongs exactly once in facts with key, literal value, image, side, location, role and confidence. "
                        + "Use category values sports_card, tcg, sealed_trading_card_product, consumer_electronics, consumer_electronics_accessory, television_remote_control, audio_video_remote_control, appliance_remote_control, smartphone, controller, appliance, tool, other_collectible. "
                        + "Keep card number, collector number, serial/print run, HP/PV, ratings, statistics, jersey number, year, activation code, MODEL/P-N and barcode semantically separate. "
                        + "People pictured on sealed packaging are featured_subject, never the product subject. Preserve discriminating product-line tokens. "
                        + "For remote controls and electronics, emit product_type, brand_mark only for a located visible logo, control_layout, shortcut_buttons, navigation_layout, numeric_keypad and voice_control. Shape-only brand guesses belong only in candidates. "
                        + "Set content_sufficient=false only when the images themselves do not expose usable object evidence. "
                        + "Candidates are photographic hypotheses and must use only the supplied images.\n\n"
                        + prompt));
        for (String image : imageDataUrls) {
            content.put(new JSONObject().put("type", "input_image")
                    .put("image_url", image).put("detail", "high"));
        }
        JSONArray input = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", maxOutputTokens)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", compactObserverFormat()).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    /**
     * v0.82 performs observation, retrieval and visual adjudication inside one
     * multimodal Responses request. Keeping the original photo in the same
     * request as web_search prevents a lossy text-only hand-off from turning
     * generic similarities into a brand/model shortlist.
     */
    Response resolveMultimodal(List<String> imageDataUrls, String prompt) throws Exception {
        String policy = "FLIPCHECK v0.82 MULTIMODAL PRODUCT RESOLUTION. "
                + "Use at most one web_search tool call. First bound and observe the foreground physical product from the supplied image(s). "
                + "If the image is unusable, set observation_valid=false and do not search. Otherwise search, compare and source-check concrete candidates in the same request. "
                + "The photographed geometry remains primary evidence throughout resolution. Do not reduce it to category-level similarity. "
                + "The first query must always be brand-neutral and domain-unrestricted, even when the visual model believes it sees a logo. "
                + "A manufacturer may enter only a later query and only when its literal text is independently corroborated by the supplied local-OCR evidence. "
                + "Guessed brands/models must not become site: filters or namespace anchors. No query may contain a manufacturer absent from the physical observation or from a grounded result reached by the neutral query. Non-binding hypotheses cannot exclude unseen manufacturers. "
                + "Transient readouts and generic controls are forbidden query terms. Stable distinctive printed controls may be used only as quoted co-occurrence fingerprints, never as identifiers. "
                + "Prefer manufacturer-owned product, support, catalog and manual sources. Do not search price, resale value or marketplaces. "
                + "For each candidate actively compare shape, proportions, control topology, number and position of elements, connectors/openings and distinctive labels against the photo. "
                + "visual_reference_checked=true only when a retrieved source or search result actually exposes an image/diagram tied to that exact reference and you compared it to the supplied photo. "
                + "major_geometry_conflict=true for any identity-bearing layout conflict; such a candidate cannot lead even if its page is official. "
                + "exact_reference_complete=true only when the retrieved source prints the complete model/reference including suffixes. "
                + "The model field must be empty whenever exact_reference_complete=false. A source-backed product family is still a valid partial candidate and belongs in family. "
                + "probable_reference is separate from model: use it only for a complete source-printed reference whose physical topology is a plausible match but is not proven exact; otherwise leave it empty. "
                + "photo_identity_supported=true when the source/checklist supports the base identity tuple carried by the complete physically bound photo_identity; return every source-matched field explicitly. A physically printed specimen serial or print-run fraction does not require a web listing for that individual copy. "
                + "Missing proof, an unavailable reference or an unresolved suffix is an evidence gap, never a contradiction and never part of brand/family/model text. "
                + "A direct product page proves that the model exists, not that the photographed object is that model. Missing evidence is UNKNOWN. "
                + "Return only the requested strict schema.";
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", policy + "\n\n" + prompt));
        if (imageDataUrls != null) {
            for (String image : imageDataUrls) {
                content.put(new JSONObject().put("type", "input_image")
                        .put("image_url", image).put("detail", "high"));
            }
        }
        JSONArray input = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 2600)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("max_tool_calls", 1)
                .put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")
                        .put("search_context_size", "medium")))
                .put("include", new JSONArray().put("web_search_call.action.sources")
                        .put("web_search_call.results"))
                .put("text", new JSONObject().put("format", multimodalResolveFormat()).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    /** Selective no-web visual adjudication for an evidence-rich near miss. */
    Response adjudicateBorderline(List<String> imageDataUrls, String prompt) throws Exception {
        JSONObject props = new JSONObject()
                .put("supported", new JSONObject().put("type", "boolean"))
                .put("same_entity", new JSONObject().put("type", "boolean"))
                .put("contradiction", new JSONObject().put("type", "boolean"))
                .put("identity_confidence", integerSchema(0, 100))
                .put("normalized_identity", new JSONObject().put("type", "string"))
                .put("reason", new JSONObject().put("type", "string"));
        JSONObject format = jsonFormat("flipcheck_borderline_identity_v099",
                strictObject(props, "supported", "same_entity", "contradiction",
                        "identity_confidence", "normalized_identity", "reason"));
        String policy = "FLIPCHECK v1.03 BOUNDED VISUAL IDENTITY ADJUDICATOR. NO WEB. "
                + "Reinspect the supplied foreground card/object and compare only its physically visible identity fields with the supplied grounded candidate and source snippets. "
                + "Set supported=true only when all identity-bearing fields are mutually coherent, the candidate denotes the same physical entity, the source already names the exact commercial reference, and no discriminator conflicts. "
                + "Missing proof is unresolved, never a match. Do not infer a brand/model from generic controls or shape. "
                + "For TCG cards distinguish collector number x/y from character/Pokedex numbers; for sports cards preserve player, season/set, card number, parallel, rookie marker and any physical serial. "
                + "Preserve printed edition, parallel, finish, rookie and format axes in normalized_identity. JSON only.\n\n";
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", policy + prompt));
        if (imageDataUrls != null) {
            for (String image : imageDataUrls) {
                content.put(new JSONObject().put("type", "input_image")
                        .put("image_url", image).put("detail", "high"));
            }
        }
        JSONArray input = new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 550)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", format).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    /** Selective second visual read for structured fields omitted by the first pass. */
    Response recoverPhysicalIdentity(List<String> imageDataUrls, String prompt) throws Exception {
        JSONObject recoveryFactProps=new JSONObject()
                .put("key",new JSONObject().put("type","string"))
                .put("value",new JSONObject().put("type","string"))
                .put("evidence_type",new JSONObject().put("type","string"))
                .put("confidence",integerSchema(0,100))
                .put("image_index",integerSchema(0,12))
                .put("side",new JSONObject().put("type","string"))
                .put("location",new JSONObject().put("type","string"))
                .put("semantic_role",new JSONObject().put("type","string"));
        JSONObject recoveryFact=strictObject(recoveryFactProps,"key","value","evidence_type",
                "confidence","image_index","side","location","semantic_role");
        JSONObject props = new JSONObject()
                .put("applicable", new JSONObject().put("type", "boolean"))
                .put("same_foreground_object", new JSONObject().put("type", "boolean"))
                .put("physical_binding", new JSONObject().put("type", "boolean"))
                .put("overlay_or_watermark", new JSONObject().put("type", "boolean"))
                .put("external_watermark", new JSONObject().put("type", "boolean"))
                .put("identity_obscured", new JSONObject().put("type", "boolean"))
                .put("complete", new JSONObject().put("type", "boolean"))
                .put("ambiguity_resolved",new JSONObject().put("type","boolean"))
                .put("discriminative_field_visible",new JSONObject().put("type","boolean"))
                .put("category_key", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("loose_card").put("sealed_box").put("other")))
                .put("canonical_name", new JSONObject().put("type", "string"))
                .put("confidence", integerSchema(0, 100))
                .put("fields", stringArraySchema(14))
                .put("evidence_facts",new JSONObject().put("type","array").put("maxItems",20)
                        .put("items",recoveryFact))
                .put("observed_labels", stringArraySchema(12))
                .put("contradiction", new JSONObject().put("type", "string"));
        JSONObject format = jsonFormat("flipcheck_physical_recovery_v101",
                strictObject(props, "applicable", "same_foreground_object", "physical_binding",
                        "overlay_or_watermark", "external_watermark", "identity_obscured",
                        "complete", "ambiguity_resolved","discriminative_field_visible", "category_key", "canonical_name",
                        "confidence", "fields", "evidence_facts", "observed_labels", "contradiction"));
        String policy = "FLIPCHECK v1.01 SELECTIVE PHYSICAL FIELD RECOVERY. NO WEB. "
                + "Reinspect only the supplied foreground object and transcribe fields physically visible on it. "
                + "For every recovered field emit evidence_facts with the literal key/value, semantic role, supplied image_index and an exact surface location. An unlocalized summary must not be represented as a physical number or serial. "
                + "Do not use a candidate name, user hint or prior hypothesis as visual evidence. "
                + "For a loose card recover manufacturer/publisher, set or season when physically printed, subject/player, card number, parallel, rookie marker, finish and edition/printing cues. A card number is valid only when explicitly labeled and localized: return physical_card_number_marking plus card_number_binding=physical_card_surface, card_number_semantic=card_number or collector_number, and card_number_location. Ratings, stats, HP/PV, years, activation codes, graphic/star numbers and external UI/OCR are not card numbers. "
                + "For a sealed box recover manufacturer, season/year, exact product line, sport, Hobby/Blaster/Retail format and printed pack/autograph configuration. "
                + "For electronics and other products focus on DECISIVE_MISSING_FIELD: physical model/part code, valid EAN/UPC/GTIN, rear label, family, and independent physical attributes. "
                + "A barcode is usable only when printed on the foreground object or its genuine packaging and visually/category coherent; reject overlays, screens, watermarks and web images. "
                + "overlay_or_watermark may describe glare, printed graphics, composite markings or UI chrome and is not by itself an identity veto. Set external_watermark=true only for text/graphics added outside the photographed object; set identity_obscured=true only when that external mark covers decisive identity data. "
                + "Set complete=true only when the photographed surface supplies every commercial discriminator. "
                + "If a required word is unreadable, leave it absent. JSON only.\n\n";
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", policy + prompt));
        if (imageDataUrls != null) {
            for (String image : imageDataUrls) {
                content.put(new JSONObject().put("type", "input_image")
                        .put("image_url", image).put("detail", "high"));
            }
        }
        JSONArray input = new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 650)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", format).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    /** Mandatory second physical read for a sports-card serial/parallel axis. No Web. */
    Response verifySportsCardPhysicalDetails(List<String> imageDataUrls, String prompt) throws Exception {
        JSONObject props=new JSONObject()
                .put("same_physical_card",new JSONObject().put("type","boolean"))
                .put("physical_serial_marking",new JSONObject().put("type","string"))
                .put("serial_binding",new JSONObject().put("type","string"))
                .put("serial_area_clear",new JSONObject().put("type","boolean"))
                .put("serial_location",new JSONObject().put("type","string"))
                .put("parallel_location",new JSONObject().put("type","string"))
                .put("image_index",integerSchema(0,12))
                .put("physical_parallel",new JSONObject().put("type","string"))
                .put("parallel_color",new JSONObject().put("type","string"))
                .put("finish",new JSONObject().put("type","string"))
                .put("reason",new JSONObject().put("type","string"));
        JSONObject format=jsonFormat("flipcheck_sports_physical_detail_v120",
                strictObject(props,"same_physical_card","physical_serial_marking",
                        "serial_binding","serial_area_clear","serial_location",
                        "parallel_location","image_index","physical_parallel",
                        "parallel_color","finish","reason"));
        String policy="FLIPCHECK v1.20 SPORTS PHYSICAL DETAIL VERIFICATION. NO WEB. "
                +"Inspect only the supplied photos/crops of the same physical sports card. "
                +"A rating, stat, checklist result or prior OCR guess is never a serial. "
                +"Copy an x/y serial only from the card surface and set serial_binding=physical_card_surface. "
                +"Return the supplied image_index and exact serial_location/parallel_location on the card surface. "
                +"If both digits are not clear, return an empty physical_serial_marking and serial_area_clear=false. "
                +"Return parallel/color/finish only when physically visible. JSON only.\n\n";
        JSONArray content=new JSONArray().put(new JSONObject().put("type","input_text")
                .put("text",policy+prompt));
        if(imageDataUrls!=null)for(String image:imageDataUrls)content.put(new JSONObject()
                .put("type","input_image").put("image_url",image).put("detail","high"));
        JSONArray input=new JSONArray().put(new JSONObject().put("role","user").put("content",content));
        JSONObject body=new JSONObject().put("model",MODEL).put("store",false)
                .put("max_output_tokens",450).put("reasoning",new JSONObject().put("effort","medium"))
                .put("text",new JSONObject().put("format",format).put("verbosity","low"))
                .put("input",input);
        return call(body,true,true);
    }

    /** Focused no-Web inspection of TCG edition/printing/finish regions. */
    Response verifyTcgPhysicalEdition(List<String> imageDataUrls, String prompt) throws Exception {
        JSONObject props=new JSONObject()
                .put("same_card",new JSONObject().put("type","boolean"))
                .put("front_sufficient",new JSONObject().put("type","boolean"))
                .put("first_edition_present",new JSONObject().put("type","boolean"))
                .put("observed_text",new JSONObject().put("type","string"))
                .put("location",new JSONObject().put("type","string"))
                .put("crop_region",new JSONObject().put("type","string"))
                .put("image_index",integerSchema(0,20))
                .put("shadow_status",new JSONObject().put("type","string"))
                .put("finish",new JSONObject().put("type","string"))
                .put("confidence",integerSchema(0,100))
                .put("reason",new JSONObject().put("type","string"));
        JSONObject format=jsonFormat("flipcheck_tcg_physical_edition_v130",strictObject(props,
                "same_card","front_sufficient","first_edition_present","observed_text",
                "location","crop_region","image_index","shadow_status","finish","confidence","reason"));
        String policy="FLIPCHECK v1.30 FOCUSED TCG PHYSICAL EDITION INSPECTION. NO WEB. "
                +"Inspect the complete front and every supplied enlarged crop as views of the same physical card. "
                +"Decide only whether a physical edition logo/mark is visibly present. Search all layout-appropriate areas: left edge below artwork, beside the description box, near the set/collector symbol, and lower card area. "
                +"Recognize the official graphical 1st Edition mark by text fragments plus logo geometry; perfect OCR is not required. Equivalent visible forms include 1st Edition, 1st Ed., First Edition, EDITION 1 and 1ª Edizione. "
                +"Do not infer edition from card name, set knowledge, a candidate, filename, user hint, catalog or Web. Do not mark present from ordinary uses of the word edition outside the card surface. "
                +"Return the exact visible text if legible, precise physical location, which supplied image/crop exposed it, and confidence. "
                +"Classify shadow_status only from frame/layout cues and finish only from visible reflectivity placement; otherwise return unknown. JSON only.\n\n";
        JSONArray content=new JSONArray().put(new JSONObject().put("type","input_text").put("text",policy+prompt));
        if(imageDataUrls!=null)for(String image:imageDataUrls)content.put(new JSONObject()
                .put("type","input_image").put("image_url",image).put("detail","high"));
        JSONObject body=new JSONObject().put("model",MODEL).put("store",false)
                .put("max_output_tokens",420).put("reasoning",new JSONObject().put("effort","medium"))
                .put("text",new JSONObject().put("format",format).put("verbosity","low"))
                .put("input",new JSONArray().put(new JSONObject().put("role","user").put("content",content)));
        return call(body,true,true);
    }

    /** Independent number-only read; catalog/Web values are intentionally not supplied. */
    Response verifyPhysicalCardNumber(List<String> imageDataUrls) throws Exception {
        JSONObject readingProps=new JSONObject()
                .put("value",new JSONObject().put("type","string"))
                .put("image",integerSchema(0,20)).put("side",new JSONObject().put("type","string"))
                .put("location",new JSONObject().put("type","string"))
                .put("orientation",new JSONObject().put("type","integer").put("enum",new JSONArray().put(0).put(90).put(180).put(270)))
                .put("confidence",integerSchema(0,100));
        JSONObject reading=strictObject(readingProps,"value","image","side","location","orientation","confidence");
        JSONObject props=new JSONObject().put("same_card",new JSONObject().put("type","boolean"))
                .put("readings",new JSONObject().put("type","array").put("maxItems",8).put("items",reading));
        JSONObject format=jsonFormat("flipcheck_number_transcription_v125",strictObject(props,"same_card","readings"));
        JSONArray content=new JSONArray().put(new JSONObject().put("type","input_text").put("text",
                "TRANSCRIBE ONLY THE PHYSICAL CARD/COLLECTOR NUMBER. No Web and no prior or catalog number is supplied. "
                +"Inspect number-bearing corners and reverse areas at 0, 90, 180 and 270 degrees. Distinguish card number from serial x/y, HP/PV, rating, stats, jersey number, year and activation code. "
                +"Return separate literal readings with precise surface location. If uncertain, return alternatives rather than choosing by identity knowledge."));
        for(String image:imageDataUrls)content.put(new JSONObject().put("type","input_image").put("image_url",image).put("detail","high"));
        JSONObject body=new JSONObject().put("model",MODEL).put("store",false).put("max_output_tokens",500)
                .put("reasoning",new JSONObject().put("effort","medium"))
                .put("text",new JSONObject().put("format",format).put("verbosity","low"))
                .put("input",new JSONArray().put(new JSONObject().put("role","user").put("content",content)));
        return call(body,true,true);
    }

    /** One post-closure Web pass for catalog naming and price/comparable enrichment. */
    Response enrichConfirmedIdentity(String prompt) throws Exception {
        JSONObject body=new JSONObject().put("model",MODEL).put("store",false)
                .put("max_output_tokens",1600)
                .put("reasoning",new JSONObject().put("effort","low"))
                .put("max_tool_calls",1)
                .put("tools",new JSONArray().put(new JSONObject().put("type","web_search")
                        .put("search_context_size","medium")))
                .put("include",new JSONArray().put("web_search_call.action.sources")
                        .put("web_search_call.results"))
                .put("text",new JSONObject().put("format",confirmedEnrichmentFormat()).put("verbosity","low"))
                .put("input","FLIPCHECK UNIVERSAL CATALOG MATCHING. The photographic core is a hypothesis until every returned catalog candidate is compared with it. "
                        +"Use exactly one web_search batch to discover multiple exact catalog/checklist candidates and attempt to disprove the leader. "
                        +"Never change physical_card_number or physical_serial. A page-reported checklist number belongs in source_reported_catalog_number until local compatibility checks match it. "
                        +"Do not infer a catalog number from a rating/stat and never put a catalog title into the physical identity. A commercial parallel name is source-only. Never search or price a variant-specific parallel unless RARE_VARIANT_PHYSICAL_PROOF=true. "
                        +"Use QUERY_PROFILE and QUERY_SEED exactly: SEALED searches only the sealed product and never raw/graded/card-number or pictured people; RAW searches only an ungraded single card; GRADED searches the exact photographed grading state. "
                        +"Return each comparable separately with sale_status, item_state RAW/GRADED/SEALED, condition, grading company and exact grade, currency, numeric price, date, retrieved URL, identity_match and exclusion_reason. "
                        +"Never mix listings with sold results or raw with graded items. If comparable evidence is insufficient, return an empty array.\n\n"+prompt);
        return call(body,false,true);
    }

    private static JSONObject confirmedEnrichmentFormat() throws Exception {
        JSONObject candidateProps=new JSONObject()
                .put("source_url",new JSONObject().put("type","string")).put("source_authority",new JSONObject().put("type","string"))
                .put("brand",new JSONObject().put("type","string")).put("product_line",new JSONObject().put("type","string"))
                .put("main_set",new JSONObject().put("type","string")).put("insert_subset",new JSONObject().put("type","string"))
                .put("design_family",new JSONObject().put("type","string")).put("sub_series",new JSONObject().put("type","string"))
                .put("distinguishing_tokens",stringArraySchema(12))
                .put("release_year",new JSONObject().put("type","string")).put("subject",new JSONObject().put("type","string"))
                .put("team",new JSONObject().put("type","string")).put("sport",new JSONObject().put("type","string"))
                .put("card_number",new JSONObject().put("type","string")).put("language",new JSONObject().put("type","string"))
                .put("hp",new JSONObject().put("type","string")).put("evolution_stage",new JSONObject().put("type","string"))
                .put("attacks",stringArraySchema(6)).put("copyright_year",new JSONObject().put("type","string"))
                .put("layout_signature",new JSONObject().put("type","string")).put("finish",new JSONObject().put("type","string"))
                .put("edition",new JSONObject().put("type","string")).put("printing",new JSONObject().put("type","string"))
                .put("parallel",new JSONObject().put("type","string")).put("parallel_family",new JSONObject().put("type","string"))
                .put("parallel_color",new JSONObject().put("type","string")).put("print_run",new JSONObject().put("type","string"))
                .put("serial_number",new JSONObject().put("type","string")).put("format",new JSONObject().put("type","string"))
                .put("product_code",new JSONObject().put("type","string")).put("package_count",new JSONObject().put("type","string"))
                .put("cards_per_pack",new JSONObject().put("type","string")).put("autograph_guarantee",new JSONObject().put("type","string"))
                .put("memorabilia_guarantee",new JSONObject().put("type","string")).put("sealed_status",new JSONObject().put("type","string"))
                .put("configuration",new JSONObject().put("type","string")).put("product_type",new JSONObject().put("type","string"))
                .put("product_name",new JSONObject().put("type","string"));
        JSONObject candidate=strictObject(candidateProps,"source_url","source_authority","brand","product_line","main_set","insert_subset","design_family","sub_series","distinguishing_tokens","release_year","subject","team","sport","card_number","language","hp","evolution_stage","attacks","copyright_year","layout_signature","finish","edition","printing","parallel","parallel_family","parallel_color","print_run","serial_number","format","product_code","package_count","cards_per_pack","autograph_guarantee","memorabilia_guarantee","sealed_status","configuration","product_type","product_name");
        JSONObject comparableProps=new JSONObject()
                .put("sale_status",new JSONObject().put("type","string").put("enum",new JSONArray().put("SOLD").put("ACTIVE_LISTING")))
                .put("item_state",new JSONObject().put("type","string").put("enum",new JSONArray().put("RAW").put("GRADED").put("SEALED")))
                .put("condition",new JSONObject().put("type","string"))
                .put("grading_company",new JSONObject().put("type","string"))
                .put("grade",new JSONObject().put("type","string"))
                .put("currency",new JSONObject().put("type","string"))
                .put("price",new JSONObject().put("type","number"))
                .put("source_url",new JSONObject().put("type","string"))
                .put("title",new JSONObject().put("type","string"))
                .put("date",new JSONObject().put("type","string"))
                .put("identity_match",new JSONObject().put("type","boolean"))
                .put("variant_specific",new JSONObject().put("type","boolean"))
                .put("variant_key",new JSONObject().put("type","string"))
                .put("product_line",new JSONObject().put("type","string"))
                .put("main_set",new JSONObject().put("type","string"))
                .put("insert_subset",new JSONObject().put("type","string"))
                .put("sub_series",new JSONObject().put("type","string"))
                .put("release_year",new JSONObject().put("type","string"))
                .put("card_number",new JSONObject().put("type","string"))
                .put("language",new JSONObject().put("type","string"))
                .put("parallel_family",new JSONObject().put("type","string"))
                .put("parallel_color",new JSONObject().put("type","string"))
                .put("print_run",new JSONObject().put("type","string"))
                .put("format",new JSONObject().put("type","string"))
                .put("sealed_status",new JSONObject().put("type","string"))
                .put("exclusion_reason",new JSONObject().put("type","string"));
        JSONObject comparable=strictObject(comparableProps,"sale_status","item_state","condition",
                "grading_company","grade","currency","price","source_url","title","date","identity_match","variant_specific","variant_key","product_line","main_set","insert_subset","sub_series","release_year","card_number","language","parallel_family","parallel_color","print_run","format","sealed_status","exclusion_reason");
        JSONObject props=new JSONObject()
                .put("source_grounded",new JSONObject().put("type","boolean"))
                .put("physical_tuple_coherent",new JSONObject().put("type","boolean"))
                .put("source_url",new JSONObject().put("type","string"))
                .put("source_reported_catalog_number",new JSONObject().put("type","string"))
                .put("source_reported_release_year",new JSONObject().put("type","string"))
                .put("source_reported_product_line",new JSONObject().put("type","string"))
                .put("source_reported_variant",new JSONObject().put("type","string"))
                .put("source_catalog_title",new JSONObject().put("type","string"))
                .put("candidates",new JSONObject().put("type","array").put("maxItems",6).put("items",candidate))
                .put("comparables",new JSONObject().put("type","array").put("maxItems",12).put("items",comparable))
                .put("evidence",new JSONObject().put("type","string"));
        return jsonFormat("flipcheck_confirmed_enrichment_v124",strictObject(props,
                "source_grounded","physical_tuple_coherent","source_url",
                "source_reported_catalog_number","source_reported_release_year","source_reported_product_line","source_reported_variant",
                "source_catalog_title","candidates","comparables","evidence"));
    }

    /** One optional focused Web lookup to name a visually observed sports-card parallel. */
    Response recoverSportsCardParallel(List<String> imageDataUrls, String prompt) throws Exception {
        JSONObject props = new JSONObject()
                .put("supported", new JSONObject().put("type", "boolean"))
                .put("same_physical_card", new JSONObject().put("type", "boolean"))
                .put("front_back_match", new JSONObject().put("type", "boolean"))
                .put("card_number_match", new JSONObject().put("type", "boolean"))
                .put("parallel_visually_distinguishable", new JSONObject().put("type", "boolean"))
                .put("exact_parallel_name", new JSONObject().put("type", "string"))
                .put("normalized_identity", new JSONObject().put("type", "string"))
                .put("source_url", new JSONObject().put("type", "string"))
                .put("identity_confidence", integerSchema(0, 100))
                .put("contradiction", new JSONObject().put("type", "string"))
                .put("evidence", new JSONObject().put("type", "string"));
        JSONObject format = jsonFormat("flipcheck_sports_parallel_v104",
                strictObject(props, "supported", "same_physical_card", "front_back_match",
                        "card_number_match", "parallel_visually_distinguishable",
                        "exact_parallel_name", "normalized_identity", "source_url",
                        "identity_confidence", "contradiction", "evidence"));
        String policy = "FLIPCHECK v1.04 SPORTS CARD EXACT PARALLEL RECOVERY. "
                + "Use exactly one web_search call and no marketplace/price search. Compare BOTH supplied card faces with an exact checklist or authoritative catalog reference. "
                + "Resolve the commercial parallel name only when player, set/season, card number, rookie marker, front finish/border and reverse layout all agree. "
                + "Color alone is insufficient: distinguish Green Prizm from Silver, Choice, Pulsar, retail exclusives and other green finishes using the actual pattern and checklist. "
                + "Do not return phrases such as green reflective, probable, unresolved or unknown as an exact_parallel_name. Missing proof remains unsupported. JSON only.\n\n";
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", policy + prompt));
        if (imageDataUrls != null) for (String image : imageDataUrls) {
            content.put(new JSONObject().put("type", "input_image")
                    .put("image_url", image).put("detail", "high"));
        }
        JSONArray input = new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 750)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("max_tool_calls", 1)
                .put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")
                        .put("search_context_size", "medium")))
                .put("include", new JSONArray().put("web_search_call.action.sources")
                        .put("web_search_call.results"))
                .put("text", new JSONObject().put("format", format).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    /**
     * Mandatory bounded catalog recovery for a richly observed card left open
     * by the first search, including the zero-candidate case.
     */
    Response recoverExactCardCatalog(List<String> imageDataUrls, String prompt,
                                     boolean useWebSearch) throws Exception {
        JSONObject props = new JSONObject()
                .put("supported", new JSONObject().put("type", "boolean"))
                .put("same_physical_card", new JSONObject().put("type", "boolean"))
                .put("manufacturer", new JSONObject().put("type", "string"))
                .put("set_or_series", new JSONObject().put("type", "string"))
                .put("subject", new JSONObject().put("type", "string"))
                .put("card_number", new JSONObject().put("type", "string"))
                .put("parallel_or_variant", new JSONObject().put("type", "string"))
                .put("rookie", new JSONObject().put("type", "boolean"))
                .put("language", new JSONObject().put("type", "string"))
                .put("normalized_identity", new JSONObject().put("type", "string"))
                .put("exact_reference_complete", new JSONObject().put("type", "boolean"))
                .put("strongest_alternative_disproved", new JSONObject().put("type", "boolean"))
                .put("source_url", new JSONObject().put("type", "string"))
                .put("exact_composite_tuple_match", new JSONObject().put("type", "boolean"))
                .put("visual_reference_checked", new JSONObject().put("type", "boolean"))
                .put("visual_match_confidence", integerSchema(0, 100))
                .put("matched_physical_fields", stringArraySchema(14))
                .put("contradictions", stringArraySchema(8))
                .put("disproof_passed", new JSONObject().put("type", "boolean"))
                .put("identity_confidence", integerSchema(0, 100))
                .put("evidence", new JSONObject().put("type", "string"));
        JSONObject format = jsonFormat("flipcheck_exact_card_catalog_v105",
                strictObject(props, "supported", "same_physical_card", "manufacturer",
                        "set_or_series", "subject", "card_number", "parallel_or_variant",
                        "rookie", "language", "normalized_identity",
                        "exact_reference_complete", "strongest_alternative_disproved", "source_url",
                        "exact_composite_tuple_match",
                        "visual_reference_checked", "visual_match_confidence",
                        "matched_physical_fields", "contradictions", "disproof_passed",
                        "identity_confidence", "evidence"));
        String policy = "FLIPCHECK v1.11 UNIVERSAL EXACT IDENTITY RECOVERY. "
                + (useWebSearch ? "Use exactly one web_search call. " : "Do not call tools; adjudicate the complete first-pass source ledger below. ")
                + "The first pass left a richly observed physical object unresolved. Restart from every physical label, structured field, layout cue and supplied source; never inherit a failed shortlist. "
                + "For cards use manufacturer, subject, team, year/season, set/series, ratings/stats, collector number, language, rookie mark, parallel pattern, serial fraction and both face layouts. A plain sports rating beside DEF/OFF/ATT/OVR is not a card number. A sports fraction such as 2/25 is the photographed serial and /25 is a decisive print-run axis. "
                + "A physical card number is preferred but is not mandatory when an exact front-and-back composite tuple, subject, set/year and multiple distinctive printed fields uniquely match one catalog entry. Set exact_composite_tuple_match=true only when at least six independent photographed cues bind one checklist entry. A reference image is preferred; an authoritative text checklist may still corroborate a composite tuple when it exposes the same subject, set/year and distinctive printed fields. "
                + "For sports cards preserve player, set, exact parallel, card number and RC. For TCG preserve subject, set, collector number, finish, edition/printing and language. "
                + "For sealed products preserve manufacturer, season, complete product line, sport, box format and printed configuration. For other objects require a model-specific visual reference and explicitly disprove the strongest lookalike. "
                + "Do not search prices or marketplaces. Do not return a generic brand/family. If the exact entry or a value-relevant variant remains ambiguous, supported=false. JSON only.\n\n";
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", policy + prompt));
        if (imageDataUrls != null) for (String image : imageDataUrls) {
            content.put(new JSONObject().put("type", "input_image")
                    .put("image_url", image).put("detail", "low"));
        }
        JSONArray input = new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 550)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", format).put("verbosity", "low"))
                .put("input", input);
        if (useWebSearch) {
            body.put("max_tool_calls", 1)
                    .put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")
                            .put("search_context_size", "low")))
                    .put("include", new JSONArray().put("web_search_call.action.sources")
                            .put("web_search_call.results"));
        }
        return call(body, imageDataUrls != null && !imageDataUrls.isEmpty(), true);
    }

    /** Selective manufacturer/family visual check for richly structured control devices. */
    Response recoverVisualBrandFamily(List<String> imageDataUrls, String prompt) throws Exception {
        JSONObject props = new JSONObject()
                .put("applicable", new JSONObject().put("type", "boolean"))
                .put("same_foreground_object", new JSONObject().put("type", "boolean"))
                .put("visually_distinguishable", new JSONObject().put("type", "boolean"))
                .put("model_code_visible", new JSONObject().put("type", "boolean"))
                .put("brand", new JSONObject().put("type", "string"))
                .put("family", new JSONObject().put("type", "string"))
                .put("confidence", integerSchema(0, 100))
                .put("distinctive_cues", stringArraySchema(8))
                .put("contradiction", new JSONObject().put("type", "string"));
        JSONObject format = jsonFormat("flipcheck_visual_brand_family_v102",
                strictObject(props, "applicable", "same_foreground_object",
                        "visually_distinguishable", "model_code_visible", "brand", "family",
                        "confidence", "distinctive_cues", "contradiction"));
        String policy = "FLIPCHECK v1.02 SELECTIVE VISUAL BRAND/FAMILY CHECK. NO WEB. "
                + "Inspect only the supplied foreground physical object. Ignore every prior candidate, user hint, filename and web hypothesis; none is provided as evidence. "
                + "Use manufacturer-specific industrial design, enclosure geometry, panel colors, typography, control topology and logo shape. Shared control words or functions are not brand evidence. "
                + "Return a brand and commercial family only when the physical design is genuinely distinctive at confidence 90 or higher; otherwise leave both empty and set visually_distinguishable=false. "
                + "Never infer an exact model code from station count, button count or family appearance. model_code_visible=true only when a literal MODEL/P/N marking is readable in the photo. "
                + "This pass may disprove a text-only candidate but cannot confirm an exact model. JSON only.\n\n";
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", policy + prompt));
        if (imageDataUrls != null) {
            for (String image : imageDataUrls) {
                content.put(new JSONObject().put("type", "input_image")
                        .put("image_url", image).put("detail", "high"));
            }
        }
        JSONArray input = new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 550)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", format).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    Response visionRole(List<String> imageDataUrls, String prompt, String detail, int maxOutputTokens) throws Exception {
        String d = detail == null ? "low" : detail.trim().toLowerCase(Locale.ROOT);
        if (!d.equals("low") && !d.equals("high") && !d.equals("auto")) {
            d = "low";
        }
        JSONArray content = new JSONArray().put(new JSONObject().put("type", "input_text").put("text", prompt));
        for (String image : imageDataUrls) {
            content.put(new JSONObject().put("type", "input_image").put("image_url", image).put("detail", d));
        }
        JSONArray input = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        JSONObject candidateProps = new JSONObject().put("brand", new JSONObject().put("type", "string")).put("family", new JSONObject().put("type", "string")).put("model", new JSONObject().put("type", "string")).put("confidence", new JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100)).put("reason", new JSONObject().put("type", "string"));
        JSONObject candidateSchema = new JSONObject().put("type", "object").put("additionalProperties", false).put("properties", candidateProps).put("required", new JSONArray().put("brand").put("family").put("model").put("confidence").put("reason"));
        JSONObject resultProps = new JSONObject().put("brand", new JSONObject().put("type", "string")).put("family", new JSONObject().put("type", "string")).put("model", new JSONObject().put("type", "string")).put("identity_confidence", new JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100)).put("reason", new JSONObject().put("type", "string")).put("candidates", new JSONObject().put("type", "array").put("maxItems", 4).put("items", candidateSchema));
        JSONObject resultSchema = new JSONObject().put("type", "object").put("additionalProperties", false).put("properties", resultProps).put("required", new JSONArray().put("brand").put("family").put("model").put("identity_confidence").put("reason").put("candidates"));
        JSONObject format = new JSONObject().put("type", "json_schema").put("name", "flipcheck_vision_role").put("strict", true).put("schema", resultSchema);
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false).put("max_output_tokens", Math.max(1000, Math.min(1400, maxOutputTokens))).put("reasoning", new JSONObject().put("effort", "low")).put("text", new JSONObject().put("format", format).put("verbosity", "low")).put("input", input);
        return call(body, true, true);
    }

    Response visionMatch(List<String> imageDataUrls, String prompt, String detail) throws Exception {
        String d = detail == null ? "low" : detail.trim().toLowerCase(Locale.ROOT);
        if (!d.equals("low") && !d.equals("high") && !d.equals("auto")) {
            d = "low";
        }
        JSONArray content = new JSONArray().put(new JSONObject()
                .put("type", "input_text").put("text", prompt));
        for (String image : imageDataUrls) {
            content.put(new JSONObject().put("type", "input_image")
                    .put("image_url", image).put("detail", d));
        }
        JSONArray input = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        JSONObject matchProps = new JSONObject()
                .put("candidate_index", integerSchema(-1, 5))
                .put("visual_similarity", integerSchema(0, 100))
                .put("geometry_consistent", new JSONObject().put("type", "boolean"))
                .put("same_entity_role", new JSONObject().put("type", "boolean"))
                .put("exact_variant_distinguishable", new JSONObject().put("type", "boolean"))
                .put("contradictions", stringArraySchema(8))
                .put("reason", new JSONObject().put("type", "string"));
        JSONObject matchSchema = strictObject(matchProps, "candidate_index", "visual_similarity",
                "geometry_consistent", "same_entity_role", "exact_variant_distinguishable",
                "contradictions", "reason");
        JSONObject resultProps = new JSONObject()
                .put("winner_candidate_index", integerSchema(-1, 5))
                .put("identity_confidence", integerSchema(0, 100))
                .put("reason", new JSONObject().put("type", "string"))
                .put("matches", new JSONObject().put("type", "array").put("maxItems", 6).put("items", matchSchema));
        JSONObject format = jsonFormat("flipcheck_visual_match_v075",
                strictObject(resultProps, "winner_candidate_index", "identity_confidence", "reason", "matches"));
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", 1300)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("text", new JSONObject().put("format", format).put("verbosity", "low"))
                .put("input", input);
        return call(body, true, true);
    }

    Response webStage(String stage, String prompt) throws Exception {
        String policy;
        String effort;
        int maxOutput;
        String contextSize;
        String s = stage == null ? "discovery" : stage.trim().toLowerCase(Locale.ROOT);
        if ("resolve".equals(s)) {
            policy = "FLIPCHECK v0.82 ONE-PASS PRODUCT RESOLUTION. Use exactly one web_search tool call. "
                    + "Discover, compare and source-check concrete candidates for the same physical entity in that one pass. "
                    + "After resolving identity, use the same single web_search call to collect available sold/comparable prices; identity and price remain independent. Use only the structured observations supplied by the prompt; "
                    + "never reconstruct a query from omitted raw OCR. Transient displays and control captions are forbidden query terms. "
                    + "A visible manufacturer mark is a strong namespace anchor, but a same-word retailer/domain is not the manufacturer. "
                    + "Prefer manufacturer-owned product, support, catalog or manual pages. exact_reference_complete=true only when a retrieved "
                    + "source prints the complete model/reference, including regional suffixes. exact_identity_supported=true only when that source "
                    + "directly names the candidate. Actively disprove the leader against the strongest alternative and keep related/compatible/host "
                    + "products out of the identity candidates. Missing evidence is UNKNOWN. Return only the requested schema.";
            effort = "low";
            maxOutput = 1650;
            contextSize = "medium";
        } else if ("brand_entity".equals(s)) {
            policy = "FLIPCHECK v0.73 BRAND ENTITY RESOLVER. Risolvi esclusivamente quale PRODUTTORE/MANUFACTURER corrisponde al marchio fisicamente visibile sul prodotto. La stessa parola nel nome di un negozio, rivenditore, concessionario, distributore, sito, localita' o dominio NON e' una corrispondenza di marca. Una entita' e' valida solo se una fonte manufacturer-owned/ufficiale usa quel marchio sui propri prodotti e la sua gamma contiene una classe fisicamente compatibile con l'oggetto osservato. Individua il dominio/catalogo ufficiale del produttore. Se esistono omonimi e non puoi distinguerli, resolved=false. Restituisci solo il JSON richiesto dal prompt.";
            effort = "low";
            maxOutput = 950;
            contextSize = "low";
        } else if ("verify".equals(s)) {
            policy = "FLIPCHECK v0.67 UNIVERSAL VERIFY + DISPROOF. Verify exactly one concrete candidate against real sources. First establish whether a source names the exact candidate and its identity-bearing attributes. Then look for the strongest surviving alternative and actively search for a contradiction. First verify that the candidate denotes the same physical entity as the photographed object; related or compatible products are context only. A STRONG conflict needs conflict_evidence_confidence>=85. If you actually inspect a real image tied to the exact candidate source, visual_reference_checked may be true and visual_match_confidence must reflect image-to-image identity, not textual similarity.  UNIVERSAL RULES: represent evidence as dynamic key=value attributes supported by the photographed object or grounded sources. A direct conflict on identity-bearing attributes (model/reference, edition/variant, year/version, size/capacity/count, compatible generation, serial/print run or any other source-defined discriminator) is STRONG and must defeat visual similarity. Missing or unreadable attributes are UNKNOWN, never contradictions. Prefix contradiction strings STRONG: or WEAK:. Do not use category-specific assumptions. Return only the JSON schema requested by the prompt.";
            effort = "medium";
            maxOutput = 1450;
            contextSize = "medium";
        } else if ("compare".equals(s)) {
            policy = "FLIPCHECK v0.67 UNIVERSAL CANDIDATE TOURNAMENT. Compare only grounded candidates using dynamic attributes, OCR, geometry/layout and sources. Try to eliminate candidates with direct attribute conflicts. Do not invent a discriminator and do not reward a candidate merely because it shares the category or overall silhouette.  UNIVERSAL RULES: represent evidence as dynamic key=value attributes supported by the photographed object or grounded sources. A direct conflict on identity-bearing attributes (model/reference, edition/variant, year/version, size/capacity/count, compatible generation, serial/print run or any other source-defined discriminator) is STRONG and must defeat visual similarity. Missing or unreadable attributes are UNKNOWN, never contradictions. Prefix contradiction strings STRONG: or WEAK:. Do not use category-specific assumptions. Return only the JSON schema requested by the prompt.";
            effort = "low";
            maxOutput = 1350;
            contextSize = "low";
        } else {
            policy = "FLIPCHECK v0.67 UNIVERSAL DISCOVERY. Find concrete grounded product/model candidates from neutral observed evidence. Candidate identity must denote the same physical entity as the photographed object; related products are not identity candidates. Vision brand/model guesses and user hints are soft leads only. Prefer sources that identify a concrete model/reference and expose attributes or images that can later be verified.  UNIVERSAL RULES: represent evidence as dynamic key=value attributes supported by the photographed object or grounded sources. A direct conflict on identity-bearing attributes (model/reference, edition/variant, year/version, size/capacity/count, compatible generation, serial/print run or any other source-defined discriminator) is STRONG and must defeat visual similarity. Missing or unreadable attributes are UNKNOWN, never contradictions. Prefix contradiction strings STRONG: or WEAK:. Do not use category-specific assumptions. Return only the JSON schema requested by the prompt.";
            effort = "low";
            maxOutput = 1550;
            contextSize = "low";
        }
        JSONObject body = new JSONObject().put("model", MODEL).put("store", false)
                .put("max_output_tokens", maxOutput)
                .put("reasoning", new JSONObject().put("effort", effort))
                .put("max_tool_calls", 1)
                .put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")
                        .put("search_context_size", contextSize)))
                .put("include", new JSONArray().put("web_search_call.action.sources")
                        .put("web_search_call.results"))
                .put("text", new JSONObject().put("format", webFormat(s)).put("verbosity", "low"))
                .put("input", policy + "\n\n" + prompt);
        return call(body, false, true);
    }

    /** Identity-only retrieval for UniversalIdentityEngineV2; never requests prices. */
    Response identityWebSearchV2(String prompt) throws Exception {return identityWebSearchV2(null,prompt);}

    /** v1.33 keeps the original image in the identity search so layout disproof is real. */
    Response identityWebSearchV2(List<String> imageDataUrls,String prompt) throws Exception {
        String policy="FLIPCHECK v1.33 NEUTRAL IDENTITY RETRIEVAL AND DISPROOF. Use exactly one web_search call. "
                +"Search official manufacturers/publishers, authoritative checklists and structured product pages before retailers. "
                +"Do not search prices, sold listings or marketplace comparables. OBSERVED means localized photo evidence; INFERRED values are query leads only. "
                +"Unless a brand is literal localized evidence, the first query MUST be brand-neutral and use only LOCALIZED_OBSERVED_FACTS. Never put an inferred brand or model into query[0], including a site filter. "
                +"After neutral retrieval, generate multiple alternative brands/records and compare them against the supplied photographs. "
                +"An inferred brand may not veto a retrieved candidate or enter the final title. A checklist or product page containing multiple items MUST yield one isolated candidate per row/model/edition/format. "
                +"Never combine a number from one row, a set from another row, or a brand from another result. Return source_record_id and page scope for every candidate. "
                +"Ambiguous text is unknown, not a conflict. Actively disprove every leading candidate using exact number/set/season/configuration or full control-layout differences. JSON only.\n\n";
        Object input=policy+prompt;
        if(imageDataUrls!=null&&!imageDataUrls.isEmpty()){JSONArray content=new JSONArray().put(new JSONObject().put("type","input_text").put("text",policy+prompt));for(String image:imageDataUrls)content.put(new JSONObject().put("type","input_image").put("image_url",image).put("detail","high"));input=new JSONArray().put(new JSONObject().put("role","user").put("content",content));}
        JSONObject body=new JSONObject().put("model",MODEL).put("store",false)
                .put("max_output_tokens",1900).put("reasoning",new JSONObject().put("effort","low"))
                .put("max_tool_calls",1).put("tools",new JSONArray().put(new JSONObject()
                        .put("type","web_search").put("search_context_size","medium")))
                .put("include",new JSONArray().put("web_search_call.action.sources").put("web_search_call.results"))
                .put("text",new JSONObject().put("format",identityWebFormatV2()).put("verbosity","low"))
                .put("input",input);
        return call(body,imageDataUrls!=null&&!imageDataUrls.isEmpty(),true);
    }

    private static JSONObject identityWebFormatV2() throws Exception {
        JSONObject candidateProps=new JSONObject()
                .put("candidate_id",new JSONObject().put("type","string"))
                .put("source_id",new JSONObject().put("type","string"))
                .put("source_title",new JSONObject().put("type","string"))
                .put("source_record_id",new JSONObject().put("type","string"))
                .put("source_page_scope",new JSONObject().put("type","string").put("enum",new JSONArray().put("SINGLE_RECORD").put("CHECKLIST_ROW").put("MULTI_RECORD_PAGE").put("GENERIC_PAGE")))
                .put("identity_level",new JSONObject().put("type","string").put("enum",new JSONArray().put("FAMILY").put("CORE_IDENTITY").put("CATALOG_IDENTITY").put("VARIANT_OR_FORMAT")))
                .put("brand",new JSONObject().put("type","string"))
                .put("product_line",new JSONObject().put("type","string"))
                .put("set_name",new JSONObject().put("type","string"))
                .put("sub_series",new JSONObject().put("type","string"))
                .put("model",new JSONObject().put("type","string"))
                .put("category",new JSONObject().put("type","string"))
                .put("year",new JSONObject().put("type","string"))
                .put("subject",new JSONObject().put("type","string"))
                .put("card_number",new JSONObject().put("type","string"))
                .put("language",new JSONObject().put("type","string"))
                .put("edition",new JSONObject().put("type","string"))
                .put("card_role",new JSONObject().put("type","string"))
                .put("printed_total",new JSONObject().put("type","string"))
                .put("set_symbol",new JSONObject().put("type","string"))
                .put("copyright_year",new JSONObject().put("type","string"))
                .put("sport",new JSONObject().put("type","string"))
                .put("team",new JSONObject().put("type","string"))
                .put("finish",new JSONObject().put("type","string"))
                .put("format",new JSONObject().put("type","string"))
                .put("configuration",new JSONObject().put("type","string"))
                .put("product_code",new JSONObject().put("type","string"))
                .put("barcode",new JSONObject().put("type","string"))
                .put("control_layout",new JSONObject().put("type","string"))
                .put("shortcut_buttons",new JSONObject().put("type","string"))
                .put("navigation_layout",new JSONObject().put("type","string"))
                .put("numeric_keypad",new JSONObject().put("type","string"))
                .put("voice_control",new JSONObject().put("type","string"))
                .put("layout_signature",new JSONObject().put("type","string"))
                .put("source_url",new JSONObject().put("type","string"))
                .put("source_authority",new JSONObject().put("type","string"))
                .put("source_quality_percent",integerSchema(0,100).put("description","Source reliability on a 0 to 100 scale, NOT a 1 to 5 star rating. Assess authority and support for this isolated record, independently of visual similarity."))
                .put("exact_reference",new JSONObject().put("type","boolean"))
                .put("disproof_passed",new JSONObject().put("type","boolean"))
                .put("layout_match",integerSchema(0,100))
                .put("matched_observed_fields",stringArraySchema(16))
                .put("contradicted_observed_fields",stringArraySchema(12))
                .put("unknown_fields",stringArraySchema(12));
        JSONObject candidate=strictObject(candidateProps,"candidate_id","source_id","source_title","source_record_id","source_page_scope","identity_level","brand","product_line","set_name","sub_series","model","category","year","subject","card_number","language","edition","card_role","printed_total","set_symbol","copyright_year","sport","team","finish","format","configuration","product_code","barcode","control_layout","shortcut_buttons","navigation_layout","numeric_keypad","voice_control","layout_signature","source_url","source_authority","source_quality_percent","exact_reference","disproof_passed","layout_match","matched_observed_fields","contradicted_observed_fields","unknown_fields");
        JSONObject props=new JSONObject()
                .put("queries",stringArraySchema(4))
                .put("candidates",new JSONObject().put("type","array").put("maxItems",6).put("items",candidate))
                .put("retrieval_reason",new JSONObject().put("type","string"));
        return jsonFormat("flipcheck_identity_retrieval_v133",strictObject(props,"queries","candidates","retrieval_reason"));
    }

    private static JSONObject webFormat(String stage) throws Exception {
        if ("resolve".equals(stage)) {
            JSONObject comparableProps=new JSONObject()
                    .put("sale_status",new JSONObject().put("type","string").put("enum",new JSONArray().put("SOLD").put("ACTIVE_LISTING")))
                    .put("item_state",new JSONObject().put("type","string").put("enum",new JSONArray().put("RAW").put("GRADED").put("SEALED")))
                    .put("condition",new JSONObject().put("type","string"))
                    .put("grading_company",new JSONObject().put("type","string"))
                    .put("grade",new JSONObject().put("type","string"))
                    .put("currency",new JSONObject().put("type","string"))
                    .put("price",new JSONObject().put("type","number"))
                    .put("source_url",new JSONObject().put("type","string"))
                    .put("title",new JSONObject().put("type","string"))
                    .put("date",new JSONObject().put("type","string"))
                    .put("identity_match",new JSONObject().put("type","boolean"))
                    .put("variant_specific",new JSONObject().put("type","boolean"))
                    .put("variant_key",new JSONObject().put("type","string"))
                    .put("exclusion_reason",new JSONObject().put("type","string"));
            JSONObject comparable=strictObject(comparableProps,"sale_status","item_state","condition",
                    "grading_company","grade","currency","price","source_url","title","date","identity_match","variant_specific","variant_key","exclusion_reason");
            JSONObject candidateProps = new JSONObject()
                    .put("brand", new JSONObject().put("type", "string"))
                    .put("family", new JSONObject().put("type", "string"))
                    .put("model", new JSONObject().put("type", "string"))
                    .put("category_key",new JSONObject().put("type","string"))
                    .put("year",new JSONObject().put("type","string"))
                    .put("subject",new JSONObject().put("type","string"))
                    .put("team",new JSONObject().put("type","string"))
                    .put("sport",new JSONObject().put("type","string"))
                    .put("card_number",new JSONObject().put("type","string"))
                    .put("language",new JSONObject().put("type","string"))
                    .put("hp",new JSONObject().put("type","string"))
                    .put("evolution_stage",new JSONObject().put("type","string"))
                    .put("attacks",stringArraySchema(6))
                    .put("copyright_year",new JSONObject().put("type","string"))
                    .put("layout_signature",new JSONObject().put("type","string"))
                    .put("product_type",new JSONObject().put("type","string"))
                    .put("source_authority",new JSONObject().put("type","string"))
                    .put("edition",new JSONObject().put("type","string"))
                    .put("printing",new JSONObject().put("type","string"))
                    .put("parallel",new JSONObject().put("type","string"))
                    .put("parallel_color",new JSONObject().put("type","string"))
                    .put("finish",new JSONObject().put("type","string"))
                    .put("format",new JSONObject().put("type","string"))
                    .put("configuration",new JSONObject().put("type","string"))
                    .put("material_variant_key",new JSONObject().put("type","string"))
                    .put("materially_distinct_variant",new JSONObject().put("type","boolean"))
                    .put("probable_reference", new JSONObject().put("type", "string"))
                    .put("probable_reference_confidence", integerSchema(0, 100))
                    .put("source_url", new JSONObject().put("type", "string"))
                    .put("exact_reference_complete", new JSONObject().put("type", "boolean"))
                    .put("exact_identity_supported", new JSONObject().put("type", "boolean"))
                    .put("source_identity_confidence", integerSchema(0, 100))
                    .put("same_entity_role", new JSONObject().put("type", "boolean"))
                    .put("relationship_only", new JSONObject().put("type", "boolean"))
                    .put("disproof_passed", new JSONObject().put("type", "boolean"))
                    .put("identifier_score", integerSchema(0, 100))
                    .put("text_score", integerSchema(0, 100))
                    .put("layout_score", integerSchema(0, 100))
                    .put("web_score", integerSchema(0, 100))
                    .put("visual_reference_checked", new JSONObject().put("type", "boolean"))
                    .put("visual_match_confidence", integerSchema(0, 100))
                    .put("major_geometry_conflict", new JSONObject().put("type", "boolean"))
                    .put("photo_identity_supported", new JSONObject().put("type", "boolean"))
                    .put("matched_photo_identity_fields", stringArraySchema(12))
                    .put("matched_distinctive_features", stringArraySchema(8))
                    .put("conflicting_distinctive_features", stringArraySchema(8))
                    .put("candidate_facts", stringArraySchema(14))
                    .put("contradictions", stringArraySchema(10))
                    .put("evidence", new JSONObject().put("type", "string"));
            JSONObject candidate = strictObject(candidateProps, "brand", "family", "model",
                    "category_key","year","subject","team","sport","card_number","language","hp","evolution_stage","attacks","copyright_year","layout_signature","product_type","source_authority","edition","printing","parallel","parallel_color","finish","format","configuration","material_variant_key","materially_distinct_variant",
                    "probable_reference", "probable_reference_confidence", "source_url",
                    "exact_reference_complete", "exact_identity_supported", "source_identity_confidence",
                    "same_entity_role", "relationship_only", "disproof_passed", "identifier_score",
                    "text_score", "layout_score", "web_score", "visual_reference_checked",
                    "visual_match_confidence", "major_geometry_conflict", "photo_identity_supported",
                    "matched_photo_identity_fields", "matched_distinctive_features",
                    "conflicting_distinctive_features", "candidate_facts", "contradictions", "evidence");
            JSONObject props = new JSONObject()
                    .put("resolved_category", new JSONObject().put("type", "string"))
                    .put("resolved_brand", new JSONObject().put("type", "string"))
                    .put("leader_index", integerSchema(-1, 5))
                    .put("confirmed", new JSONObject().put("type", "boolean"))
                    .put("model_proof", new JSONObject().put("type", "string")
                            .put("enum", new JSONArray().put("direct_product_page").put("exact_manual")
                                    .put("exact_catalog").put("exact_retailer").put("exact_identifier")
                                    .put("photo_complete_identity").put("weak").put("none")))
                    .put("candidates", new JSONObject().put("type", "array").put("maxItems", 6).put("items", candidate))
                    .put("strongest_alternative", new JSONObject().put("type", "string"))
                    .put("evidence", new JSONObject().put("type", "string"))
                    .put("source_grounded",new JSONObject().put("type","boolean"))
                    .put("physical_tuple_coherent",new JSONObject().put("type","boolean"))
                    .put("source_url",new JSONObject().put("type","string"))
                    .put("source_confirmed_catalog_number",new JSONObject().put("type","string"))
                    .put("source_confirmed_release_year",new JSONObject().put("type","string"))
                    .put("source_confirmed_variant",new JSONObject().put("type","string"))
                    .put("source_catalog_title",new JSONObject().put("type","string"))
                    .put("comparables",new JSONObject().put("type","array").put("maxItems",12).put("items",comparable))
                    .put("price_evidence",new JSONObject().put("type","string"))
                    .put("next_photo_request", new JSONObject().put("type", "string"))
                    .put("next_photo_reason", new JSONObject().put("type", "string"));
            return jsonFormat("flipcheck_resolve_v082", strictObject(props, "resolved_category",
                    "resolved_brand", "leader_index", "confirmed", "model_proof", "candidates",
                    "strongest_alternative", "evidence", "source_grounded",
                    "physical_tuple_coherent", "source_url", "source_confirmed_catalog_number", "source_confirmed_release_year",
                    "source_confirmed_variant", "source_catalog_title", "comparables", "price_evidence",
                    "next_photo_request", "next_photo_reason"));
        }
        if ("brand_entity".equals(stage)) {
            JSONObject props = new JSONObject()
                    .put("resolved", new JSONObject().put("type", "boolean"))
                    .put("visible_brand", new JSONObject().put("type", "string"))
                    .put("manufacturer_entity", new JSONObject().put("type", "string"))
                    .put("official_domain", new JSONObject().put("type", "string"))
                    .put("official_url", new JSONObject().put("type", "string"))
                    .put("manufacturer_role_confirmed", new JSONObject().put("type", "boolean"))
                    .put("product_class_match", new JSONObject().put("type", "boolean"))
                    .put("compatible_product_classes", stringArraySchema(8))
                    .put("confidence", integerSchema(0, 100))
                    .put("evidence", new JSONObject().put("type", "string"));
            return jsonFormat("flipcheck_brand_entity_v075", strictObject(props,
                    "resolved", "visible_brand", "manufacturer_entity", "official_domain",
                    "official_url", "manufacturer_role_confirmed", "product_class_match",
                    "compatible_product_classes", "confidence", "evidence"));
        }
        if ("verify".equals(stage)) {
            JSONObject props = new JSONObject()
                    .put("confirmed", new JSONObject().put("type", "boolean"))
                    .put("same_entity_role", new JSONObject().put("type", "boolean"))
                    .put("relationship_only", new JSONObject().put("type", "boolean"))
                    .put("exact_identity_supported", new JSONObject().put("type", "boolean"))
                    .put("source_identity_confidence", integerSchema(0, 100))
                    .put("visual_reference_checked", new JSONObject().put("type", "boolean"))
                    .put("visual_match_confidence", integerSchema(0, 100))
                    .put("conflict_level", new JSONObject().put("type", "string")
                            .put("enum", new JSONArray().put("none").put("weak").put("strong")))
                    .put("conflict_evidence_confidence", integerSchema(0, 100))
                    .put("attribute_conflicts", stringArraySchema(10))
                    .put("brand", new JSONObject().put("type", "string"))
                    .put("family", new JSONObject().put("type", "string"))
                    .put("model", new JSONObject().put("type", "string"))
                    .put("model_proof", new JSONObject().put("type", "string")
                            .put("enum", new JSONArray().put("direct_product_page").put("exact_manual")
                                    .put("exact_catalog").put("exact_retailer").put("exact_identifier")
                                    .put("weak").put("none")))
                    .put("matched_visual_facts", stringArraySchema(10))
                    .put("matched_layout_tokens", stringArraySchema(10))
                    .put("contradictions", stringArraySchema(10))
                    .put("disproof_passed", new JSONObject().put("type", "boolean"))
                    .put("strongest_alternative", new JSONObject().put("type", "string"))
                    .put("evidence", new JSONObject().put("type", "string"))
                    .put("next_photo_request", new JSONObject().put("type", "string"))
                    .put("next_photo_reason", new JSONObject().put("type", "string"));
            return jsonFormat("flipcheck_verify_v075", strictObject(props,
                    "confirmed", "same_entity_role", "relationship_only", "exact_identity_supported",
                    "source_identity_confidence", "visual_reference_checked", "visual_match_confidence",
                    "conflict_level", "conflict_evidence_confidence", "attribute_conflicts", "brand",
                    "family", "model", "model_proof", "matched_visual_facts", "matched_layout_tokens",
                    "contradictions", "disproof_passed", "strongest_alternative", "evidence",
                    "next_photo_request", "next_photo_reason"));
        }
        JSONObject candidateProps = new JSONObject()
                .put("brand", new JSONObject().put("type", "string"))
                .put("family", new JSONObject().put("type", "string"))
                .put("model", new JSONObject().put("type", "string"))
                .put("source_url", new JSONObject().put("type", "string"))
                .put("identifier_score", integerSchema(0, 100))
                .put("text_score", integerSchema(0, 100))
                .put("layout_score", integerSchema(0, 100))
                .put("web_score", integerSchema(0, 100))
                .put("candidate_facts", stringArraySchema(14))
                .put("contradictions", stringArraySchema(10))
                .put("evidence", new JSONObject().put("type", "string"));
        JSONObject candidate = strictObject(candidateProps, "brand", "family", "model", "source_url",
                "identifier_score", "text_score", "layout_score", "web_score", "candidate_facts",
                "contradictions", "evidence");
        JSONObject props = new JSONObject()
                .put("candidates", new JSONObject().put("type", "array").put("maxItems", 6).put("items", candidate))
                .put("next_photo_request", new JSONObject().put("type", "string"))
                .put("next_photo_reason", new JSONObject().put("type", "string"));
        return jsonFormat("compare".equals(stage) ? "flipcheck_compare_v075" : "flipcheck_discovery_v075",
                strictObject(props, "candidates", "next_photo_request", "next_photo_reason"));
    }

    private static JSONObject jsonFormat(String name, JSONObject schema) throws Exception {
        return new JSONObject().put("type", "json_schema").put("name", name)
                .put("strict", true).put("schema", schema);
    }

    /**
     * Deliberately small observer contract. The former schema required the model to
     * repeat the same identity in labels, summaries, fields, candidates and prose;
     * that made a valid photo vulnerable to max_output_tokens truncation.
     */
    private static JSONObject compactObserverFormat() throws Exception {
        JSONObject factProps = new JSONObject()
                .put("key", new JSONObject().put("type", "string").put("enum", new JSONArray(java.util.Arrays.asList(ObservationFieldContractV2.FIELDS)))
                        .put("description", "Use the canonical semantic field. A product/set logo is productLine/setName, not manufacturer. A button/service logo is controlLabel, not accessory brand. Unknown properties stay physicalFeature or printedLabel with their original role."))
                .put("value", new JSONObject().put("type", "string"))
                .put("image", integerSchema(-1, 20))
                .put("side", new JSONObject().put("type", "string"))
                .put("location", new JSONObject().put("type", "string"))
                .put("role", new JSONObject().put("type", "string"))
                .put("confidence", integerSchema(0, 100));
        JSONObject fact = strictObject(factProps, "key", "value", "image", "side",
                "location", "role", "confidence");
        JSONObject candidateProps = new JSONObject()
                .put("brand", new JSONObject().put("type", "string"))
                .put("product_line", new JSONObject().put("type", "string"))
                .put("subject", new JSONObject().put("type", "string"))
                .put("year", new JSONObject().put("type", "string"))
                .put("card_number", new JSONObject().put("type", "string"))
                .put("language", new JSONObject().put("type", "string"))
                .put("edition", new JSONObject().put("type", "string"))
                .put("finish", new JSONObject().put("type", "string"))
                .put("format", new JSONObject().put("type", "string"))
                .put("material_variant_key", new JSONObject().put("type", "string"))
                .put("materially_distinct", new JSONObject().put("type", "boolean"))
                .put("confidence", integerSchema(0, 100));
        JSONObject candidate = strictObject(candidateProps, "brand", "product_line",
                "subject", "year", "card_number", "language", "edition", "finish",
                "format", "material_variant_key", "materially_distinct", "confidence");
        JSONObject props = new JSONObject()
                .put("content_sufficient", new JSONObject().put("type", "boolean"))
                .put("category", new JSONObject().put("type", "string"))
                .put("views", stringArraySchema(6))
                .put("facts", new JSONObject().put("type", "array").put("maxItems", 32)
                        .put("items", fact))
                .put("identity_hint", new JSONObject().put("type", "string"))
                .put("candidates", new JSONObject().put("type", "array").put("maxItems", 6)
                        .put("items", candidate))
                .put("missing_discriminators", stringArraySchema(8));
        return jsonFormat("flipcheck_compact_photo_observation_v125",
                strictObject(props, "content_sufficient", "category", "views", "facts",
                        "identity_hint", "candidates", "missing_discriminators"));
    }

    private static JSONObject observerFormat() throws Exception {
        JSONObject physicalCandidateProps=new JSONObject()
                .put("category_key",new JSONObject().put("type","string"))
                .put("brand",new JSONObject().put("type","string"))
                .put("family",new JSONObject().put("type","string"))
                .put("model",new JSONObject().put("type","string"))
                .put("year",new JSONObject().put("type","string"))
                .put("subject",new JSONObject().put("type","string"))
                .put("card_number",new JSONObject().put("type","string"))
                .put("language",new JSONObject().put("type","string"))
                .put("edition",new JSONObject().put("type","string"))
                .put("printing",new JSONObject().put("type","string"))
                .put("parallel",new JSONObject().put("type","string"))
                .put("parallel_color",new JSONObject().put("type","string"))
                .put("finish",new JSONObject().put("type","string"))
                .put("format",new JSONObject().put("type","string"))
                .put("configuration",new JSONObject().put("type","string"))
                .put("material_variant_key",new JSONObject().put("type","string"))
                .put("materially_distinct_variant",new JSONObject().put("type","boolean"))
                .put("confidence",integerSchema(0,100))
                .put("evidence",new JSONObject().put("type","string"));
        JSONObject physicalCandidate=strictObject(physicalCandidateProps,"category_key","brand","family","model","year","subject","card_number","language","edition","printing","parallel","parallel_color","finish","format","configuration","material_variant_key","materially_distinct_variant","confidence","evidence");
        JSONObject evidenceFactProps=new JSONObject()
                .put("key",new JSONObject().put("type","string"))
                .put("value",new JSONObject().put("type","string"))
                .put("evidence_type",new JSONObject().put("type","string"))
                .put("confidence",integerSchema(0,100))
                .put("image_index",integerSchema(-1,20))
                .put("side",new JSONObject().put("type","string"))
                .put("location",new JSONObject().put("type","string"))
                .put("semantic_role",new JSONObject().put("type","string"));
        JSONObject evidenceFact=strictObject(evidenceFactProps,"key","value","evidence_type",
                "confidence","image_index","side","location","semantic_role");
        JSONObject labelProps = new JSONObject()
                .put("text", new JSONObject().put("type", "string"))
                .put("type", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("brand_logo").put("manufacturer_text")
                                .put("identifier").put("control").put("transient_display")
                                .put("descriptor").put("unknown")))
                .put("entity_role", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("foreground_product")
                                .put("component_or_insert").put("packaging_or_document")
                                .put("nearby_object").put("uncertain")))
                .put("identity_binding", new JSONObject().put("type", "boolean"));
        JSONObject label = strictObject(labelProps, "text", "type", "entity_role", "identity_binding");
        JSONObject hintProps = new JSONObject()
                .put("brand", new JSONObject().put("type", "string"))
                .put("family", new JSONObject().put("type", "string"))
                .put("model", new JSONObject().put("type", "string"))
                .put("confidence", integerSchema(0, 100))
                .put("reason", new JSONObject().put("type", "string"));
        JSONObject hint = strictObject(hintProps, "brand", "family", "model", "confidence", "reason");
        JSONObject photoIdentityProps = new JSONObject()
                .put("complete", new JSONObject().put("type", "boolean"))
                .put("canonical_name", new JSONObject().put("type", "string"))
                .put("identity_code", new JSONObject().put("type", "string"))
                .put("evidence_kind", new JSONObject().put("type", "string")
                        .put("enum", new JSONArray().put("none").put("identity_label")
                                .put("barcode").put("device_identity_screen")
                                .put("composite_markings")))
                .put("physical_binding", new JSONObject().put("type", "boolean"))
                .put("overlay_or_watermark", new JSONObject().put("type", "boolean"))
                .put("external_watermark", new JSONObject().put("type", "boolean"))
                .put("identity_obscured", new JSONObject().put("type", "boolean"))
                .put("identity_ambiguous",new JSONObject().put("type","boolean"))
                .put("materially_distinct_alternatives",integerSchema(0,6))
                .put("missing_discriminative_field",new JSONObject().put("type","string"))
                .put("discriminative_field_visible",new JSONObject().put("type","boolean"))
                .put("confidence", integerSchema(0, 100))
                .put("fields", stringArraySchema(24))
                .put("candidates",new JSONObject().put("type","array").put("maxItems",6).put("items",physicalCandidate))
                .put("evidence_facts",new JSONObject().put("type","array").put("maxItems",32).put("items",evidenceFact));
        JSONObject photoIdentity = strictObject(photoIdentityProps, "complete", "canonical_name",
                "identity_code", "evidence_kind", "physical_binding", "overlay_or_watermark",
                "external_watermark", "identity_obscured",
                "identity_ambiguous","materially_distinct_alternatives",
                "missing_discriminative_field","discriminative_field_visible",
                "confidence", "fields","candidates","evidence_facts");
        JSONObject props = new JSONObject()
                .put("observation_valid", new JSONObject().put("type", "boolean"))
                .put("observation_reason", new JSONObject().put("type", "string"))
                .put("title", new JSONObject().put("type", "string"))
                .put("category", new JSONObject().put("type", "string"))
                .put("category_key", new JSONObject().put("type", "string"))
                .put("brand", new JSONObject().put("type", "string"))
                .put("brand_evidence", new JSONObject().put("type", "string"))
                .put("brand_role_confidence", integerSchema(0, 100))
                .put("brand_role_reason", new JSONObject().put("type", "string"))
                .put("family", new JSONObject().put("type", "string"))
                .put("model", new JSONObject().put("type", "string"))
                .put("category_confidence", integerSchema(0, 100))
                .put("family_confidence", integerSchema(0, 100))
                .put("identity_confidence", integerSchema(0, 100))
                .put("identity_reason", new JSONObject().put("type", "string"))
                .put("distinctive_terms", stringArraySchema(10))
                .put("variant_facts", stringArraySchema(16))
                .put("visible_labels", new JSONObject().put("type", "array").put("maxItems", 20).put("items", label))
                .put("spatial_signature", stringArraySchema(14))
                .put("candidate_hints", stringArraySchema(5))
                .put("fast_candidates", new JSONObject().put("type", "array").put("maxItems", 4).put("items", hint))
                .put("photo_views", stringArraySchema(5))
                .put("visual_fingerprint", new JSONObject().put("type", "string"));
        props.put("photo_identity", photoIdentity);
        return jsonFormat("flipcheck_photographic_evidence_ledger_v124", strictObject(props, "observation_valid",
                "observation_reason", "title", "category", "category_key", "brand", "brand_evidence",
                "brand_role_confidence", "brand_role_reason", "family", "model", "category_confidence",
                "family_confidence", "identity_confidence", "identity_reason", "distinctive_terms",
                "variant_facts", "visible_labels", "spatial_signature", "candidate_hints",
                "fast_candidates", "photo_views", "visual_fingerprint", "photo_identity"));
    }

    private static JSONObject multimodalResolveFormat() throws Exception {
        JSONObject props = new JSONObject()
                .put("observation", observerFormat().getJSONObject("schema"))
                .put("resolution", webFormat("resolve").getJSONObject("schema"));
        return jsonFormat("flipcheck_multimodal_resolve_v082",
                strictObject(props, "observation", "resolution"));
    }

    static JSONObject observerFormatForTest() throws Exception {
        return observerFormat();
    }

    static JSONObject resolveFormatForTest() throws Exception {
        return webFormat("resolve");
    }

    static JSONObject multimodalResolveFormatForTest() throws Exception {
        return multimodalResolveFormat();
    }

    private static JSONObject strictObject(JSONObject properties, String... required) throws Exception {
        JSONArray names = new JSONArray();
        for (String name : required) {
            names.put(name);
        }
        return new JSONObject().put("type", "object").put("additionalProperties", false)
                .put("properties", properties).put("required", names);
    }

    private static JSONObject integerSchema(int min, int max) throws Exception {
        return new JSONObject().put("type", "integer").put("minimum", min).put("maximum", max);
    }

    private static JSONObject stringArraySchema(int maxItems) throws Exception {
        return new JSONObject().put("type", "array").put("maxItems", maxItems)
                .put("items", new JSONObject().put("type", "string"));
    }

    private Response call(JSONObject body, boolean vision) throws Exception {
        return call(body, vision, false);
    }

    private Response call(JSONObject body, boolean vision, boolean tolerantJson) throws Exception {
        if (this.apiKey.isEmpty()) {
            throw new IllegalStateException("Chiave API mancante");
        }
        long started = System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Authorization", "Bearer " + this.apiKey);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        OutputStream out = c.getOutputStream();
        try {
            out.write(bytes);
            if (out != null) {
                out.close();
            }
            int code = c.getResponseCode();
            InputStream stream = (code < 200 || code >= 300) ? c.getErrorStream() : c.getInputStream();
            String text = readAll(stream);
            String retryAfter = c.getHeaderField("Retry-After");
            c.disconnect();
            if (code < 200 || code >= 300) {
                throw ApiCallFailure.fromResponse(code, text).withRetryAfter(retryAfter);
            }
            JSONObject raw = new JSONObject(text);
            Response r = new Response();
            r.raw = raw;
            if ("incomplete".equalsIgnoreCase(raw.optString("status", ""))) {
                r.complete = false;
                JSONObject details = raw.optJSONObject("incomplete_details");
                r.incompleteReason = details == null ? "response_incomplete"
                        : details.optString("reason", "response_incomplete");
                r.parseError = r.incompleteReason;
                r.technicalStatus = r.incompleteReason.toLowerCase(Locale.ROOT).contains("max_output_tokens")
                        ? "INCOMPLETE_MAX_TOKENS" : "INVALID_JSON";
                r.payload = salvageCompactObservation(outputText(raw));
                r.sources.addAll(extractSources(raw));
                r.queries.addAll(extractQueries(raw));
                r.usage = usage(raw, vision, System.currentTimeMillis() - started);
                return r;
            }
            r.refusal = extractRefusal(raw);
            if (!r.refusal.isEmpty()) {
                r.complete = false;
                r.incompleteReason = "refusal";
                r.parseError = "refusal";
                r.technicalStatus = "CONTENT_INSUFFICIENT";
                r.payload = new JSONObject();
                r.sources.addAll(extractSources(raw));
                r.queries.addAll(extractQueries(raw));
                r.usage = usage(raw, vision, System.currentTimeMillis() - started);
                return r;
            }
            String generated = outputText(raw);
            try {
                r.payload = parseJsonObject(generated);
                if (r.payload.has("content_sufficient")
                        && !r.payload.optBoolean("content_sufficient", true)) {
                    r.technicalStatus = "CONTENT_INSUFFICIENT";
                }
            } catch (Exception parse) {
                if (!tolerantJson) {
                    throw parse;
                }
                r.parseError = parse.getMessage() == null ? "JSON non valido" : parse.getMessage();
                JSONObject salvaged = usesStrictFormat(body) ? null : salvageCompleteCandidates(generated);
                if (salvaged == null) salvaged = salvageCompactObservation(generated);
                r.payload = salvaged == null ? new JSONObject() : salvaged;
                r.technicalStatus = "INVALID_JSON";
            }
            r.sources.addAll(extractSources(raw));
            r.queries.addAll(extractQueries(raw));
            r.usage = usage(raw, vision, System.currentTimeMillis() - started);
            return r;
        } finally {
        }
    }

    private static boolean usesStrictFormat(JSONObject body) {
        JSONObject text = body == null ? null : body.optJSONObject("text");
        JSONObject format = text == null ? null : text.optJSONObject("format");
        return format != null && "json_schema".equals(format.optString("type"));
    }

    /** Keeps fully emitted compact facts/candidates even when the outer JSON was truncated. */
    private static JSONObject salvageCompactObservation(String generated) {
        JSONObject out = new JSONObject();
        if (generated == null || generated.trim().isEmpty()) return out;
        try { return parseJsonObject(generated); } catch (Exception ignored) {}
        try {
            out.put("content_sufficient", extractBoolean(generated, "content_sufficient", true));
            out.put("category", extractString(generated, "category"));
            out.put("identity_hint", extractString(generated, "identity_hint"));
            out.put("views", salvageStringArray(generated, "views"));
            out.put("facts", salvageObjectArray(generated, "facts"));
            out.put("candidates", salvageObjectArray(generated, "candidates"));
            out.put("missing_discriminators", salvageStringArray(generated,
                    "missing_discriminators"));
        } catch (Exception ignored) { return new JSONObject(); }
        return out;
    }

    private static JSONArray salvageObjectArray(String text, String key) {
        JSONArray out = new JSONArray(); int start = arrayStart(text, key);
        if (start < 0) return out;
        boolean quoted=false,escaped=false; int depth=0,objectStart=-1;
        for (int i=start+1;i<text.length();i++) {
            char ch=text.charAt(i);
            if (quoted) { if (escaped) escaped=false; else if (ch=='\\') escaped=true;
                else if (ch=='\"') quoted=false; continue; }
            if (ch=='\"') { quoted=true; continue; }
            if (ch=='{') { if (depth==0) objectStart=i; depth++; }
            else if (ch=='}'&&depth>0) { depth--; if (depth==0&&objectStart>=0) {
                try { out.put(new JSONObject(text.substring(objectStart,i+1))); } catch(Exception ignored) {}
                objectStart=-1;
            }} else if (ch==']'&&depth==0) break;
        }
        return out;
    }

    private static JSONArray salvageStringArray(String text, String key) {
        JSONArray out=new JSONArray(); int start=arrayStart(text,key); if(start<0)return out;
        boolean quoted=false,escaped=false;StringBuilder value=new StringBuilder();
        for(int i=start+1;i<text.length();i++){char ch=text.charAt(i);
            if(quoted){if(escaped){value.append(ch);escaped=false;}else if(ch=='\\')escaped=true;
                else if(ch=='\"'){quoted=false;out.put(value.toString());value.setLength(0);}else value.append(ch);}
            else if(ch=='\"')quoted=true;else if(ch==']')break;}
        return out;
    }

    private static int arrayStart(String text,String key){int k=text.indexOf("\""+key+"\"");return k<0?-1:text.indexOf('[',k);}
    private static String extractString(String text,String key){int k=text.indexOf("\""+key+"\"");if(k<0)return "";int colon=text.indexOf(':',k),q=colon<0?-1:text.indexOf('\"',colon);if(q<0)return "";StringBuilder b=new StringBuilder();boolean escaped=false;for(int i=q+1;i<text.length();i++){char ch=text.charAt(i);if(escaped){b.append(ch);escaped=false;}else if(ch=='\\')escaped=true;else if(ch=='\"')break;else b.append(ch);}return b.toString();}
    private static boolean extractBoolean(String text,String key,boolean fallback){int k=text.indexOf("\""+key+"\"");if(k<0)return fallback;int colon=text.indexOf(':',k);if(colon<0)return fallback;String tail=text.substring(colon+1).trim();return tail.startsWith("true")||(!tail.startsWith("false")&&fallback);}

    private static Models.Usage usage(JSONObject jSONObject, boolean z, long j) {
        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.visionCalls = z ? 1 : 0;
        usage.webCalls = countWebCalls(jSONObject);
        usage.apiMs = j;
        JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("usage");
        if (jSONObjectOptJSONObject != null) {
            usage.inputTokens = jSONObjectOptJSONObject.optLong("input_tokens", 0L);
            usage.outputTokens = jSONObjectOptJSONObject.optLong("output_tokens", 0L);
            JSONObject jSONObjectOptJSONObject2 = jSONObjectOptJSONObject.optJSONObject("input_tokens_details");
            if (jSONObjectOptJSONObject2 != null) {
                usage.cachedTokens = jSONObjectOptJSONObject2.optLong("cached_tokens", 0L);
            }
        }
        usage.costUsd = (((Math.max(0L, usage.inputTokens - usage.cachedTokens) * 0.2d) + (usage.cachedTokens * 0.02d)) + (usage.outputTokens * 1.2d)) / 1000000.0d;
        usage.costUsd += usage.webCalls * 0.01d;
        return usage;
    }

    private static int countWebCalls(JSONObject raw) {
        int n = 0;
        JSONArray out = raw.optJSONArray("output");
        if (out == null) {
            return 0;
        }
        for (int i = 0; i < out.length(); i++) {
            JSONObject x = out.optJSONObject(i);
            if (x != null && "web_search_call".equals(x.optString("type"))) {
                n++;
            }
        }
        return n;
    }

    private static String outputText(JSONObject raw) {
        JSONArray content;
        String direct = raw.optString("output_text", "");
        if (!direct.isEmpty()) {
            return direct;
        }
        StringBuilder sb = new StringBuilder();
        JSONArray output = raw.optJSONArray("output");
        if (output == null) {
            return "";
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item != null && "message".equals(item.optString("type")) && (content = item.optJSONArray("content")) != null) {
                for (int j = 0; j < content.length(); j++) {
                    JSONObject z = content.optJSONObject(j);
                    if (z != null && "output_text".equals(z.optString("type"))) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(z.optString("text", ""));
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String extractRefusal(JSONObject raw) {
        JSONArray output = raw == null ? null : raw.optJSONArray("output");
        if (output == null) {
            return "";
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            JSONArray content = item == null ? null : item.optJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "refusal".equals(part.optString("type"))) {
                    return part.optString("refusal", part.optString("text", "refusal"));
                }
            }
        }
        return "";
    }

    static JSONObject salvageCompleteCandidates(String text) {
        int key;
        int arr;
        if (text == null || text.trim().isEmpty() || (key = text.indexOf("\"candidates\"")) < 0 || (arr = text.indexOf(91, key)) < 0) {
            return null;
        }
        JSONArray candidates = new JSONArray();
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        int start = -1;
        for (int i = arr + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '\"') {
                    inString = false;
                }
            } else if (ch != '\"') {
                if (ch == '{') {
                    if (depth == 0) {
                        start = i;
                    }
                    depth++;
                } else if (ch == '}' && depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        String obj = text.substring(start, i + 1);
                        try {
                            candidates.put(new JSONObject(obj));
                        } catch (Exception e) {
                        }
                        start = -1;
                        if (candidates.length() >= 6) {
                            break;
                        }
                    }
                } else if (ch == ']' && depth == 0) {
                    break;
                }
            } else {
                inString = true;
            }
        }
        int i2 = candidates.length();
        if (i2 == 0) {
            return null;
        }
        try {
            return new JSONObject().put("candidates", candidates).put("next_photo_request", "").put("next_photo_reason", "").put("recovered_truncated_json", true);
        } catch (Exception e2) {
            return null;
        }
    }

    private static JSONObject parseJsonObject(String text) throws Exception {
        if (text == null) {
            throw new IllegalStateException("Risposta AI vuota");
        }
        String text2 = text.trim().replaceFirst("(?is)^```(?:json)?\\s*", "").replaceFirst("(?is)\\s*```$", "").trim();
        int a = text2.indexOf(123);
        int b = text2.lastIndexOf(125);
        if (a >= 0 && b > a) {
            text2 = text2.substring(a, b + 1);
        }
        return new JSONObject(text2);
    }

    private static List<String> extractQueries(JSONObject raw) {
        JSONObject action;
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JSONArray output = raw.optJSONArray("output");
        if (output == null) {
            return out;
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item != null && "web_search_call".equals(item.optString("type")) && (action = item.optJSONObject("action")) != null) {
                addQuery(action.optString("query", ""), out, seen);
                JSONArray queries = action.optJSONArray("queries");
                if (queries != null) {
                    for (int j = 0; j < queries.length(); j++) {
                        addQuery(queries.optString(j, ""), out, seen);
                    }
                }
            }
        }
        return out;
    }

    private static void addQuery(String q, List<String> out, Set<String> seen) {
        String q2 = q == null ? "" : q.trim();
        if (q2.isEmpty()) {
            return;
        }
        String key = q2.toLowerCase(Locale.ROOT);
        if (seen.add(key)) {
            out.add(q2);
        }
    }

    private static List<Models.Source> extractSources(JSONObject raw) {
        JSONArray content;
        List<Models.Source> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JSONArray output = raw.optJSONArray("output");
        if (output == null) {
            return out;
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item != null) {
                if ("web_search_call".equals(item.optString("type"))) {
                    JSONObject action = item.optJSONObject("action");
                    if (action != null) {
                        addArray(action.optJSONArray("sources"), out, seen);
                        addArray(action.optJSONArray("results"), out, seen);
                    }
                    addArray(item.optJSONArray("sources"), out, seen);
                    addArray(item.optJSONArray("results"), out, seen);
                }
                if ("message".equals(item.optString("type")) && (content = item.optJSONArray("content")) != null) {
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject z = content.optJSONObject(j);
                        if (z != null) {
                            addAnnotations(z.optJSONArray("annotations"), out, seen);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void addAnnotations(JSONArray a, List<Models.Source> out, Set<String> seen) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                JSONObject c = x.optJSONObject("url_citation");
                if (c == null && "url_citation".equals(x.optString("type"))) {
                    c = x;
                }
                if (c != null) {
                    String url = c.optString("url", "").trim();
                    if (!url.isEmpty() && seen.add(url)) {
                        Models.Source s = new Models.Source();
                        s.url = url;
                        s.title = c.optString("title", "");
                        s.snippet = c.optString("text", "");
                        out.add(s);
                    }
                }
            }
        }
    }

    private static void addArray(JSONArray a, List<Models.Source> out, Set<String> seen) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                String url = x.optString("url", "").trim();
                if (!url.isEmpty() && seen.add(url)) {
                    Models.Source s = new Models.Source();
                    s.url = url;
                    s.title = x.optString("title", x.optString("name", ""));
                    s.snippet = x.optString("snippet", x.optString("text", ""));
                    out.add(s);
                }
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[8192];
                while (true) {
                    int n = in.read(buf);
                    if (n < 0) {
                        break;
                    }
                    out.write(buf, 0, n);
                }
                String string = out.toString("UTF-8");
                out.close();
                if (in != null) {
                    in.close();
                }
                return string;
            } finally {
            }
        } catch (Throwable th) {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }
}
