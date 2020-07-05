
package chatty.util.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Handles TwitchApi requests and responses.
 * 
 * @author tduva
 */
public class YouTubeApi {

    private final static Logger LOGGER = Logger.getLogger(YouTubeApi.class.getName());

    /**
     * How long the Emoticons can be cached in a file after they are updated
     * from the API.
     */
    public static final int CACHED_EMOTICONS_EXPIRE_AFTER = 60*60*24;
    
    public static final int TOKEN_CHECK_DELAY = 600;

    public enum RequestResultCode {
        ACCESS_DENIED, SUCCESS, FAILED, NOT_FOUND, RUNNING_COMMERCIAL,
        INVALID_CHANNEL, INVALID_STREAM_STATUS, UNKNOWN
    }

    private final TwitchApiResultListener resultListener;
    
    private volatile Long tokenLastChecked = Long.valueOf(0);

    protected volatile YouTube youtubeService;

    public YouTubeApi(TwitchApiResultListener apiResultListener) {
        this.resultListener = apiResultListener;
    }

    public void setGoogleCredential(GoogleCredential credential) {
        this.youtubeService = YouTubeAuth.createService(credential);
    }

    public void verifyToken(GoogleCredential credential) {
        try {
            YouTube service = YouTubeAuth.createService(credential);
            YouTube.Channels.List request = service.channels()
                    .list("snippet");
            ChannelListResponse response = request.setMine(true).execute();
            Channel channel = response.getItems().get(0);
            String channel_id = channel.getId();
            String username = channel.getSnippet().getTitle();
            Collection<String> scope = new ArrayList<>();
            scope.add(TokenInfo.Scope.FULL_SCOPE.scope);
            TokenInfo info = new TokenInfo(username, channel_id, scope);
            this.resultListener.tokenVerified(credential, info);
        } catch (IOException e) {
            this.resultListener.tokenVerified(credential, null);
        }
    }

    public LiveChatMessageListResponse getChatMessages(final String liveChatId, final String nextPageToken) {
        // Get chat messages from YouTube
        LiveChatMessageListResponse response = null;
        try {
            response = youtubeService
                    .liveChatMessages()
                    .list(liveChatId, "snippet, authorDetails")
                    .setPageToken(nextPageToken)
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    public String getLiveId(String videoId) throws IOException {
        // Get liveChatId from the video
        VideoListResponse response = youtubeService.videos()
                .list("liveStreamingDetails")
                .setId(videoId).execute();;
        for (Video v : response.getItems()) {
            String liveChatId = v.getLiveStreamingDetails().getActiveLiveChatId();
            if (liveChatId != null && !liveChatId.isEmpty()) {
                return liveChatId;
            }
        }
        return null;
    }


}