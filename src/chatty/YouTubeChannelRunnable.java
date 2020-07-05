package chatty;

import chatty.util.api.YouTubeApi;
import chatty.util.api.YouTubeWebParser;
import chatty.util.irc.MsgTags;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.model.LiveChatMessage;
import com.google.api.services.youtube.model.LiveChatMessageListResponse;
import com.google.api.services.youtube.model.LiveChatMessageSnippet;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class YouTubeChannelRunnable implements Runnable {
    boolean stop = false;
    public final YouTubeApi api;
    public final YouTubeLiveChat handler;

    public String channel_id = null;
    public String video_id = null;
    public String liveId = null;
    public String nextPageToken = null;

    public static int OKAY = 0;

    public static int FAILED = 1;

    public static int QUOTA = 2;


    public YouTubeChannelRunnable(YouTubeLiveChat handler, YouTubeApi api) {
        this.api = api;
        this.handler = handler;
    }

    public int loadInformation(String channel_identifier) {
        if(Helper.isChannelID(channel_identifier)) {
            this.channel_id = channel_identifier;
            video_id = YouTubeWebParser.grabCurrentVideoID(this.channel_id);
            if(video_id.isEmpty()) {
                return 1;
            }
        } else {
            return 1;
        }
        try {
            liveId = api.getLiveId(video_id);
            if (liveId == null) {
                return 1;
            }
            return 0;
        } catch (GoogleJsonResponseException e) {
            if(e.getDetails().getErrors().get(0).getReason().equalsIgnoreCase("quotaExceeded")) {
                return QUOTA;
            }
            return 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    @Override
    public void run() {
        while(!stop) {
            try {
                LiveChatMessageListResponse response = api.getChatMessages(liveId, nextPageToken);
                nextPageToken = response.getNextPageToken();

                List<LiveChatMessage> messages = response.getItems();
                for (int i = 0; i < messages.size(); i++) {
                    LiveChatMessage message = messages.get(i);
                    LiveChatMessageSnippet snippet = message.getSnippet();

                    String user_username = message.getAuthorDetails().getDisplayName();
                    String user_channel_id = message.getAuthorDetails().getChannelId();

                    Map<String, String> tags_map = new HashMap<>();
                    tags_map.put("id", message.getId());
                    MsgTags tags = new MsgTags(tags_map);
                    handler.onChannelMessage(channel_id, user_channel_id, user_username, snippet.getDisplayMessage(), tags, false);
                }

                TimeUnit.MILLISECONDS.sleep(response.getPollingIntervalMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void stop() {
        stop = true;
    }



}
