package chatty.util.api;

import chatty.Helper;
import chatty.util.JSONUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.List;
import java.util.Map;

public class RequestResponse {
    private final int status_code;
    private final Map<String, List<String>> headers;
    private final String response;
    private final boolean failed;

    public RequestResponse(int status_code, String response, Map<String, List<String>> headerFields) {
        this.response = response;
        this.status_code = status_code;
        this.headers = headerFields;
        this.failed = false;
    }

    public RequestResponse() {
        this.response = null;
        this.status_code = 0;
        this.headers = null;
        this.failed = true;
    }

    public int getStatusCode() {
        return this.status_code;
    }

    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    public String getContentType() {
        return this.headers.get("Content-Type").get(0);
    }

    public String getString() {
        return this.response;
    }

    public JSONObject toJson() {
        if(!getContentType().contains("application/json")) {
            return null;
        }
        return (JSONObject) JSONUtil.parseJSON(this.response);
    }

    public boolean isFailed() {
        return this.failed;
    }

}
