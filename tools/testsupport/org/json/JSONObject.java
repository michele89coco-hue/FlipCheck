package org.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

/** Desktop-only Android org.json compatibility adapter. Never packaged in the APK. */
public class JSONObject {
    final JsonObject value;
    public JSONObject(){value=new JsonObject();}
    public JSONObject(String json){value=JsonParser.parseString(json).getAsJsonObject();}
    JSONObject(JsonObject object){value=object==null?new JsonObject():object;}
    public JSONObject put(String key,Object item){value.add(key,wrap(item));return this;}
    public boolean has(String key){return value.has(key);}
    public int length(){return value.size();}
    public Object opt(String key){return unwrap(value.get(key));}
    public String optString(String key){return optString(key,"");}
    public String optString(String key,String fallback){JsonElement e=value.get(key);return e==null||e.isJsonNull()?fallback:e.getAsString();}
    public int optInt(String key){return optInt(key,0);}
    public int optInt(String key,int fallback){try{JsonElement e=value.get(key);return e==null||e.isJsonNull()?fallback:e.getAsInt();}catch(Exception ignored){return fallback;}}
    public long optLong(String key,long fallback){try{JsonElement e=value.get(key);return e==null||e.isJsonNull()?fallback:e.getAsLong();}catch(Exception ignored){return fallback;}}
    public double optDouble(String key,double fallback){try{JsonElement e=value.get(key);return e==null||e.isJsonNull()?fallback:e.getAsDouble();}catch(Exception ignored){return fallback;}}
    public boolean optBoolean(String key){return optBoolean(key,false);}
    public boolean optBoolean(String key,boolean fallback){try{JsonElement e=value.get(key);return e==null||e.isJsonNull()?fallback:e.getAsBoolean();}catch(Exception ignored){return fallback;}}
    public JSONObject optJSONObject(String key){JsonElement e=value.get(key);return e!=null&&e.isJsonObject()?new JSONObject(e.getAsJsonObject()):null;}
    public JSONArray optJSONArray(String key){JsonElement e=value.get(key);return e!=null&&e.isJsonArray()?new JSONArray(e.getAsJsonArray()):null;}
    public JSONObject getJSONObject(String key){JSONObject o=optJSONObject(key);if(o==null)throw new IllegalStateException(key);return o;}
    public JSONArray getJSONArray(String key){JSONArray a=optJSONArray(key);if(a==null)throw new IllegalStateException(key);return a;}
    public String getString(String key){if(!has(key))throw new IllegalStateException(key);return optString(key);}
    public int getInt(String key){if(!has(key))throw new IllegalStateException(key);return optInt(key);}
    public String toString(){return value.toString();}
    static JsonElement wrap(Object item){if(item==null)return com.google.gson.JsonNull.INSTANCE;if(item instanceof JSONObject)return ((JSONObject)item).value;if(item instanceof JSONArray)return ((JSONArray)item).value;if(item instanceof Number)return new com.google.gson.JsonPrimitive((Number)item);if(item instanceof Boolean)return new com.google.gson.JsonPrimitive((Boolean)item);return new com.google.gson.JsonPrimitive(String.valueOf(item));}
    static Object unwrap(JsonElement e){if(e==null||e.isJsonNull())return null;if(e.isJsonObject())return new JSONObject(e.getAsJsonObject());if(e.isJsonArray())return new JSONArray(e.getAsJsonArray());if(e.getAsJsonPrimitive().isBoolean())return e.getAsBoolean();if(e.getAsJsonPrimitive().isNumber())return e.getAsNumber();return e.getAsString();}
}
