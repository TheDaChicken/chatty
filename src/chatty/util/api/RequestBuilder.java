package chatty.util.api;

import java.util.HashMap;

/*
    Builder for Requesting stuff from the Internet.
 */
public class RequestBuilder {

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36";

    public String REQUEST_METHOD = "GET";
    public String URL;
    public HashMap<String, String> HEADERS = new HashMap<>();

    public RequestBuilder(String url) {
        this.URL = url;
    }

    public RequestBuilder setRequestMethod(String method) {
        this.REQUEST_METHOD = method;
        return this;
    }

    public RequestBuilder setUrl(String url) {
        this.URL = url;
        return this;
    }

    public RequestBuilder setHeaders(HashMap<String, String> headers) {
        this.HEADERS = headers;
        return this;
    }

    public Request build() {
        if(!this.HEADERS.containsKey("User-Agent")) {
            this.HEADERS.put("User-Agent", DEFAULT_USER_AGENT);
        }


        return new Request(this.REQUEST_METHOD, this.URL, this.HEADERS);
    }


}
