package chatty.util.api;

import chatty.Cookies;
import chatty.Helper;
import chatty.exceptions.InternetOffline;
import chatty.exceptions.YouTube404;
import chatty.util.JSONUtil;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatContinuation;
import chatty.util.api.youtubeObjects.Pages.LiveChatPage;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatResponse;
import chatty.util.api.youtubeObjects.Pages.BasicPage;
import chatty.util.api.youtubeObjects.Pages.PrivateStream;
import chatty.util.api.youtubeObjects.Pages.VideoPage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeWeb {

    /*
        Contains YOUTUBEI API and other things.
     */

    private final static Logger LOGGER = Logger.getLogger(YouTubeWeb.class.getName());

    private static final Pattern YOUTUBE_INIT_GUIDED_DATA = Pattern.compile("var ytInitialGuideData\\s*=\\s*(\\{.+?\\});");
    private static final Pattern END_POINT_TYPE = Pattern.compile("\\{\\s[^>]*page: \"(.+?)\",");

    private final CookieManager cookieManager = new CookieManager();
    private boolean emptyYouTubeCookies = true;

    public final YouTubeApiResultListener resultListener;
    private final ExecutorService executor;

    public YouTubeWeb(YouTubeApiResultListener apiResultListener) {
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.resultListener = apiResultListener;
        this.executor = Executors.newCachedThreadPool();
    }

    public void setCookies(String cookies_json) {
        if(cookies_json.isEmpty()) {
            emptyYouTubeCookies = true;
            this.cookieManager.getCookieStore().removeAll();
        } else {
            emptyYouTubeCookies = false;
            List<HttpCookie> cookies = Cookies.loadCookies(cookies_json);
            for(HttpCookie cookie : cookies) {
                this.cookieManager.getCookieStore().add(null, cookie);
            }
        }
    }

    private static class VerifyHolder {
        private String channel_id = null;
        private boolean logged_in = false;
        private String json_cookies = null;
    }

    private VerifyHolder parseResponseContext(JSONObject root) {
        VerifyHolder holder = new VerifyHolder();
        if(root != null) {
            JSONObject responseContext = (JSONObject) root.get("responseContext");
            JSONArray serviceTrackingParams = (JSONArray) responseContext.get("serviceTrackingParams");
            for (Object serviceTrackingParam : serviceTrackingParams) {
                JSONObject service = (JSONObject) serviceTrackingParam;
                String service_name = (String) service.get("service");
                if (service_name.equalsIgnoreCase("GUIDED_HELP")) {
                    JSONArray params = (JSONArray) service.get("params");
                    for (Object param_obj : params) {
                        JSONObject param = (JSONObject) param_obj;
                        String key = (String) param.get("key");
                        String value = (String) param.get("value");
                        if (key.equalsIgnoreCase("creator_channel_id")) {
                            holder.channel_id = value;
                        }
                        if (key.equalsIgnoreCase("logged_in") && value.equalsIgnoreCase("1")) {
                            holder.logged_in = true;
                        }
                    }
                }
            }
        }
        return holder;
    }

    public void verifyCookies(String json_cookies, List<HttpCookie> parsed_cookies) {
        Request request = new RequestInput("https://www.youtube.com").setCookies(parsed_cookies).setOrigin(response -> {
            String website_string = response.getString();
            JSONObject root = getInitGuidedData(website_string);
            VerifyHolder holder = parseResponseContext(root);
            holder.json_cookies = json_cookies;

            TokenInfo tokenInfo = new TokenInfo();
            if(holder.logged_in && holder.channel_id != null) {
                tokenInfo = new TokenInfo(null, holder.channel_id);
            }
            resultListener.cookiesVerified(holder.json_cookies, tokenInfo);
        }).build();
        executor.execute(request);
    }

    public void checkToken() {
        if(!emptyYouTubeCookies) {
            TokenInfo tokenInfo = new TokenInfo();
            resultListener.cookiesVerified(null, tokenInfo);
        }
    }

    public BasicPage getLatestChannelLiveStream(String channel_id) throws YouTube404, InternetOffline {
        Request request = new RequestInput(
                "https://www.youtube.com/channel/" + channel_id + "/live").setCookieManager(cookieManager).build();
        RequestResponse response = request.sendRequest();
        if(response.isFailed()) {
            throw(new InternetOffline());
        }
        if(response.getStatusCode() == 404) {
            throw (new YouTube404(channel_id));
        }
        String website_string = response.getString();
        String endpointType = getEndpointType(website_string);
        if(endpointType != null && endpointType.equalsIgnoreCase("browse")) {
            return new PrivateStream();
        }
        return VideoPage.parse(website_string);
    }

    public LiveChatPage getLiveChatPage(String continuation) {
        Request request = new RequestInput(
                "https://www.youtube.com/live_chat?continuation=" + continuation).setCookieManager(cookieManager).build();
        RequestResponse response = request.sendRequest();
        return LiveChatPage.parse(response.getString());
    }

    private HttpCookie getCookieFromName(String name) {
        Optional<HttpCookie> cookie = cookieManager.getCookieStore().getCookies().stream().filter(httpCookie -> httpCookie.getName().equalsIgnoreCase(name)).findFirst();
        return cookie.orElse(null);
    }

    private Map<String, Object> createContext(String user_id, JSONObject inneryoutube_context) {
        Map<String, Object> client = new HashMap<String, Object>((JSONObject) inneryoutube_context.get("client")) {{
            put("connectionType", "CONN_CELLULAR_4G");
            put("gl", "US"); // Sorry have to force English or modlogs won't parse correctly
            put("hl", "en");
            put("screenHeightPoints", 689);
            put("screenPixelDensity", 1);
            put("screenWidthPoints", 400);
            put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK");
            put("utcOffsetMinutes", -420);
        }};
        Map<String, Object> user = new HashMap<String, Object>() {{
            put("onBehalfOfUser", user_id);
        }};
        Map<String, Object> request = new HashMap<String, Object>((JSONObject) inneryoutube_context.get("request")) {{
            put("consistencyTokenJars", new ArrayList<>());
            put("internalExperimentFlags", new ArrayList<>());
        }};
        Map<String, Object> context = new HashMap<String, Object>() {{
            put("client", client);
            put("user", user);
            put("request", request);
        }};
        return context;
    }

    public LiveChatResponse fetchNewChats(String continuation, String user_id, JSONObject inneryoutube_context) throws InternetOffline {
        JSONObject post_json = new JSONObject() {{
             put("context", createContext(user_id, inneryoutube_context));
             put("hidden", true);
        }};

        String origin = "https://www.youtube.com";
        String auth = null;
        HttpCookie papisid = getCookieFromName("__Secure-3PAPISID");
        if(papisid != null) {
            String auth_hash = Helper.create_authorization_hash(
                    papisid.getValue(), origin);
            auth = "SAPISIDHASH " + auth_hash;
        }

        HashMap<String, String> headers = new HashMap<String, String>() {{
            put("Accept", "application/json");
            put("X-Origin", origin);
            put("Origin", origin);
            put("Referer", "https://www.youtube.com/live_chat");
        }};

        if (((JSONObject) inneryoutube_context.get("client")).get("visitorData") != null) {
            headers.put("X-Goog-AuthUser", "0");
            headers.put("X-Goog-Visitor-Id", (String) ((JSONObject) inneryoutube_context.get("client")).get("visitorData"));
        }

        Request request_ = new RequestInput("https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?" +
                "commandMetadata=%5Bobject%20Object%5D&continuation=" + continuation + "&isInvalidationTimeoutRequest=true&pbj=1&key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8").
                setCookieManager(cookieManager).setRequestMethod(RequestMethods.POST).setData(post_json).putHeaders(headers).
                setAuth(auth).build();
        RequestResponse response = request_.sendRequest();
        if(response.isFailed()) {
            throw(new InternetOffline());
        }
        JSONObject jsonObject = (JSONObject) JSONUtil.parseJSON(response.getString());
        if(jsonObject.get("responseContext") != null) {
            VerifyHolder holder = parseResponseContext(jsonObject);
            if(!holder.logged_in) {
                resultListener.accessDenied();
            }
        }
        return new LiveChatResponse(jsonObject);
    }

    /**
     moderation
     **/
    public void sendModeration(String params, String user_id, JSONObject inneryoutube_context) {
        JSONObject post_json = new JSONObject() {{
            put("context", createContext(user_id, inneryoutube_context));
            put("params", params);
        }};

        String origin = "https://www.youtube.com";
        HttpCookie papisid = getCookieFromName("__Secure-3PAPISID");
        if(papisid == null) {
            return;
        }

        String auth_hash = Helper.create_authorization_hash(
                papisid.getValue(), origin);
        String auth = "SAPISIDHASH " + auth_hash;

        HashMap<String, String> headers = new HashMap<String, String>() {{
            put("Accept", "application/json");
            put("X-Origin", origin);
            put("Origin", origin);
            put("Referer", "https://www.youtube.com/live_chat");
        }};

        if (((JSONObject) inneryoutube_context.get("client")).get("visitorData") != null) {
            headers.put("X-Goog-AuthUser", "0");
            headers.put("X-Goog-Visitor-Id", (String) ((JSONObject) inneryoutube_context.get("client")).get("visitorData"));
        }

        Request web_request = new RequestInput("https://www.youtube.com/youtubei/v1/live_chat/moderate?" +
                "key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8").
                setCookieManager(cookieManager).setRequestMethod(RequestMethods.POST).setData(post_json).putHeaders(headers).
                setAuth(auth).setOrigin(new Request.RequestResult() {
            @Override
            public void requestResult(RequestResponse response) {
                if(response.isFailed()) {
                    return;
                }
                JSONObject jsonObject = (JSONObject) JSONUtil.parseJSON(response.getString());
                JSONObject root = new JSONObject();
            }
        }).build();
        executor.execute(web_request);
    }

    /**
     timeout using a thread
     **/
    public void sendMessage(String client_id, String params, JSONArray text, String user_id, JSONObject inneryoutube_context) {
        JSONObject post_json = new JSONObject() {{
            put("context", createContext(user_id, inneryoutube_context));
            put("params", params);
            put("clientMessageId", client_id);
            put("richMessage", new JSONObject() {{
                put("textSegments", text);
            }});
        }};

        String origin = "https://www.youtube.com";
        HttpCookie papisid = getCookieFromName("__Secure-3PAPISID");
        if(papisid == null) {
            return;
        }

        String auth_hash = Helper.create_authorization_hash(
                papisid.getValue(), origin);
        String auth = "SAPISIDHASH " + auth_hash;

        HashMap<String, String> headers = new HashMap<String, String>() {{
            put("Accept", "application/json");
            put("X-Origin", origin);
            put("Origin", origin);
            put("Referer", "https://www.youtube.com/live_chat");
        }};

        if (((JSONObject) inneryoutube_context.get("client")).get("visitorData") != null) {
            headers.put("X-Goog-AuthUser", "0");
            headers.put("X-Goog-Visitor-Id", (String) ((JSONObject) inneryoutube_context.get("client")).get("visitorData"));
        }

        Request web_request = new RequestInput("https://www.youtube.com/youtubei/v1/live_chat/send_message?" +
                "key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8").
                setCookieManager(cookieManager).setRequestMethod(RequestMethods.POST).setData(post_json).putHeaders(headers).
                setAuth(auth).setOrigin(new Request.RequestResult() {
            @Override
            public void requestResult(RequestResponse response) {
                if(response.isFailed()) {
                    return;
                }
                JSONObject jsonObject = (JSONObject) JSONUtil.parseJSON(response.getString());
                JSONObject root = new JSONObject();
            }
        }).build();
        executor.execute(web_request);
    }

    public static String getEndpointType(String website_code) {
        Matcher matcher = END_POINT_TYPE.matcher(website_code);
        if(matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static JSONObject getInitGuidedData(String website_code) {
        Matcher matcher = YouTubeWeb.YOUTUBE_INIT_GUIDED_DATA.matcher(website_code);
        if(matcher.find()) {
            return (JSONObject) JSONUtil.parseJSON(matcher.group(1));
        }
        return null;
    }


}
