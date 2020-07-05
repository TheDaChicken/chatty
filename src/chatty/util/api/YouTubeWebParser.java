package chatty.util.api;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class YouTubeWebParser {
    /**
     * Timeout for connecting in milliseconds.
     */
    private static final int CONNECT_TIMEOUT = 30*1000;
    /**
     * Timeout for reading from the connection in milliseconds.
     */
    private static final int READ_TIMEOUT = 60*1000;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36";

    private static final Pattern YOUTUBE_PLAYER_CONFIG = Pattern.compile(";ytplayer\\.config\\s*=\\s*(\\{.+?\\});");

    private static String getURL(String targetUrl) {
        Charset charset = Charset.forName("UTF-8");
        URL url;
        HttpURLConnection connection = null;

        try {
            url = new URL(targetUrl);
            connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestMethod("GET");

            // Read response
            InputStream input = connection.getInputStream();
            if ("gzip".equals(connection.getContentEncoding())) {
                input = new GZIPInputStream(input);
            }

            StringBuilder response;
            try (BufferedReader reader
                         = new BufferedReader(new InputStreamReader(input, charset))) {
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


    public static String grabCurrentVideoID(String channel_id) {
        String live_page = getURL("https://www.youtube.com/channel/" + channel_id + "/live");
        JSONObject player_config = getPlayerConfig(live_page);
        if(player_config == null) {
            return "";
        }
        JSONObject args = (JSONObject)player_config.get("args");
        String player_responseString = (String) args.get("player_response");
        JSONObject player_response = parseJSON(player_responseString);
        JSONObject videoDetails = (JSONObject) player_response.get("videoDetails");
        return (String) videoDetails.get("videoId");
    }


    protected static JSONObject getPlayerConfig(String website_code) {
        Matcher matcher = YOUTUBE_PLAYER_CONFIG.matcher(website_code);
        if(matcher.find()) {
            return parseJSON(matcher.group(1));
        }
        return null;
    }

    protected static JSONObject parseJSON(String json) {
        JSONParser parser = new JSONParser();
        JSONObject root = null;
        try {
            root = (JSONObject)parser.parse(json);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return root;
    }

}
