package chatty;

import static chatty.Logging.USERINFO;

import chatty.exceptions.InternetOffline;
import chatty.exceptions.UnableJoin;
import chatty.exceptions.YouTube404;
import chatty.lang.Language;
import chatty.util.DateTime;
import chatty.util.DelayedActionQueue;
import chatty.util.api.Emoticon;
import chatty.util.api.YouTubeWeb;
import chatty.util.api.usericons.Usericon;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatTextMessageRenderer;
import chatty.util.api.youtubeObjects.ModerationData;
import chatty.util.irc.MsgParameters;
import chatty.util.irc.MsgTags;
import org.json.simple.JSONArray;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public abstract class YouTubeLiveChats {

    private static final Logger LOGGER = Logger.getLogger(YouTubeLiveChats.class.getName());

    /**
     * Delay between JOINs (in milliseconds).
     */
    private static final int JOIN_DELAY = 1200;

    private final DelayedActionQueue<String> joinQueue
            = DelayedActionQueue.create(new DelayedJoinAction(), JOIN_DELAY);

    private final HashMap<String, YouTubeLiveChatRunnable> channels = new HashMap<>();

    private long connectedSince = -1;

    private volatile int state = STATE_NONE;

    /**
     * State while being offline, either not connected or already disconnected
     * without reconnecting.
     */
    public static final int STATE_NONE = 0;
    /**
     * State value after being "enabled" or ("connected")
     */
    public static final int STATE_ENABLED = 2;

    private final String id;
    private final String idPrefix;

    private final YouTubeWeb web;

    private final String user_channel_id;

    public YouTubeLiveChats(String id, YouTubeWeb web, String user_channel_id) {
        this.id = id;
        this.idPrefix = "["+id+"] ";
        this.web = web;
        this.user_channel_id = user_channel_id;
    }

    private void info(String message) {
        LOGGER.info(idPrefix+message);
    }

    private void warning(String message) {
        LOGGER.warning(idPrefix+message);
    }

    /**
     * Set a new connection state.
     *
     * @param newState
     */
    protected void setState(int newState) {
        this.state = newState;
    }

    /**
     * Get the current connection state
     *
     * @return
     */
    public int getState() {
        return state;
    }

    public boolean isRegistered() {
        return state == STATE_ENABLED;
    }

    public boolean isOffline() {
        return state == STATE_NONE;
    }

    public String getConnectedSince() {
        return DateTime.ago(connectedSince);
    }

    /**
     * Outputs the debug string
     *
     * @param line
     */
    abstract public void debug(String line);

    public final void connect() {
        if (state >= STATE_ENABLED) {
            warning("Already enabled.");
            return;
        }

        onConnectionAttempt("no_ip", 0, true);
        fake_connected();
    }

    public boolean disconnect() {
        return true;
    }

    /**
     * Listener for the join queue, which is called when the next channel can
     * be joined.
     */
    private class DelayedJoinAction implements DelayedActionQueue.DelayedActionListener<String> {
        @Override
        public void actionPerformed(String item) {
            info("JOIN: "+item+" (delayed)");
            joinChannelImmediately(item);
        }
    }

    /**
     * Joins {@code channel} on a queue, that puts some time between each join.
     *
     * @param channel The name of the channel to join
     */
    public void joinChannel(String channel) {
        info("JOINING: " + channel);
        joinQueue.add(channel);
    }

    /**
     * Join a channel.
     *
     * @param channel_identifier The Channel Identifier
     */
    public void joinChannelImmediately(String channel_identifier) {
        if (state >= STATE_ENABLED) {
            YouTubeLiveChatRunnable channelRunnable = new YouTubeLiveChatRunnable(web, this, id);
            onJoinAttempt(channel_identifier);
            try {
                channelRunnable.loadInformation(channel_identifier);
                if(channelRunnable.viewer_name != null) {
                    onJoin(channel_identifier, user_channel_id, channelRunnable.viewer_name);
                } else {
                    onJoin(channel_identifier, user_channel_id, "[NOT LOGGED IN]");
                }
                new Thread(channelRunnable).start();
            } catch (YouTube404 | UnableJoin | InternetOffline ignored) {
                LOGGER.warning("Join may have failed ("+channel_identifier+")");
                LOGGER.log(USERINFO, Language.getString("chat.error.joinFailed", channel_identifier));
            }
            channels.put(channel_identifier, channelRunnable);
        }
    }

    /**
     * Send a message, usually to a channel.
     *
     * @param to
     * @param message
     * @param tags
     */
    public void sendMessage(String channel, String message, MsgTags tags) {
        if(channels.get(channel) != null) {
            YouTubeLiveChatRunnable runnable = channels.get(channel);
            runnable.send_message(message);
        }
    }

    public void sendMessage(String channel, JSONArray segments, MsgTags tags) {
        if(channels.get(channel) != null) {
            YouTubeLiveChatRunnable runnable = channels.get(channel);
            runnable.send_message(segments);
        }
    }
    public boolean timeout(String channel, String msgId, String name, String time, String reason) {
        if(channels.get(channel) != null) {
            YouTubeLiveChatRunnable runnable = channels.get(channel);
            return runnable.timeout(name, time);
        }
        return false;
    }

    public boolean ban(String channel, String channel_id) {
        if(channels.get(channel) != null) {
            YouTubeLiveChatRunnable runnable = channels.get(channel);
            return runnable.ban(channel_id);
        }
        return false;
    }

    public boolean unban(String channel, String channel_id) {
        if(channels.get(channel) != null) {
            YouTubeLiveChatRunnable runnable = channels.get(channel);
            return runnable.unban(channel_id);
        }
        return false;
    }

    /**
     * Called from the Connection Thread once the initial connection has
     * been established without an error.
     *
     * So now work on getting the connection to the IRC Server going by
     * sending credentials and stuff.
     *
     */
    protected void fake_connected() {
        this.connectedSince = System.currentTimeMillis();
        setState(YouTubeLiveChats.STATE_ENABLED);
        onConnect();
        onRegistered();
    }

    /**
     * Called by the Connection Thread, when the Connection was closed, be
     * it because it was closed by the server, the program itself or because
     * of an error.
     *
     * @param reason The reason of the disconnect as defined in various
     * constants in this class
     * @param reasonMessage An error message or other information about the
     * disconnect
     */
    protected void disconnected(int reason, String reasonMessage) {
        // Clear any potential join queue, so it doesn't carry over to the next
        // connection
        joinQueue.clear();

        // Retrieve state before changing it, but must be changed before calling
        // onDisconnect() which might check the state when trying to reconnect
        int oldState = getState();
        setState(YouTubeLiveChats.STATE_NONE);
    }

    /**
     * Convenience method without a reason message.
     *
     * @param reason
     */
    void disconnected(int reason) {
        disconnected(reason,"");
    }


    /*
     * Methods that can by overwritten by another Class
     */

    void onChannelMessage(String channel, String client_id, liveChatTextMessageRenderer messageRenderer) {}

    void onQueryMessage(String nick, String from, String text) {}

    void onNotice(String nick, String from, String text) {}

    void onNotice(String channel, String text, MsgTags tags) { }

    void onJoinAttempt(String channel) {}

    void onJoin(String channel, String user_channel_id, String username) {}

    void onPart(String channel, String nick) { }

    void onModeChange(String channel, String nick, boolean modeAdded, String mode, String prefix) { }

    void onUserlist(String channel, String[] nicks) {}

    void onWhoResponse(String channel, String nick) {}

    void onConnectionAttempt(String server, int port, boolean secured) { }

    void onConnect() { }

    void onRegistered() { }

    void onDisconnect(int reason, String reasonMessage) { }

    void parsed(String prefix, String command, MsgParameters parameters) { }

    void raw(String message) { }

    void sent(String message) { }

    void onUserstate(String channel, MsgTags tags) { }

    void onGlobalUserstate(MsgTags tags) { }

    void onClearChat(MsgTags tags, String channel, String name) { }

    void onClearMsg(MsgTags tags, String channel, ModerationData data) { }

    void onChannelCommand(MsgTags tags, String nick, String channel, String command, String trailing) { }

    void onCommand(String nick, String command, String parameter, String text, MsgTags tags) { }

    void onUsernotice(String channel, String message, MsgTags tags) { }

    void addUsericons(List<Usericon> icons) { }

    void receivedModerationData(ModerationData data) { }

    void receivedUsername(String username) {}

    void receivedEmoticons(Set<Emoticon> emoticons) {  }


    //User getUserFromMessage(String channel, String targetMsgId) { return null; }
    // I hate this so much but there is legit no way from YouTube getting username. This is why it's so bad.

}
