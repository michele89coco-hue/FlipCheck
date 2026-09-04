package com.flipcheck.nativebeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One vocabulary for every photographic fact consumed after parsing. */
enum CanonicalFieldKey {
    MANUFACTURER("manufacturer"), PUBLISHER("publisher"), BRAND("brand"),
    PRODUCT_TYPE("productType"), PRODUCT_NAME("productName"), PRODUCT_LINE("productLine"), SET("set"), SERIES("series"),
    MAIN_SET("mainSet"), INSERT_SUBSET("insertSubset"), DESIGN_FAMILY("designFamily"), SUB_SERIES("subSeries"),
    SUBJECT("subject"), FEATURED_SUBJECT("featuredSubjects"), TEAM("team"), LANGUAGE("language"),
    FORMAT("format"), CONFIGURATION("configuration"), MODEL_CODE("modelCode"), BARCODE("barcode"),
    CARD_NUMBER_CANDIDATE("cardNumberCandidate"), COLLECTOR_NUMBER_CANDIDATE("collectorNumberCandidate"),
    PHYSICAL_SERIAL_CANDIDATE("physicalSerialCandidate"), PHYSICAL_PARALLEL_CANDIDATE("physicalParallelCandidate"),
    PARALLEL_FAMILY("parallelFamily"), PARALLEL_COLOR("parallelColor"), PRINT_RUN("printRun"),
    FINISH("finish"), EDITION("edition"), PRINTING("printing"),
    FIRST_EDITION_MARK("firstEditionMark"), SHADOW_STATUS("shadowStatus"),
    HOLO_STATUS("holoStatus"), REVERSE_HOLO_STATUS("reverseHoloStatus"),
    SET_CODE("setCode"), RARITY("rarity"), ARTIST("artist"), PROMO_MARK("promoMark"),
    VISUAL_SYMBOL("visualSymbol"),
    ROOKIE_MARKER("rookieMarker"), EVOLUTION_STAGE("evolutionStage"), HP_OR_PV("hpOrPv"), ATTACK_NAME("attackNames"),
    ATTACK_TEXT("attackText"), LAYOUT_SIGNATURE("layoutSignature"), ARTWORK_SIGNATURE("artworkSignature"),
    AUTOGRAPH("autograph"), MEMORABILIA("memorabilia"), CONDITION("condition"),
    GRAPHIC_NUMBER("graphicNumber"), ATTACK_DAMAGE("attackDamage"), CARD_TYPE("cardType"),
    DISTINCTIVE_PRINTED_TOKEN("distinctivePrintedToken"), POSITION("position"), HEIGHT("height"),
    WEIGHT("weight"), BIRTHPLACE("birthplace"),
    STATISTICS("statistics"), PROMOTIONAL_TEXT("promotionalText"), VISUAL_DESCRIPTION("visualDescription"),
    PHYSICAL_SET_OR_RELEASE_YEAR("physicalSetOrReleaseYear"), COPYRIGHT_YEAR("copyrightYear"),
    STATISTICAL_SEASON("statisticalSeason"), SOURCE_CONFIRMED_RELEASE_YEAR("sourceConfirmedReleaseYear"),
    FRONT_COMPLETE("frontComplete"), INSERT_LEVEL("insertOrLevel"), SPORT("sport"), DESIGN("design"),
    PACKAGE_COUNT("packageCount"), CARDS_PER_PACK("cardsPerPack"), AUTOGRAPH_GUARANTEE("autographGuarantee"),
    MEMORABILIA_GUARANTEE("memorabiliaGuarantee"), SEALED_STATUS("sealedStatus"),
    NUMBER_BINDING("numberBinding"), NUMBER_SEMANTIC("numberSemantic"), NUMBER_LOCATION("numberLocation"),
    SERIAL_BINDING("serialBinding"), SERIAL_LOCATION("serialLocation"), GRADED("graded"),
    SOURCE_CONFIRMED_CATALOG_NUMBER("sourceConfirmedCatalogNumber"),
    SOURCE_CONFIRMED_PRODUCT_LINE("sourceConfirmedProductLine"),
    SOURCE_CONFIRMED_VARIANT("sourceConfirmedVariant"), SOURCE_CATALOG_TITLE("sourceCatalogTitle"),
    MARKET_COMPARABLE("marketComparable"),
    CONTROL_LAYOUT("controlLayout"), SHORTCUT_BUTTONS("shortcutButtons"), BRAND_MARK("brandMark"),
    NAVIGATION_LAYOUT("navigationLayout"), NUMERIC_KEYPAD("numericKeypad"), VOICE_CONTROL("voiceControl"),
    UNKNOWN("unknown");

    final String debugName;
    CanonicalFieldKey(String debugName){this.debugName=debugName;}

