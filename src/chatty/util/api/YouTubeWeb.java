package chatty.util.api;

import chatty.Cookies;
import chatty.Helper;
import chatty.exceptions.PrivateStream;
import chatty.exceptions.RegisterException;
import chatty.exceptions.YouTube404;
import chatty.util.JSONUtil;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatPage;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatResponse;
import chatty.util.api.youtubeObjects.YouTubeLiveStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeWeb {

    private final static Logger LOGGER = Logger.getLogger(YouTubeWeb.class.getName());

    private static final Pattern YOUTUBE_PLAYER_CONFIG = Pattern.compile(";ytplayer\\.config\\s*=\\s*(\\{.+?\\});");
    private static final Pattern YOUTUBE_INIT_DATA = Pattern.compile("window\\[\"ytInitialData\"]\\s*=\\s*(\\{.+?\\});");
    private static final Pattern YOUTUBE_INIT_GUIDED_DATA = Pattern.compile("var ytInitialGuideData\\s*=\\s*(\\{.+?\\});");
    private static final Pattern END_POINT_TYPE = Pattern.compile("var data\\s=\\s\\{\\s[^>]*page: \\\\\"(.+?)\\\\\",");

    public final YouTubeApiResultListener resultListener;
    public List<HttpCookie> cookies = new ArrayList<>();
    private final ExecutorService executor;

    public YouTubeWeb(YouTubeApiResultListener apiResultListener) {
        this.resultListener = apiResultListener;
        this.executor = Executors.newCachedThreadPool();
    }

    public YouTubeLiveStream getCurrentLiveStream(String channel_id) throws YouTube404, RegisterException, PrivateStream {
        RequestResponse response = new RequestBuilder(
                "https://www.youtube.com/channel/" + channel_id + "/live").setCookies(cookies).build().execute();
        if(response.status_code == 404) {
            throw(new YouTube404(channel_id));
        } else if(response.status_code != 200) {
            throw(new RegisterException("YouTube returned a status code that isn't 200 when getting live stream."));
        }
        String website_string = response.getResponse();
        String endpointType = YouTubeWeb.getEndpointType(website_string);
        if(endpointType != null && endpointType.equalsIgnoreCase("browse")) {
            throw(new PrivateStream(channel_id));
        }
        return YouTubeLiveStream.parse(website_string);
    }

    public LiveChatResponse getLiveChatPage(String continuation) {
        RequestResponse response = new RequestBuilder(
                "https://www.youtube.com/live_chat?continuation=" + continuation).setCookies(cookies).build().execute();
        return LiveChatPage.parse(continuation, response.getResponse());
    }

    public void setCookies(List<HttpCookie> cookies) {
        this.cookies = cookies;
    }

    /**
     * Easily checks if not logged in using account_advanced status code.
     * @param cookies: JSON Cookies
     * @param parsed_cookies: Cookies already parsed.
     */

    public void verifyCookies(String cookies, List<HttpCookie> parsed_cookies) {
        Request request = new RequestBuilder("https://www.youtube.com").setCookies(parsed_cookies).setRequestResult(response -> {
            TokenInfo tokenInfo = new TokenInfo();
            String channel_id = null;
            String s = response.getResponse();

            JSONObject root = getInitGuidedData(s);
            if(root == null) {
                resultListener.cookiesVerified(cookies, tokenInfo);
                return;
            }
            JSONObject responseContext = (JSONObject) root.get("responseContext");
            JSONArray serviceTrackingParams = (JSONArray) responseContext.get("serviceTrackingParams");

            for(Object o : serviceTrackingParams) {
                JSONObject obj = (JSONObject) o;
                String service_name = (String) obj.get("service");
                if(service_name.equalsIgnoreCase("GUIDED_HELP")) {
                    JSONArray params = (JSONArray) obj.get("params");
                    for(Object param_obj : params) {
                        JSONObject param = (JSONObject) param_obj;
                        String key = (String) param.get("key");
                        String value = (String) param.get("value");
                        if(key.equalsIgnoreCase("creator_channel_id")) {
                            channel_id = value;
                        }
                        if(key.equalsIgnoreCase("logged_in") && value.equalsIgnoreCase("0")) {
                            resultListener.cookiesVerified(cookies, tokenInfo);
                            return;
                        }
                    }
                }
            }
            tokenInfo = new TokenInfo(null, channel_id);
            resultListener.cookiesVerified(cookies, tokenInfo);
        }).build();
        executor.execute(request);
    }

    public static JSONObject getInitData(String website_code) {
        Matcher matcher = YouTubeWeb.YOUTUBE_INIT_DATA.matcher(website_code);
        if(matcher.find()) {
            return JSONUtil.parseJSON(matcher.group(1));
        }
        return null;
    }

    private static JSONObject getInitGuidedData(String website_code) {
        Matcher matcher = YouTubeWeb.YOUTUBE_INIT_GUIDED_DATA.matcher(website_code);
        if(matcher.find()) {
            return JSONUtil.parseJSON(matcher.group(1));
        }
        return null;
    }


    public static JSONObject getPlayerConfig(String website_code) {
        Matcher matcher = YouTubeWeb.YOUTUBE_PLAYER_CONFIG.matcher(website_code);
        if(matcher.find()) {
            return JSONUtil.parseJSON(matcher.group(1));
        }
        return null;
    }

    public static String getEndpointType(String website_code) {
        Matcher matcher = YouTubeWeb.END_POINT_TYPE.matcher(website_code);
        if(matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public LiveChatResponse getLiveChat(String continuation) {
        RequestResponse response = new RequestBuilder("https://www.youtube.com/live_chat/get_live_chat?commandMetadata=%5Bobject%20Object%5D&continuation=" +
                continuation + "&hidden=false&pbj=1").setCookies(cookies).build().execute();
        JSONObject obj = JSONUtil.parseJSON(response.getResponse());
        JSONObject response_ = (JSONObject) obj.get("response");
        return new LiveChatResponse(response_);
    }


}
