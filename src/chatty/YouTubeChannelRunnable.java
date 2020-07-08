package chatty;

import chatty.util.api.YouTubeWeb;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatAction;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatMessages;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatResponse;
import chatty.util.api.youtubeObjects.YouTubeLiveStream;
import chatty.util.irc.MsgTags;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class YouTubeChannelRunnable implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(YouTubeChannelRunnable.class.getName());

    boolean stop = false;
    public final YouTubeWeb web;
    public final YouTubeLiveChats handler;

    public String channel_id = null;
    public String video_id = null;
    public String continuation = null;
    public LiveChatResponse lastResponse;
    public HashMap<String, String> messageIds = new HashMap<>();

    public static int OKAY = 0;

    public static int FAILED = 1;

    public static int QUOTA = 2;

    public YouTubeChannelRunnable(YouTubeWeb web, YouTubeLiveChats handler) {
        this.web = web;
        this.handler = handler;
    }

    public int loadInformation(String channel_identifier) {
        if(Helper.isChannelID(channel_identifier)) {
            this.channel_id = channel_identifier;
        } else {
            return FAILED;
        }

        YouTubeLiveStream liveStream = null;
        try {
            liveStream = web.getCurrentLiveStream(this.channel_id);
        } catch (IOException e) {
            e.printStackTrace();
            return FAILED;
        }
        if(liveStream == null) {
            return FAILED;
        }
        video_id = liveStream.getVideoDetails().video_id;
        lastResponse = web.getLiveChatPage(liveStream.getContinuation().getTimedContinuationData().getContinuation());
        continuation = lastResponse.getLiveChatContinuation().getTimedContinuationData().getContinuation();
        return OKAY;
    }

    public void handleAction(LiveChatAction action) {
        Map<String, String> tags_map = new HashMap<>();
        if(action.getId() != null) {
            tags_map.put("id", action.getId());
        }
        MsgTags tags = new MsgTags(tags_map);
        if(action.getType().equalsIgnoreCase("liveChatTextMessageRenderer")) {
            handler.onChannelMessage(channel_id, action.getAuthorDetails().getChannelId(), action.getAuthorDetails().getName(), action.getMessage().toString(), tags, false);
            messageIds.put(action.getId(), action.getAuthorDetails().getChannelId());
        }
        if (action.getType().equalsIgnoreCase("liveChatViewerEngagementMessageRenderer")) {
            handler.onNotice(channel_id, action.getMessage().toString(), tags);
        }
        if(action.getType().equalsIgnoreCase("markChatItemAsDeletedAction")) {
            tags_map.put("target-msg-id", action.getTargetMessageId());
            tags_map.put("login", messageIds.get(action.getTargetMessageId()));
            handler.onClearMsg(tags, channel_id, "haha deleted");
        }
    }


    @Override
    public void run() {
        while(!stop) {
            for(LiveChatAction action : lastResponse.getLiveChatContinuation().getActions()) {
                handleAction(action);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(lastResponse.getLiveChatContinuation().getTimedContinuationData().getPollingIntervalMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            lastResponse = new LiveChatMessages().setContinuation(continuation).build().execute();
            continuation = lastResponse.getLiveChatContinuation().getTimedContinuationData().getContinuation();
        }
    }

    public void stop() {
        stop = true;
    }

}
