package chatty;

import chatty.gui.colors.UsercolorManager;
import chatty.util.StringUtil;
import chatty.util.api.YouTubeApi;
import chatty.util.api.usericons.UsericonManager;
import chatty.util.irc.MsgTags;
import chatty.util.settings.Settings;

import java.util.*;
import java.util.logging.Logger;

public class YouTubeConnection {

    public enum JoinError {
        NOT_REGISTERED, ALREADY_JOINED, INVALID_NAME, ROOM
    }

    private static final Logger LOGGER = Logger.getLogger(YouTubeConnection.class.getName());

    private final ConnectionListener listener;
    private final Settings settings;

    /**
     * Channels that should be joined after connecting.
     */
    private volatile String[] autojoin;

    /**
     * Channels that are open in the program (in tabs if it's more than one).
     */
    private final Set<String> openChannels = Collections.synchronizedSet(new HashSet<String>());

    /**
     * The username to send to the server. This is stored to reconnect.
     */
    private volatile String channel_id;

    /**
     * Holds the UserManager instance, which manages all the user objects.
     */
    protected UserManager users = new UserManager();

    private final RoomManager rooms;

    private final YouTubeLiveChat liveChat;

    private final SpamProtection spamProtection;
    private final ChannelStateManager channelStates = new ChannelStateManager();

    private final SentMessages sentMessages = new SentMessages();

    private final YouTubeApi api;

    public YouTubeConnection(final ConnectionListener listener, Settings settings,
                             String label, RoomManager rooms, YouTubeApi api) {
        liveChat = new YouTubeLiveChatHandler(label, api);
        this.listener = listener;
        this.settings = settings;
        this.rooms = rooms;
        this.api = api;
        spamProtection = new SpamProtection();
        spamProtection.setLinesPerSeconds(settings.getString("spamProtection"));
        //users.setCapitalizedNames(settings.getBoolean("capitalizedNames"));
        users.setSettings(settings);
    }

    public void addChannelStateListener(ChannelStateManager.ChannelStateListener listener) {
        channelStates.addListener(listener);
    }

    public ChannelState getChannelState(String channel) {
        return channelStates.getState(channel);
    }

    public void setUsercolorManager(UsercolorManager m) {
        users.setUsercolorManager(m);
    }

    public void setAddressbook(Addressbook addressbook) {
        users.setAddressbook(addressbook);
    }

    public void setUsericonManager(UsericonManager usericonManager) {
        users.setUsericonManager(usericonManager);
    }

    public void setCustomNamesManager(CustomNames customNames) {
        users.setCustomNamesManager(customNames);
    }

    public void setSpamProtection(String setting) {
        spamProtection.setLinesPerSeconds(setting);
    }

    public String getSpamProtectionInfo() {
        return spamProtection.toString();
    }

    public void updateRoom(Room room) {
        users.updateRoom(room);
    }

    public User getUser(String channel, String channel_id, String name) {
        return users.getUser(rooms.getRoom(channel), channel_id, name);
    }

    public User getExistingUser(String channel, String channel_id) {
        return users.getUserIfExists(channel, channel_id);
    }

    /**
     * The channel_id used for the last connection.
     *
     * @return
     */
    public String getChannelID() {
        return channel_id;
    }


    public Set<String> getOpenChannels() {
        synchronized(openChannels) {
            return new HashSet<>(openChannels);
        }
    }

    public Set<Room> getOpenRooms() {
        Set<String> chans = getOpenChannels();
        Set<Room> result = new HashSet<>();
        for (String chan : chans) {
            result.add(rooms.getRoom(chan));
        }
        return result;
    }

    public Room getRoomByChannel(String channel) {
        return rooms.getRoom(channel);
    }

    public int getState() {
        return liveChat.getState();
    }

    /**
     * Checks if actually joined to the given channel.
     *
     * @param channel
     * @return
     */
    public boolean onChannel(String channel) {
        return onChannel(channel, false);
    }

    public boolean isChannelOpen(String channel) {
        return openChannels.contains(channel);
    }

    public void closeChannel(String channel) {
        partChannel(channel);
        openChannels.remove(channel);
        users.clear(channel);
    }

