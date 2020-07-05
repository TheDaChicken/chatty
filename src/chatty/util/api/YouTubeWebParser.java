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
    private static final Pattern YOUTUBE_PLAYER_CONFIG = Pattern.compile(";ytplayer\\.config\\s*=\\s*(\\{.+?\\});");


    public static String grabCurrentVideoID(String channel_id) {
        String live_page = new RequestBuilder(
                "https://www.youtube.com/channel/" + channel_id + "/live").build().execute();
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
