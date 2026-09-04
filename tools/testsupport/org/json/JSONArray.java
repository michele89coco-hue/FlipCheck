package org.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/** Desktop-only Android org.json compatibility adapter. Never packaged in the APK. */
public class JSONArray {
    final JsonArray value;
    public JSONArray(){value=new JsonArray();}
    public JSONArray(String json){value=com.google.gson.JsonParser.parseString(json).getAsJsonArray();}
    JSONArray(JsonArray array){value=array==null?new JsonArray():array;}
    public JSONArray put(Object item){value.add(JSONObject.wrap(item));return this;}
    public int length(){return value.size();}
    public Object opt(int index){return index<0||index>=value.size()?null:JSONObject.unwrap(value.get(index));}
    public JSONObject optJSONObject(int index){JsonElement e=index<0||index>=value.size()?null:value.get(index);return e!=null&&e.isJsonObject()?new JSONObject(e.getAsJsonObject()):null;}
    public JSONArray optJSONArray(int index){JsonElement e=index<0||index>=value.size()?null:value.get(index);return e!=null&&e.isJsonArray()?new JSONArray(e.getAsJsonArray()):null;}
    public String optString(int index){return optString(index,"");}
    public String optString(int index,String fallback){try{JsonElement e=index<0||index>=value.size()?null:value.get(index);return e==null||e.isJsonNull()?fallback:e.getAsString();}catch(Exception ignored){return fallback;}}
    public int optInt(int index,int fallback){try{JsonElement e=index<0||index>=value.size()?null:value.get(index);return e==null||e.isJsonNull()?fallback:e.getAsInt();}catch(Exception ignored){return fallback;}}
    public JSONObject getJSONObject(int index){JSONObject o=optJSONObject(index);if(o==null)throw new IllegalStateException(String.valueOf(index));return o;}
    public String getString(int index){if(index<0||index>=value.size())throw new IllegalStateException(String.valueOf(index));return optString(index);}
    public String toString(){return value.toString();}
}
