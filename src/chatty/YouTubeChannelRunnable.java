package chatty;

import chatty.exceptions.PrivateStream;
import chatty.exceptions.RegisterException;
import chatty.exceptions.YouTube404;
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
        try {
            YouTubeLiveStream liveStream = web.getCurrentLiveStream(this.channel_id);
            if(liveStream == null) { // can be null after passing checks for 404 etc
                return FAILED;
            }
            video_id = liveStream.getVideoDetails().video_id;
            lastResponse = web.getLiveChatPage(liveStream.getContinuation().getTimedContinuationData().getContinuation());
            continuation = lastResponse.getLiveChatContinuation().getTimedContinuationData().getContinuation();
            return OKAY;
        } catch (PrivateStream | RegisterException | YouTube404 privateStream) {
            return FAILED;
        }
    }

    public void handleAction(LiveChatAction action) {
        Map<String, String> tags_map = new HashMap<>();
        if(action.getId() != null) {
            tags_map.put("id", action.getId());
        }
        MsgTags tags = new MsgTags(tags_map);
        switch(action.getType()) {
            case "liveChatTextMessageRenderer": {
                // Parse Badges from JSON
                tags_map.put("badges", action.getAuthorDetails().getBadges().toJSONString());
                handler.onChannelMessage(channel_id, action.getAuthorDetails().getChannelId(), action.getAuthorDetails().getName(), action.getMessage().toString(), tags, false);
                messageIds.put(action.getId(), action.getAuthorDetails().getChannelId());
                break;
            }
            case "liveChatViewerEngagementMessageRenderer": {
                handler.onNotice(channel_id, action.getMessage().toString(), tags);
                break;
            }
            case "markChatItemAsDeletedAction": {
                tags_map.put("target-msg-id", action.getTargetMessageId());
                tags_map.put("login", messageIds.get(action.getTargetMessageId()));
                handler.onClearMsg(tags, channel_id, "haha deleted");
                break;
            }
        }
    }

    @Override
    public void run() {
        //LOGGER.info(lastResponse.getLiveChatContinuation().getViewerName());
        //lastResponse.getLiveChatContinuation().getPanel();

        while(!stop) {
            for(LiveChatAction action : lastResponse.getLiveChatContinuation().getActions()) {
                handleAction(action);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(lastResponse.getLiveChatContinuation().getTimedContinuationData().getPollingIntervalMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            lastResponse = web.getLiveChat(continuation);
            continuation = lastResponse.getLiveChatContinuation().getTimedContinuationData().getContinuation();
        }
    }

    public void stop() {
        stop = true;
    }

}
