package chatty.util.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Request implements Runnable {
    /*
        Request something
     */

    /**
     * Timeout for connecting in milliseconds.
     */
    private static final int CONNECT_TIMEOUT = 30*1000;
    /**
     * Timeout for reading from the connection in milliseconds.
     */
    private static final int READ_TIMEOUT = 60*1000;

    public String REQUEST_METHOD = "GET";
    public String URL;
    public HashMap<String, String> HEADERS;
    public List<HttpCookie> COOKIES;
    public RequestResult REQUEST_RESULT;

    public Request(String REQUEST_METHOD, String URL, HashMap<String, String> HEADERS, List<HttpCookie> COOKIES, RequestResult REQUEST_RESULT) {
        this.REQUEST_METHOD = REQUEST_METHOD;
        this.URL = URL;
        this.HEADERS = HEADERS;
        this.COOKIES = COOKIES;
        this.REQUEST_RESULT = REQUEST_RESULT;
    }

    public String getCookieHeaderValue() {
        StringBuilder string = new StringBuilder("");
        for (HttpCookie cookie : this.COOKIES) {
            string.append(cookie.getName() + "=" + cookie.getValue() + ";");
        }
        return string.toString();
    }

    public RequestResponse execute() {
        URL url;
        HttpURLConnection connection = null;

        try {
            url = new URL(this.URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod(REQUEST_METHOD);
            connection.setRequestProperty("cookie", getCookieHeaderValue());

            connection.setRequestProperty("Accept-Encoding", "gzip");

            for(Map.Entry<String, String> entrySet : this.HEADERS.entrySet()) {
                connection.setRequestProperty(entrySet.getKey(), entrySet.getValue());
            }

            InputStream stream = connection.getErrorStream();
            if (stream == null) {
                stream = connection.getInputStream();
            }
            if ("gzip".equals(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            return new RequestResponse(connection.getResponseCode(), stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void run() {
        RequestResponse response = execute();
        if(REQUEST_RESULT != null) {
            REQUEST_RESULT.requestResult(response);
        }
    }

    public interface RequestResult {
        void requestResult(RequestResponse response);
    }
}
