package com.flipcheck.nativebeta;

/** Marks only actually used retrieved URLs as catalog or market sources. */
final class SourceClassificationPolicy {
    private SourceClassificationPolicy() {}
    static boolean mark(Models.Identification id,String url,String type,int relevance){if(id==null||safe(url).isEmpty())return false;for(Models.Source s:id.sources)if(s!=null&&norm(s.url).equals(norm(url))){s.sourceType=type;s.relevance=Math.max(s.relevance,relevance);s.strong=s.relevance>=50;return true;}return false;}
    static boolean importAndMark(Models.Identification id,OpenAiClient.Response response,String url,String type,int relevance){
        if(id==null||response==null||safe(url).isEmpty())return false;
        if(!mark(id,url,type,relevance))for(Models.Source source:response.sources)if(source!=null&&norm(source.url).equals(norm(url))){
            Models.Source copy=new Models.Source();copy.url=source.url;copy.title=source.title;copy.snippet=source.snippet;
            copy.sourceType=type;copy.relevance=Math.max(source.relevance,relevance);copy.strong=copy.relevance>=50;id.sources.add(copy);return true;
        }
        return mark(id,url,type,relevance);
    }
    static int count(Models.Identification id,String type){int n=0;if(id!=null)for(Models.Source s:id.sources)if(s!=null&&type.equals(s.sourceType)&&!safe(s.url).isEmpty())n++;return n;}
    private static String norm(String x){String v=safe(x).toLowerCase(java.util.Locale.ROOT);while(v.endsWith("/"))v=v.substring(0,v.length()-1);return v;}
    private static String safe(String x){return x==null?"":x.trim();}
}