    private static final Map<String,CanonicalFieldKey> ALIASES=aliases();
    static CanonicalFieldKey fromAlias(String raw){CanonicalFieldKey key=ALIASES.get(normalize(raw));return key==null?UNKNOWN:key;}
    static Map<String,CanonicalFieldKey> aliasMap(){return Collections.unmodifiableMap(ALIASES);}

    private static Map<String,CanonicalFieldKey> aliases(){Map<String,CanonicalFieldKey> m=new LinkedHashMap<>();
        put(m,MANUFACTURER,"manufacturer","maker","producer","manufacturer_publisher","manufacturer/publisher","manufacturer_or_publisher");
        put(m,PUBLISHER,"publisher");
        put(m,BRAND,"brand","game_brand","game","game_or_publisher");
        put(m,PRODUCT_TYPE,"product_type","sealed_product_type","object_type","device_type","human_category");
        put(m,PRODUCT_NAME,"product_name","product_title","canonical_product_name","model");
        put(m,PRODUCT_LINE,"product_line","set_or_product_line","set/product_line","product_family","collection",
                "set_or_product_line_name");
        put(m,SET,"set","set_or_series"); put(m,SERIES,"series","family");
        put(m,MAIN_SET,"main_set","parent_set","release_main_set","base_set");
        put(m,INSERT_SUBSET,"insert_set","insert_subset","subset","subset_name","card_subset");
        put(m,DESIGN_FAMILY,"design_family","design_series","legacy_design_family");
        put(m,SUB_SERIES,"sub_series","subseries","product_line_qualifier","release_qualifier");
        put(m,SUBJECT,"subject","subject_name","card_name","character","player","athlete");
        put(m,FEATURED_SUBJECT,"featured_subject","featured_subjects","athlete_on_packaging","featured_player");
        put(m,TEAM,"team","club"); put(m,LANGUAGE,"language","card_language");
        put(m,FORMAT,"format","sealed_format","box_format","retail_format","product_format");
        put(m,CONFIGURATION,"configuration","pack_configuration","hit_guarantee","pack_count","contents");
        put(m,MODEL_CODE,"model_code","physical_model_code","physical_product_code","physical_part_number","physical_sku","product_code","part_number","sku","p/n","pn");
        put(m,BARCODE,"barcode","physical_barcode","ean","upc","gtin");
        put(m,CARD_NUMBER_CANDIDATE,"card_number","physical_card_number","physical_card_number_marking","card_identifier");
        put(m,COLLECTOR_NUMBER_CANDIDATE,"collector_number","collector_marking","physical_collector_number");
        put(m,PHYSICAL_SERIAL_CANDIDATE,"physical_serial","physical_serial_marking","physical_print_run","card_surface_serial","serial_fraction");
        put(m,PHYSICAL_PARALLEL_CANDIDATE,"physical_parallel","parallel","parallel_name_marking");
        put(m,PARALLEL_FAMILY,"parallel_family","parallel_series","parallel_type");
        put(m,PARALLEL_COLOR,"parallel_color","variant_color");
        put(m,PRINT_RUN,"print_run","numbered_to","serial_denominator","edition_size");
        put(m,FINISH,"finish","surface_finish");
        put(m,HOLO_STATUS,"holo","holo_status","holo_mark");
        put(m,REVERSE_HOLO_STATUS,"reverse_holo","reverse_holo_status","reverse_holo_mark");
        put(m,EDITION,"edition","print_edition"); put(m,PRINTING,"printing","physical_printing","print_variant");
        put(m,FIRST_EDITION_MARK,"first_edition_mark","first_edition_logo","edition_mark","edition_logo","1st_edition_mark");
        put(m,SHADOW_STATUS,"shadow_status","shadowless","frame_shadow_status");
        put(m,SET_CODE,"set_code","expansion_code"); put(m,RARITY,"rarity","rarity_symbol");
        put(m,ARTIST,"artist","illustrator","illustration_credit");
        put(m,PROMO_MARK,"promo_mark","promo_symbol"); put(m,VISUAL_SYMBOL,"visual_symbol","visual_symbols","set_symbol");
        put(m,ROOKIE_MARKER,"rookie_marker","rookie","rc_marker");
        put(m,EVOLUTION_STAGE,"evolution_stage","evolution_level","pokemon_stage","creature_stage");
        put(m,HP_OR_PV,"hp","pv","hp_value","hp/stat","hp_pv","hp_or_pv","HP/PV","health_points");
        put(m,ATTACK_NAME,"attack","attack_name","attack_names","attacks","move","move_name","moves");
        put(m,ATTACK_TEXT,"move_text","attack_text","characteristic_text");
        put(m,ATTACK_DAMAGE,"attack_damage","move_damage","damage");
        put(m,GRAPHIC_NUMBER,"graphic_number","game_number","rating_number","front_graphic_number");
        put(m,CARD_TYPE,"card_type","element","energy_type");
        put(m,DISTINCTIVE_PRINTED_TOKEN,"distinctive_printed_token","distinctive_token","printed_token","printed_label","remote_feature","product_line_token","title_token","subline_token");
        put(m,CONTROL_LAYOUT,"control_layout","button_layout","control_topology");
        put(m,SHORTCUT_BUTTONS,"shortcut_buttons","app_buttons","streaming_shortcuts");
        put(m,BRAND_MARK,"brand_mark","brand_logo","logo_mark");
        put(m,NAVIGATION_LAYOUT,"navigation_layout","directional_layout","navigation_pad");
        put(m,NUMERIC_KEYPAD,"numeric_keypad","number_pad");put(m,VOICE_CONTROL,"voice_control","voice_button");
        put(m,POSITION,"position","player_position"); put(m,HEIGHT,"height","player_height");
        put(m,WEIGHT,"weight","player_weight"); put(m,BIRTHPLACE,"birthplace","birth_place","place_of_birth");
        put(m,LAYOUT_SIGNATURE,"layout","frame","layout_signature","layout_match","distinctive_layout","layout_distinctive");
        put(m,ARTWORK_SIGNATURE,"illustration","artwork","visual_subject_layout","artwork_signature");
        put(m,AUTOGRAPH,"autograph","autographed","signature_present");
        put(m,MEMORABILIA,"memorabilia","relic","game_used_material");
        put(m,CONDITION,"condition","physical_condition");
        put(m,STATISTICS,"statistics","statistic","rating","ratings","offense","defense","jersey_number");
        put(m,PROMOTIONAL_TEXT,"promotional_text","slogan","marketing_text","activation_code");
        put(m,VISUAL_DESCRIPTION,"visual_description","appearance","surface_description");
        put(m,PHYSICAL_SET_OR_RELEASE_YEAR,"set_year","release_year","release","printed_year","manufacture_year","physical_year","season_year","season","year");
        put(m,COPYRIGHT_YEAR,"copyright_year");
        put(m,STATISTICAL_SEASON,"statistical_season","stats_season","stat_season","season_stats");
        put(m,SOURCE_CONFIRMED_RELEASE_YEAR,"source_confirmed_release_year","catalog_release_year");
        put(m,FRONT_COMPLETE,"front_complete","complete_front"); put(m,INSERT_LEVEL,"insert","level","tier");
        put(m,SPORT,"sport","sport_category","sports_category","game_category","category_sport"); put(m,DESIGN,"design","item_name","character_design");
        put(m,PACKAGE_COUNT,"package_count","pack_count","packs_per_box","number_of_packs");
        put(m,CARDS_PER_PACK,"cards_per_pack","card_count_per_pack");
        put(m,AUTOGRAPH_GUARANTEE,"autograph_guarantee","autographs_per_box","autograph_configuration");
        put(m,MEMORABILIA_GUARANTEE,"memorabilia_guarantee","memorabilia_per_box","relic_configuration");
        put(m,SEALED_STATUS,"sealed_status","package_sealed","factory_sealed");
        put(m,NUMBER_BINDING,"card_number_binding","physical_card_number_binding","collector_number_binding","number_binding");
        put(m,NUMBER_SEMANTIC,"card_number_semantic","physical_number_semantic","collector_number_semantic","number_semantic");
        put(m,NUMBER_LOCATION,"card_number_location","physical_card_number_location","collector_number_location","number_location");
        put(m,SERIAL_BINDING,"serial_binding"); put(m,SERIAL_LOCATION,"serial_location","physical_serial_location");
        put(m,GRADED,"graded","slabbed","grading_label_physical");
        put(m,SOURCE_CONFIRMED_CATALOG_NUMBER,"source_confirmed_catalog_number","catalog_number");
        put(m,SOURCE_CONFIRMED_PRODUCT_LINE,"source_confirmed_product_line","catalog_product_line","source_confirmed_set_or_product_line");
        put(m,SOURCE_CONFIRMED_VARIANT,"source_confirmed_variant","catalog_variant");
        put(m,SOURCE_CATALOG_TITLE,"source_catalog_title","catalog_title");
        put(m,MARKET_COMPARABLE,"market_comparable","comparable","sold_comparable"); return m;
    }
    private static void put(Map<String,CanonicalFieldKey> m,CanonicalFieldKey key,String...aliases){for(String alias:aliases)m.put(normalize(alias),key);}
    static String normalize(String raw){String x=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);int p=x.indexOf('=');if(p<1)p=x.indexOf(':');if(p>0)x=x.substring(0,p);return x.replace("/","_or_").replace('-','_').replace(' ','_').replaceAll("_+","_");}
}