    public void setAllOffline() {
        users.setAllOffline();
    }

    public void partChannel(String channel) {
        if (onChannel(channel)) {
            LOGGER.info("TODO partChannel();");
        }
    }

    /**
     * Checks if actually joined to the given channel and also, if not,
     * optionally outputs a message to inform the user about it.
     *
     * @param channel
     * @param showMessage
     * @return
     */
    public boolean onChannel(String channel, boolean showMessage) {
        boolean onChannel = false;
        if (showMessage && !onChannel) {
            if (channel == null || channel.isEmpty()) {
                listener.onInfo("Not in a channel");
            } else {
                listener.onInfo("Not in this channel (" + channel + ")");
            }
        }
        return onChannel;
    }

    /**
     * This actually connects to the server. All data necessary for connecting
     * should already be present at this point, however it still checks again if
     * it exists.
     *
     * Even if connected, this will store the given data and potentially use it
     * for reconnecting.
     *
     * @param server The server address to connect to
     * @param serverPorts The server ports to connect to (comma-separated list)
     * @param username The username to use for connecting
     * @param password The password
     * @param autojoin The channels to join after connecting
     */
    public void prepareAutoJoin(String channel_id, String[] autojoin) {
        this.channel_id = channel_id;
        this.autojoin = autojoin;
        users.setLocalChannelID(channel_id);
        start();
    }

    /**
     * Connect to the main connection based on the current login data. Will only
     * connect it not already connected/connecting.
     */
    private void start() {
        if (liveChat.getState() <= YouTubeLiveChat.STATE_NONE) {
            liveChat.connect();
        } else {
            listener.onConnectError("Already enabled.");
        }
    }

    public User getSpecialUser() {
        return users.specialUser;
    }

    /**
     * Disconnect from the server or cancel trying to reconnect.
     *
     * @return true if the disconnect did something, or false if not actually
     * connected
     */
    public boolean disconnect() {
        boolean success = liveChat.disconnect();
        return success;
    }

    public void join(String channel_identifier) {
        liveChat.joinChannel(channel_identifier);
    }

    /**
     * Joins the channel with the given name, but only if the channel name
     * is deemed valid, it's possible to join channels at this point and we are
     * not already on the channel.
     *
     * @param channel The name of the channel, with or without leading '#'.
     */
    public void joinChannel(String channel) {
        Set<String> channels = new HashSet<>();
        channels.add(channel);
        joinChannels(channels);
    }

    /**
     * Join a rename of channels. Sorts out invalid channels and outputs an error
     * message, then joins the valid channels.
     *
     * @param channels Set of channelnames (valid/invalid, leading # or not).
     */
    public void joinChannels(Set<String> channels) {
        Set<String> valid = new LinkedHashSet<>();
        Set<String> invalid = new LinkedHashSet<>();
        Set<String> rooms = new LinkedHashSet<>();
        for (String channel : channels) {
            String checkedChannel = Helper.toValidChannel(channel);
            if (checkedChannel == null) {
                invalid.add(channel);
            } else if (checkedChannel.startsWith("#chatrooms:")) {
                rooms.add(channel);
            } else {
                valid.add(checkedChannel);
            }
        }
        for (String channel : invalid) {
            listener.onJoinError(channels, channel, JoinError.INVALID_NAME);
        }
        for (String channel : rooms) {
            listener.onJoinError(channels, channel, JoinError.ROOM);
        }
        joinValidChannels(valid);
    }

    /**
     * Joins the valid channels. If offline, opens the connect dialog with the
     * valid channels already entered.
     *
     * @param valid A Set of valid channels (valid names, with leading #).
     */
    private void joinValidChannels(Set<String> valid) {
        if (valid.isEmpty()) {
            return;
        } else if (!liveChat.isRegistered()) {
            listener.onJoinError(valid, null, JoinError.NOT_REGISTERED);
        } else {
            for (String channel : valid) {
                if (onChannel(channel)) {
                    listener.onJoinError(valid, channel, JoinError.ALREADY_JOINED);
                } else {
                    join(channel);
                }
            }
        }
    }


