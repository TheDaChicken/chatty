
package chatty.util;

import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author tduva
 */
public class JSONUtil {
    
    /**
     * Gets the String value for the given key.
     * 
     * @param data The JSONObject
     * @param key The key
     * @return The String value, or null if there is no value for this key or
     * it's not a String
     */
    public static String getString(JSONObject data, Object key) {
        Object value = data.get(key);
        if (value != null && value instanceof String) {
            return (String)value;
        }
        return null;
    }
    
    public static List<String> getStringList(JSONObject data, Object key) {
        Object value = data.get(key);
        if (value != null && value instanceof JSONArray) {
            List<String> result = new ArrayList<>();
            for (Object item : (JSONArray)value) {
                if (item instanceof String) {
                    result.add((String)item);
                }
            }
            return result;
        }
        return null;
    }
    
    /**
     * Gets the integer value for the given key.
     * 
     * @param data The JSONObject
     * @param key The key
     * @param errorValue The value to return if no integer was found for this
     * key
     * @return The integer value, or the given errorValue if no integer was
     * found for this key
     */
    public static int getInteger(JSONObject data, Object key, int errorValue) {
        Object value = data.get(key);
        if (value != null && value instanceof Number) {
            return ((Number)value).intValue();
        }
        return errorValue;
    }
    
    public static int parseInteger(JSONObject data, Object key, int errorValue) {
        try {
            return Integer.parseInt((String)data.get(key));
        } catch (Exception ex) {
            return errorValue;
        }
    }
    
    public static long parseLong(JSONObject data, Object key, long errorValue) {
        try {
            return Long.parseLong((String)data.get(key));
        } catch (Exception ex) {
            return errorValue;
        }
    }
    
    public static long getLong(JSONObject data, Object key, long errorValue) {
        Object value = data.get(key);
        if (value != null && value instanceof Number) {
            return ((Number)value).longValue();
        }
        return errorValue;
    }
    
    public static boolean getBoolean(JSONObject data, Object key, boolean errorValue) {
        Object value = data.get(key);
        if (value != null && value instanceof Boolean) {
            return (Boolean)value;
        }
        return errorValue;
    }
    
    public static String listToJSON(Object... args) {
        JSONArray o = new JSONArray();
        for (Object a : args) {
            o.add(a);
        }
        return o.toJSONString();
    }

    public static Object parseJSON(String json) {
        JSONParser parser = new JSONParser();
        try {
            return parser.parse(json);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static JSONArray parseJSONAsList(String json) {
        JSONParser parser = new JSONParser();
        JSONArray root = null;
        try {
            root = (JSONArray)parser.parse(json);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return root;
    }
    
}
