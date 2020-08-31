package chatty.util.api;

import com.sun.net.httpserver.HttpExchange;
import org.json.simple.JSONObject;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RequestInput {

    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/84.0.4147.125 Safari/537.36";

    private HashMap<String, String> headers = new HashMap<>();

    private final String url;

    private RequestMethods requestMethod = RequestMethods.GET;

    private String requestData = null;
    private String contentType = null;
    private Request.RequestResult origin = null;

    private List<HttpCookie> cookies = null;
    private CookieManager cookieManager = null;

    private String auth = null;

    public RequestInput(String url) {
        this.url = url;
    }

    public String getUrl() {
        return this.url;
    }

    public String getRequestData() {
        return this.requestData;
    }

    public HashMap<String, String> getHeaders() {
        return this.headers;
    }

    public RequestMethods getRequestMethod() {
        return this.requestMethod;
    }

    public String getContentType() {
        return this.contentType;
    }

    public CookieManager getCookieManager() {
        return this.cookieManager;
    }

    public List<HttpCookie> getCookies() { return this.cookies; }

    public String getAuth() { return this.auth; }

    public void setContentType(String content_type) {
        this.contentType = content_type;
    }

    public RequestInput setData(String data) {
        this.requestData = data;
        return this;
    }

    public RequestInput setData(JSONObject object) {
        this.requestData = object.toJSONString();
        this.contentType = "application/json";
        return this;
    }

    public RequestInput addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public RequestInput putHeaders(HashMap<String, String> put) {
        this.headers.putAll(put);
        return this;
    }

    public RequestInput setRequestMethod(RequestMethods method) {
        this.requestMethod = method;
        return this;
    }

    public RequestInput setCookieManager(CookieManager manager) {
        this.cookieManager = manager;
        return this;
    }

    public RequestInput setCookies(List<HttpCookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public RequestInput setOrigin(Request.RequestResult result) {
        this.origin = result;
        return this;
    }

    public RequestInput setAuth(String auth) {
        this.auth = auth;
        return this;
    }

    public Request build() {
        if(!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", DEFAULT_USER_AGENT);
        }

        Request request = new Request(this);
        request.setOrigin(this.origin);
        return request;
    }
}
