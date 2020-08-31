package chatty;

import chatty.exceptions.InternetOffline;
import chatty.exceptions.UnableJoin;
import chatty.exceptions.YouTube404;
import chatty.util.api.YouTubeWeb;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatContinuation;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatResponse;
import chatty.util.api.youtubeObjects.LiveChat.actions.*;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatModerationMessageRenderer;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatTextMessageRenderer;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatViewerEngagementMessageRenderer;
import chatty.util.api.youtubeObjects.ModerationData;
import chatty.util.api.youtubeObjects.Pages.BasicPage;
import chatty.util.api.youtubeObjects.Pages.PrivateStream;
import chatty.util.api.youtubeObjects.Pages.VideoPage;
import chatty.util.irc.MsgTags;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Base64;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class YouTubeLiveChatRunnable implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(YouTubeLiveChatRunnable.class.getName());
    private final YouTubeWeb web;
    private final YouTubeLiveChats handler;

    private BasicPage originalPage = null;
    private LiveChatResponse lastResponse = null;

    private String owner_id_channel;
    private String channel_name;

    public String viewer_name = null;

    private String client_message_id = null;
    private int message_count = 0;

    private final String idPrefix;

    boolean stop = false;

    public YouTubeLiveChatRunnable(YouTubeWeb web, YouTubeLiveChats handler, String id) {
        this.web = web;
        this.handler = handler;
        this.idPrefix = "["+id+"] ";
    }

    private final void info(String message) {
        LOGGER.info(idPrefix+message);
    }

    private final void warning(String message) {
        LOGGER.warning(idPrefix+message);
    }

    public void loadInformation(String channel_identifier) throws YouTube404, UnableJoin, InternetOffline {
        LOGGER.info("loading Information for \"" + channel_identifier + "\".");
        this.owner_id_channel = channel_identifier;
        client_message_id = Helper.generate_random(26);
        if(Helper.isChannelID(channel_identifier)) {
            this.owner_id_channel = channel_identifier;
            originalPage = web.getLatestChannelLiveStream(channel_identifier);
            if(originalPage instanceof PrivateStream) {
                handler.onNotice(channel_identifier,
                        String.format("%s's has their live stream currently unlisted or private. Using safeguard.",
                                this.owner_id_channel), new MsgTags(new HashMap<>()));
            } else if(originalPage instanceof VideoPage) {
                LiveChatContinuation continuation = ((VideoPage) originalPage).getContinuation();
                lastResponse = web.getLiveChatPage(continuation.getTimedContinuationData().getContinuation()).getLiveChatResponse();
                viewer_name = lastResponse.getLiveChatContinuation().getViewerName();
                handler.receivedUsername(viewer_name);
                handler.receivedEmoticons(lastResponse.getLiveChatContinuation().getEmotes());
            } else if(originalPage == null) {
                throw (new UnableJoin());
            }
        }
    }


    public LiveChatResponse fetchChats() throws InterruptedException {
        int x = 0;
        while(true){
            try {
                return web.fetchNewChats(lastResponse.getLiveChatContinuation().getTimedContinuationData().getContinuation(),
                        ((VideoPage) originalPage).getDelegatedSessionId(), ((VideoPage) originalPage).getIntertubeContext());
            } catch (InternetOffline internetOffline) {
                if(x == 0) {
                    LOGGER.log(Logging.USERINFO, "Warning: Server not responding");
                    x++;
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }
    }

    private ChatItemsByAuthorAsDeletedAction last_deleted_action;

    @Override
    public void run() {
        while(!stop) {
            try {
                if (originalPage instanceof PrivateStream) {
                    int delay = 10;
                    handler.onNotice(owner_id_channel, String.format("Retrying getting stream information in %s seconds.", delay), new MsgTags(new HashMap<>()));
                    TimeUnit.SECONDS.sleep(delay);
                    loadInformation(owner_id_channel);
                } else if(originalPage instanceof VideoPage){
                    if(lastResponse.isLiveChatClosed()) {
                        handler.onNotice(owner_id_channel, "Stream Chat has been closed. Getting new live chat.", new MsgTags(new HashMap<>()));
                        loadInformation(owner_id_channel);
                    } else {
                        List<BaseAction> actions = lastResponse.getLiveChatContinuation().getActions();
                        for (BaseAction action : actions) {
                            if (action instanceof ChatItemAction) {
                                ActionItem item = ((ChatItemAction) action).getItem();
                                if (item instanceof liveChatTextMessageRenderer) {
                                    liveChatTextMessageRenderer message = (liveChatTextMessageRenderer) item;
                                    handler.onChannelMessage(this.owner_id_channel, client_message_id, message);
                                } else if(item instanceof liveChatViewerEngagementMessageRenderer) {
                                    liveChatViewerEngagementMessageRenderer message = (liveChatViewerEngagementMessageRenderer) item;
                                    handler.onNotice(this.owner_id_channel, message.getMessage().toString(), new MsgTags(new HashMap<>()));
                                } else if(item instanceof liveChatModerationMessageRenderer) {
                                    ModerationData data = ((liveChatModerationMessageRenderer) item).parseModerationData(this.owner_id_channel);
                                    if(last_deleted_action != null) {
                                        // Most stupidest thing to have to save this for later
                                        // YouTube is stupid.
                                        // This is the only way to give the channel id to make sure the right person shows up as banned
                                        // or else it will have took at usernames
                                        data.channel_id = last_deleted_action.getTargetChannelId();
                                    }
                                    last_deleted_action = null;
                                    handler.receivedModerationData(data);
                                }
                            } else if(action instanceof ChatItemAsDeletedAction) {
                                handler.onClearMsg(((ChatItemAsDeletedAction) action).parse(), this.owner_id_channel, ((ChatItemAsDeletedAction) action).parseModerationData(this.owner_id_channel));
                            } else if(action instanceof ChatItemsByAuthorAsDeletedAction) {
                                last_deleted_action = (ChatItemsByAuthorAsDeletedAction) action;
                                handler.onClearChat(((ChatItemsByAuthorAsDeletedAction) action).parse(), this.owner_id_channel, ((ChatItemsByAuthorAsDeletedAction) action).getTargetChannelId());
                            } else {
                                warning("unrecognized action: " + action.getUnrecognized());
                            }
                        }

                        TimeUnit.MILLISECONDS.sleep(lastResponse.getLiveChatContinuation().getTimedContinuationData().getPollingIntervalMillis());
                        lastResponse = fetchChats();
                    }
                }
            } catch (InterruptedException | YouTube404 | UnableJoin | InternetOffline e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        stop = true;
    }

    public void send_message(String text) {
        JSONArray array = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("text", text);
        array.add(jsonObject);
        send_message(array);
    }

    public void send_message(JSONArray segments) {
        if(originalPage instanceof VideoPage) {
            String video_id = ((VideoPage) originalPage).getPlayerConfig().getPlayerResponse().getVideoDetails().getVideoId();
            String param = "\nL\u0012!\n" +
                    "\u0018" + owner_id_channel + "\u0012\u0005/live*'\n" +
                    "\u0018" + owner_id_channel + "\u0012\u000B" + video_id +"\u0010\u0001\u0018\u0004";
            String first_encode = Base64.getEncoder().encodeToString(param.getBytes());
            String second_encode = Base64.getEncoder().encodeToString(first_encode.replace("=", "%3D").getBytes());

            String client_id = client_message_id + message_count;

            web.sendMessage(client_id, second_encode, segments, ((VideoPage) originalPage).getDelegatedSessionId(), ((VideoPage) originalPage).getIntertubeContext());
            message_count += 1;
        }
    }

    public boolean timeout(String channel_id, String time) {
        if(originalPage instanceof VideoPage) {
            if(!channel_id.startsWith("UC")) {
                return false;
            } else {
                int location = channel_id.indexOf("UC");
                channel_id = channel_id.substring(location + 2);
                String video_id = ((VideoPage) originalPage).getPlayerConfig().getPlayerResponse().getVideoDetails().getVideoId();
                String param = "\nL\u0012!\n" +
                        "\u0018" + owner_id_channel + "\u0012\u0005/live*'\n" +
                        "\u0018" + owner_id_channel + "\u0012\u000B" +
                        video_id + "2\u0018\n\u0016" + channel_id + "P\u0001X\u0001";
                String first_encode = Base64.getEncoder().encodeToString(param.getBytes());
                String second_encode = Base64.getEncoder().encodeToString(first_encode.replace("=", "%3D").getBytes());
                web.sendModeration(second_encode, ((VideoPage) originalPage).getDelegatedSessionId(), ((VideoPage) originalPage).getIntertubeContext());
                return true;
            }
        }
        return false;
    }

    public boolean ban(String channel_id) {
        if(originalPage instanceof VideoPage) {
            if (!channel_id.startsWith("UC")) {
                return false;
            } else {
                int location = channel_id.indexOf("UC");
                channel_id = channel_id.substring(location + 2);
                String video_id = ((VideoPage) originalPage).getPlayerConfig().getPlayerResponse().getVideoDetails().getVideoId();
                String param = "\nL\u0012!\n" +
                        "\u0018" + owner_id_channel + "\u0012\u0005/live*'\n" +
                        "\u0018" + owner_id_channel + "\u0012\u000B" + video_id + "\"\u0018\n" +
                        "\u0016" + channel_id + "P\u0001X\u0001";
                String first_encode = Base64.getEncoder().encodeToString(param.getBytes());
                String second_encode = Base64.getEncoder().encodeToString(first_encode.replace("=", "%3D").getBytes());
                web.sendModeration(second_encode, ((VideoPage) originalPage).getDelegatedSessionId(), ((VideoPage) originalPage).getIntertubeContext());
                return true;
            }
        }
        return false;
    }

    public boolean unban(String channel_id) {
        if(originalPage instanceof VideoPage) {
            if (!channel_id.startsWith("UC")) {
                return false;
            } else {
                int location = channel_id.indexOf("UC");
                channel_id = channel_id.substring(location + 2);
                String video_id = ((VideoPage) originalPage).getPlayerConfig().getPlayerResponse().getVideoDetails().getVideoId();
                String param = "\nL\u0012!\n" +
                        "\u0018" + owner_id_channel + "\u0012\u0005/live*'\n" +
                        "\u0018" + owner_id_channel + "\u0012\u000B" + video_id + "*\u0018\n" +
                        "\u0016" + channel_id + "P\u0001X\u0001";
                String first_encode = Base64.getEncoder().encodeToString(param.getBytes());
                String second_encode = Base64.getEncoder().encodeToString(first_encode.replace("=", "%3D").getBytes());
                web.sendModeration(second_encode, ((VideoPage) originalPage).getDelegatedSessionId(), ((VideoPage) originalPage).getIntertubeContext());
                return true;
            }
        }
        return false;
    }

}