    /**
     * IRC Connection which handles the messages (manages users, special
     * messages etc.) and redirects them to the listener accordingly.
     */
    private class YouTubeLiveChatHandler extends YouTubeLiveChat {
        /**
         * Channels that this connection has joined. This is per connection, so
         * the main and secondary connection have different data here.
         */
        private final Set<String> joinedChannels = Collections.synchronizedSet(new HashSet<>());

        private final Set<String> rejoinChannel = Collections.synchronizedSet(new HashSet<>());

        /**
         * The prefix used for debug messages, so it can be determined which
         * connection it is from.
         */
        private final String idPrefix;

        /**
         * This only applies to irc2. This is reset on every new connection.
         * It's set to true once either a JOIN or a userlist from any channel
         * is received. It roughly indicates that the connection has probably
         * started to receive users.
         */
        private Set<String> userlistReceived = Collections.synchronizedSet(
                new HashSet<String>());

        public YouTubeLiveChatHandler(String id, YouTubeApi api) {
            super(id, api);
            this.idPrefix= "["+id+"] ";
        }

        public Set<String> getJoinedChannels() {
            synchronized (joinedChannels) {
                return new HashSet<>(joinedChannels);
            }
        }

        public boolean onChannel(String channel) {
            return joinedChannels.contains(channel);
        }

        public boolean primaryOnChannel(String channel) {
            LOGGER.info("primaryOnChannel(); TODO /");
            //return liveChat.onChannel(channel);
            return false;
        }

        @Override
        public void debug(String line) {
            LOGGER.info(idPrefix+line);
        }

        @Override
        void onConnect() {
            userlistReceived.clear();
        }

        @Override
        void onRegistered() {
            if (this != liveChat) {
                return;
            }

            if (autojoin != null) {
                for (String channel : autojoin) {
                    joinChannel(channel);
                }
                /**
                 * Only use autojoin once, to prevent it from being used on
                 * reconnect (open channels should be used for that).
                 */
                autojoin = null;
            } else {
                joinChannels(getOpenChannels());
            }
            listener.onRegistered();
        }

        @Override
        void onJoin(String channel, String channel_id_, String username) {
            if (channel_id.equalsIgnoreCase(channel_id_))
                debug("JOINED: " + channel);
            User user = userJoined(channel, channel_id_, username);
            if (this == liveChat && !onChannel(channel)) {
                listener.onChannelJoined(user);
            }
            joinedChannels.add(channel);
        }

        private void clearUserlist(String channel) {
            users.setAllOffline(channel);
            listener.onUserlistCleared(channel);
        }

        @Override
        void onPart(String channel, String nick) {
            LOGGER.info("onPart();");
        }

        private void updateUserFromTags(User user, MsgTags tags) {

        }

        @Override
        void onChannelMessage(String channel, String channel_id, String nick, String text,
                              MsgTags tags, boolean action) {
            if (this != liveChat) {
                return;
            }
            if (nick.isEmpty()) {
                return;
            }
            if (onChannel(channel)) {
                User user = userJoined(channel, channel_id, nick);
                updateUserFromTags(user, tags);
                listener.onChannelMessage(user, text, action, tags);
            }
        }

        @Override
        void onUsernotice(String channel, String message, MsgTags tags) {

        }

        @Override
        protected void setState(int state) {
            super.setState(state);
            listener.onConnectionStateChanged(state);
        }

        /**
         * Checks if the given channel should be open.
         *
         * @param channel The channel name
         * @return
         */
        public boolean isChannelOpen(String channel) {
            return openChannels.contains(channel);
        }

        @Override
        void onJoinAttempt(String channel) {
            if (this == liveChat) {
                listener.onJoinAttempt(rooms.getRoom(channel));
                openChannels.add(channel);
            }
        }
    }

    /**
     * Sets a user as online, add the user to the userlist if not already
     * online.
     *
     * @param channel The channel the user joined
     * @param name The name of the user
     * @return The User
     */
    public User userJoined(String channel, String channel_id, String name) {
        User user = getUser(channel, channel_id, name);
        return userJoined(user);
    }

    public User userJoined(User user) {
        if (user.setOnline(true)) {
            if (user.getName().equals(user.getStream())) {
                user.setBroadcaster(true);
            }
            listener.onUserAdded(user);
        }
        return user;
    }


