package chatty;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.net.HttpCookie;
import java.util.*;

public class Cookies {

    public static class Cookie {
        public String name;
        public String value;
        public long expirationDate = 0;
        public String path = null;
        public String domain = null;
        public boolean secure = false;
    }

    /*
        Parse to Cookies
     */
    public static List<HttpCookie> loadCookies(String cookies_json) {
        JSONParser parser = new JSONParser();
        Object root = null;
        try {
            root = parser.parse(cookies_json);
        } catch (ParseException e) {
            return null;
        }
        if(!(root instanceof JSONArray)) {
            return null;
        }
        JSONArray array = (JSONArray) root;
        List<HttpCookie> cookies = new ArrayList<>();
        for(Object o : array) {
            if(!(o instanceof JSONObject)) {
                return null;
            }
            JSONObject jsobj = (JSONObject) o;
            Cookie cookie = new Cookie();
            for(Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) jsobj.entrySet()) {
                if(entry.getKey().equalsIgnoreCase("name")) {
                    cookie.name = (String) entry.getValue();
                }
                if(entry.getKey().equalsIgnoreCase("value")) {
                    cookie.value = (String) entry.getValue();
                }
                if(entry.getKey().equalsIgnoreCase("expirationDate")) {
                    Object obj = entry.getValue();
                    if(obj instanceof Long) {
                        cookie.expirationDate = (Long) entry.getValue();
                    } else if(obj instanceof Double) {
                        cookie.expirationDate = Double.valueOf((double) entry.getValue()).longValue();
                    }
                }
                if(entry.getKey().equalsIgnoreCase("path")) {
                    cookie.path = (String) entry.getValue();
                }
                if(entry.getKey().equalsIgnoreCase("domain")) {
                    cookie.domain = (String) entry.getValue();
                }
                if(entry.getKey().equalsIgnoreCase("secure")) {
                    cookie.secure = (boolean) entry.getValue();
                }
            }

            HttpCookie httpcookie = new HttpCookie(cookie.name, cookie.value);
            httpcookie.setPath(cookie.path);
            httpcookie.setDomain(cookie.domain);
            httpcookie.setSecure(cookie.secure);
            if(cookie.expirationDate != 0 ) {
                httpcookie.setMaxAge(cookie.expirationDate);
            }
            cookies.add(httpcookie);
        }
        if(cookies.size() == 0) {
            return null;
        }
        return cookies;
    }

}
