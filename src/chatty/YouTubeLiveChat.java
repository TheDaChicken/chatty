package chatty;

import static chatty.Logging.USERINFO;
import chatty.lang.Language;
import chatty.util.DateTime;
import chatty.util.DelayedActionQueue;
import chatty.util.api.YouTubeApi;
import chatty.util.irc.MsgParameters;
import chatty.util.irc.MsgTags;

import java.util.HashMap;
import java.util.logging.Logger;

public abstract class YouTubeLiveChat {

    private static final Logger LOGGER = Logger.getLogger(YouTubeLiveChat.class.getName());

    /**
     * Delay between JOINs (in milliseconds).
     */
    private static final int JOIN_DELAY = 1200;

    private final DelayedActionQueue<String> joinQueue
            = DelayedActionQueue.create(new DelayedJoinAction(), JOIN_DELAY);

    private final HashMap<String, YouTubeChannelRunnable> channels = new HashMap<>();

    private long connectedSince = -1;

    private volatile int state = STATE_OFFLINE;

    /**
     * State while reconnecting.
     */
    public static final int STATE_RECONNECTING = -1;
    /**
     * State while being offline, either not connected or already disconnected
     * without reconnecting.
     */
    public static final int STATE_OFFLINE = 0;
    /**
     * State value while trying to connect (opening socket and streams).
     */
    public static final int STATE_CONNECTING = 1;
    /**
     * State value after having connected (socket and streams successfully opened).
     */
    public static final int STATE_CONNECTED = 2;
    /**
     * State value once the connection has been accepted by the IRC Server
     * (registered).
     */
    public static final int STATE_REGISTERED = 3;

    /**
     * Disconnect reason value for Unknown host.
     */
    public static final int ERROR_UNKNOWN_HOST = 100;
    /**
     * Disconnect reason value for socket timeout.
     */
    public static final int ERROR_SOCKET_TIMEOUT = 101;
    /**
     * Disconnect reason value for socket error.
     */
    public static final int ERROR_SOCKET_ERROR = 102;
    /**
     * Disconnect reason value for requested disconnect, meaning the user
     * wanted to disconnect from the server.
     */
    public static final int REQUESTED_DISCONNECT = 103;
    /**
     * Disconnect reason value for when the connection was closed.
     */
    public static final int ERROR_CONNECTION_CLOSED = 104;

    public static final int ERROR_REGISTRATION_FAILED = 105;

    public static final int REQUESTED_RECONNECT = 106;

    public static final int SSL_ERROR = 107;

    private final String id;
    private final String idPrefix;

    private final YouTubeApi api;

    public YouTubeLiveChat(String id, YouTubeApi api) {
        this.id = id;
        this.idPrefix = "["+id+"] ";
        this.api = api;
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
        return state == STATE_REGISTERED;
    }

    public boolean isOffline() {
        return state == STATE_OFFLINE;
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
        if (state >= STATE_CONNECTED) {
            warning("Already connected.");
            return;
        }

        if (state >= STATE_CONNECTING) {
            warning("Already trying to connect.");
            return;
        }

        state = STATE_CONNECTING;
        onConnectionAttempt("no_ip", 0, true);
        connected();
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
     * @param channel_identifier
     */
    public void joinChannelImmediately(String channel_identifier) {
        if (state >= STATE_REGISTERED) {
            YouTubeChannelRunnable channelRunnable = new YouTubeChannelRunnable(this, api);
            onJoinAttempt(channel_identifier);
            int result = channelRunnable.loadInformation(channel_identifier);
            if(result < 0) {
                new Thread(channelRunnable).start();
                onJoin(channel_identifier, channel_identifier, "[ME]");
            } else {
                /*
                    TODO Make Language String for QUOTA
                 */
                if(result == YouTubeChannelRunnable.FAILED) {
                    LOGGER.warning("Join may have failed ("+channel_identifier+")");
                    LOGGER.log(USERINFO, Language.getString("chat.error.joinFailed", channel_identifier));
                } else if(result == YouTubeChannelRunnable.QUOTA) {
                    LOGGER.warning("Join failed ("+channel_identifier+")");
                    LOGGER.log(USERINFO, "Join failed: " + channel_identifier + " due to quota limits.", channel_identifier);
                }
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
    public void sendMessage(String to, String message, MsgTags tags) {

    }

    /**
     * Called from the Connection Thread once the initial connection has
     * been established without an error.
     *
     * So now work on getting the connection to the IRC Server going by
     * sending credentials and stuff.
     *
     */
    protected void connected() {
        this.connectedSince = System.currentTimeMillis();
        setState(YouTubeLiveChat.STATE_CONNECTED);
        onConnect();
        setState(STATE_REGISTERED);
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
        setState(YouTubeLiveChat.STATE_OFFLINE);
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

    void onChannelMessage(String channel, String channel_id, String nick, String text, MsgTags tags, boolean action) {}

    void onQueryMessage (String nick, String from, String text) {}

    void onNotice(String nick, String from, String text) {}

    void onNotice(String channel, String text, MsgTags tags) { }

    void onJoinAttempt(String channel) {}

    void onJoin(String channel, String channel_id_, String username) {}

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

    void onClearMsg(MsgTags tags, String channel, String msg) { }

    void onChannelCommand(MsgTags tags, String nick, String channel, String command, String trailing) { }

    void onCommand(String nick, String command, String parameter, String text, MsgTags tags) { }

    void onUsernotice(String channel, String message, MsgTags tags) { }

}
