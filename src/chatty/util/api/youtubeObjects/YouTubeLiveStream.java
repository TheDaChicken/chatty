package chatty.util.api.youtubeObjects;

import chatty.YouTubeClient;
import chatty.util.JSONUtil;
import chatty.util.api.YouTubeWeb;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatContinuation;
import org.json.simple.JSONObject;

import java.util.logging.Logger;

public class YouTubeLiveStream {

    private final LiveChatContinuation continuation;
    private final VideoDetails videoDetails;
    private static final Logger LOGGER = Logger.getLogger(YouTubeClient.class.getName());

    public YouTubeLiveStream(VideoDetails videoDetails, LiveChatContinuation continuation) {
        this.videoDetails = videoDetails;
        this.continuation = continuation;
    }

    public static YouTubeLiveStream parse(String website_string) {
        JSONObject player_config = YouTubeWeb.getPlayerConfig(website_string);
        if(player_config == null) {
            return null;
        }
        JSONObject args = (JSONObject)player_config.get("args");
        String player_responseString = (String) args.get("player_response");
        JSONObject player_response = JSONUtil.parseJSON(player_responseString);
        if(player_response == null) {
            return null;
        }
        JSONObject videoDetails = (JSONObject) player_response.get("videoDetails");
        VideoDetails videoDetailsObj = new VideoDetails((String) videoDetails.get("videoId"), (String) videoDetails.get("title"), (String) videoDetails.get("channelId"));
        JSONObject init_data = YouTubeWeb.getInitData(website_string);
        if(init_data == null) {
            return null;
        }
        JSONObject contents = (JSONObject) init_data.get("contents");
        JSONObject twoColumnWatchNextResults = (JSONObject) contents.get("twoColumnWatchNextResults");
        JSONObject conversationBar = (JSONObject) twoColumnWatchNextResults.get("conversationBar");
        JSONObject liveChatRenderer = (JSONObject) conversationBar.get("liveChatRenderer");
        return new YouTubeLiveStream(videoDetailsObj, new LiveChatContinuation(liveChatRenderer));
    }

    public VideoDetails getVideoDetails() {
        return this.videoDetails;
    }

    public LiveChatContinuation getContinuation() { return this.continuation; }

}