    public interface ConnectionListener {

        void onJoinAttempt(Room room);

        void onChannelJoined(User user);

        void onChannelLeft(Room room, boolean closeChannel);

        void onUserAdded(User user);

        void onUserRemoved(User user);

        void onUserlistCleared(String channel);

        void onUserUpdated(User user);

        void onChannelMessage(User user, String msg, boolean action, MsgTags tags);

        void onNotice(String message);

        /**
         * An info message to a specific channel, usually intended to be
         * directly output to the user.
         *
         * <p>The channel should not be null. If no channel is associated, use
         * {@link onInfo(String) onInfo(infoMessage)} instead.</p>
         *
         * @param channel The channel the info message belongs to
         * @param infoMessage The info message
         */
        void onInfo(Room room, String infoMessage, MsgTags tags);

        /**
         * An info message, usually intended to be directly output to the user.
         *
         * <p>Since no channel is associated, this is likely to be output to the
         * currently active channel/tab.</p>
         *
         * @param infoMessage The info message
         */
        void onInfo(String infoMessage);

        void onGlobalInfo(String message);

        void onBan(User user, long length, String reason, String targetMsgId);

        void onMsgDeleted(User user, String targetMsgId, String msg);

        void onRegistered();

        void onDisconnect(int reason, String reasonMessage);

        void onMod(User user);

        void onUnmod(User user);

        void onConnectionStateChanged(int state);

        void onEmotesets(Set<String> emotesets);

        void onConnectError(String message);

        void onJoinError(Set<String> toJoin, String errorChannel, JoinError error);

        void onRawReceived(String text);

        void onRawSent(String text);

        void onHost(Room room, String target);

        void onChannelCleared(Room room);

        /**
         * A notification in chat for a new subscriber or resub.
         *
         * @param channel The channel (never null)
         * @param user The User object (may be dummy user object with empty
         * name, but never null)
         * @param text The notification text (never null or empty)
         * @param message The attached message (may be null or empty)
         * @param months The number of subscribed months (may be -1 if invalid)
         * @param emotes The emotes tag, yet to be parsed (may be null)
         */
        void onSubscriberNotification(User user, String text, String message, int months, MsgTags tags);

        void onUsernotice(String type, User user, String text, String message, MsgTags tags);

        void onSpecialMessage(String name, String message);

        void onRoomId(String channel, String id);
    }

    /**
     * Helps to hide the echo to sent messages in chatrooms.
     */
    private static class SentMessages {

        /**
         * How long keep sent messages stored.
         */
        private static final long TIMEOUT = 2000;

        private final Map<String, List<Message>> messages = new HashMap<>();

        /**
         * Store sent message. Should only be called for chatrooms (or possibly
         * other cases where sent messages are repeated back).
         *
         * @param channel The channel
         * @param message The text of the message
         */
        public synchronized void messageSent(String channel, String message) {
            if (!messages.containsKey(channel)) {
                messages.put(channel, new ArrayList<>());
            }
            messages.get(channel).add(new Message(channel, message));
        }

        /**
         * Check if the given channel and message text is currently stored as
         * a sent message.
         *
         * @param channel The channel
         * @param message The text of the message
         * @return true if the message was sent and the echo should be hidden,
         * false otherwise
         */
        public synchronized boolean shouldHide(String channel, String message) {
            if (messages.containsKey(channel)) {
                clearOld(channel);
                for (Message m : messages.get(channel)) {
                    if (m.message.equals(message)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Remove stored messages for the given channel that have expired.
         *
         * @param channel The channel
         */
        private void clearOld(String channel) {
            Iterator<Message> it = messages.get(channel).iterator();
            while (it.hasNext()) {
                Message m = it.next();
                if (System.currentTimeMillis() - m.time > TIMEOUT) {
                    it.remove();
                }
            }
        }

        private static class Message {
            private final String channel;
            private final String message;
            private final long time = System.currentTimeMillis();

            public Message(String channel, String message) {
                this.channel = channel;
                this.message = message;
            }

            @Override
            public String toString() {
                return channel+" "+message;
            }
        }

    }

}
