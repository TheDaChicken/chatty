package chatty.util.api.youtubeObjects.Pages;

import chatty.Helper;
import chatty.util.JSONUtil;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatContinuation;
import chatty.util.api.youtubeObjects.YouTubePlayerConfig;
import org.json.simple.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoPage extends BasicPage {
    private final YouTubePlayerConfig player_config;
    private final LiveChatContinuation continuation;
    private final JSONObject intertube_context;
    private final String delegatedSessionId;

    public VideoPage(YouTubePlayerConfig player_config, LiveChatContinuation continuation, String delegatedSessionId, JSONObject intertube_context) {
        this.player_config = player_config;
        this.continuation = continuation;
        this.intertube_context = intertube_context;
        this.delegatedSessionId = delegatedSessionId;
    }

    public LiveChatContinuation getContinuation() { return this.continuation; }

    public YouTubePlayerConfig getPlayerConfig() { return this.player_config; }

    public JSONObject getIntertubeContext() { return this.intertube_context; }

    public String getDelegatedSessionId() { return this.delegatedSessionId; }

    private static final Pattern INNERTUBE_CONTEXT = Pattern.compile("\"INNERTUBE_CONTEXT\"\\s*:\\s*(.+?)}\\);");

    public static VideoPage parse(String website_string) {
        JSONObject yt_player_config = Helper.parse_yt_player_config(website_string);
        JSONObject init_data = Helper.parse_yt_init_data(website_string);
        if(yt_player_config != null && init_data != null) {
            JSONObject contents = (JSONObject) init_data.get("contents");
            JSONObject twoColumnWatchNextResults = (JSONObject) contents.get("twoColumnWatchNextResults");
            JSONObject conversationBar = (JSONObject) twoColumnWatchNextResults.get("conversationBar");
            JSONObject liveChatRenderer = (JSONObject) conversationBar.get("liveChatRenderer");
            // Get Session/User ID
            JSONObject responseContext = (JSONObject) init_data.get("responseContext");
            JSONObject extension_data = (JSONObject) responseContext.get("webResponseContextExtensionData");
            JSONObject ytConfigData = (JSONObject) extension_data.get("ytConfigData");
            String session_id = (String) ytConfigData.get("delegatedSessionId");
            Matcher matcher = INNERTUBE_CONTEXT.matcher(website_string);
            if(matcher.find()) {
                String group = matcher.group(1);
                JSONObject jsonObject = (JSONObject) JSONUtil.parseJSON(group.replace("\\/", "/"));
                return new VideoPage(new YouTubePlayerConfig(yt_player_config), new LiveChatContinuation(liveChatRenderer), session_id, jsonObject);
            }
            return null;
        }
        return null;
    }


}
