package chatty.util.api;

import chatty.gui.components.settings.GenericComboSetting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Request {
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

    public Request(String REQUEST_METHOD, String URL, HashMap<String, String> HEADERS) {
        this.REQUEST_METHOD = REQUEST_METHOD;
        this.URL = URL;
        this.HEADERS = HEADERS;
    }

    public String execute() {
        Charset charset = StandardCharsets.UTF_8;
        java.net.URL url;
        HttpURLConnection connection = null;

        try {
            url = new URL(this.URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod(REQUEST_METHOD);


            connection.setRequestProperty("Accept-Encoding", "gzip");

            for(Map.Entry<String, String> entrySet : this.HEADERS.entrySet()) {
                connection.setRequestProperty(entrySet.getKey(), entrySet.getValue());
            }

            // Read response
            InputStream input = connection.getInputStream();
            if ("gzip".equals(connection.getContentEncoding())) {
                input = new GZIPInputStream(input);
            }

            StringBuilder response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset))) {
                String line;
                response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return response.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
