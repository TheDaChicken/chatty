package chatty.util.api;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class Request implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(Request.class.getName());

    private RequestResult origin;
    private RequestInput input;

    public Request(RequestInput input) {
        this.input = input;
    }

    public void setOrigin(RequestResult result) {
        this.origin = result;
    }

    public RequestResponse sendRequest() {
        if (input.getAuth() != null) {
            LOGGER.info(input.getRequestMethod().toString() + ": " + input.getUrl() + " "
                    + "(using authorization)");
        } else {
            LOGGER.info(input.getRequestMethod().toString() + ": " + input.getUrl());
        }

        URL url;
        HttpURLConnection connection = null;

        try {
            url = new URL(input.getUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(60000);
            connection.setUseCaches(true);
            connection.setRequestMethod(input.getRequestMethod().toString());
            connection.setRequestProperty("Accept-Encoding", "gzip");

            if(input.getCookieManager() != null) {
                if (input.getCookieManager().getCookieStore().getCookies().size() > 0) {
                    String cookies = input.getCookieManager().getCookieStore().getCookies().stream()
                            .map(HttpCookie::toString)
                            .collect(Collectors.joining("; "));
                    connection.setRequestProperty("Cookie", cookies);
                }
            }
            if(input.getCookies() != null) {
                String cookies = input.getCookies().stream()
                        .map(HttpCookie::toString)
                        .collect(Collectors.joining("; "));
                connection.setRequestProperty("Cookie", cookies);
            }

            if(input.getAuth() != null) {
                connection.setRequestProperty("Authorization", input.getAuth());
            }

            for(Map.Entry<String, String> entry : input.getHeaders().entrySet())
                connection.setRequestProperty(entry.getKey(), entry.getValue());

            if(input.getRequestData() != null) {
                if(input.getContentType() == null) {
                    input.setContentType("application/x-www-form-urlencoded");
                    LOGGER.info("Content Type EMPTY. Setting Content Type to application/x-www-form-urlencoded");
                }
                connection.setRequestProperty("Content-Type", input.getContentType());
                connection.setDoOutput(true);

                try(OutputStream os = connection.getOutputStream()) {
                    byte[] bytes = input.getRequestData().getBytes(StandardCharsets.UTF_8);
                    os.write(bytes, 0, bytes.length);
                }
                //LOGGER.info("Sending data: "+ input.getRequestData());
            }

            InputStream stream;
            if(connection.getResponseCode() < 400) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
            }
            if ("gzip".equals(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }

            StringBuilder response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Handle Cookies
            if(input.getCookieManager() != null) {
                List<String> cookiesHeader = connection.getHeaderFields().get("Set-Cookie");
                if (cookiesHeader != null) {
                    for (String cookie_header : cookiesHeader) {
                        HttpCookie cookie = HttpCookie.parse(cookie_header).get(0);
                        if(cookie.getMaxAge() == 0) { // YouTube removing cookies because cookies are bad.
                            input.getCookieManager().getCookieStore().remove(null, cookie);
                        } else {
                            input.getCookieManager().getCookieStore().add(null, cookie);
                        }
                    }
                }
            }



            return new RequestResponse(connection.getResponseCode(), response.toString(), connection.getHeaderFields());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return new RequestResponse();
    }

    @Override
    public void run() {
        if (origin == null) {
            return;
        }
        RequestResponse result = sendRequest();
        origin.requestResult(result);
    }

    public interface RequestResult {

        void requestResult(RequestResponse response);

    }
}
