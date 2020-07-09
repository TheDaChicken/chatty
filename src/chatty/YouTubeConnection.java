package chatty;

import chatty.gui.colors.UsercolorManager;
import chatty.util.StringUtil;
import chatty.util.api.Emoticons;
import chatty.util.api.YouTubeWeb;
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
     * The channel_id to send to the server. This is stored to reconnect.
     */
    private volatile String channel_id;

    /**
     * Holds the UserManager instance, which manages all the user objects.
     */
    protected UserManager users = new UserManager();

    private final RoomManager rooms;

    private final YouTubeLiveChats liveChat;

    private final SpamProtection spamProtection;
    private final ChannelStateManager channelStates = new ChannelStateManager();

    private final SentMessages sentMessages = new SentMessages();

    public YouTubeConnection(final ConnectionListener listener, Settings settings,
                             String label, RoomManager rooms, YouTubeWeb web) {
        liveChat = new YouTubeLiveChatClass(label, web);
        this.listener = listener;
        this.settings = settings;
        this.rooms = rooms;
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

    public User localUserJoined(String channel) {
        return userJoined(channel, channel_id, null);
    }

    public User getLocalUser(String channel) {
        return users.getUser(rooms.getRoom(channel), channel_id, "");
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
    public void autoJoinChannels(String channel_id, String[] autojoin) {
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
        if (liveChat.getState() <= YouTubeLiveChats.STATE_NONE) {
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
    private class YouTubeLiveChatClass extends YouTubeLiveChats {
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

        public YouTubeLiveChatClass(String id, YouTubeWeb web) {
            super(id, web);
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
            if (tags.isEmpty()) {
                return;
            }
            /*
             * Any and all tag values may be null, so account for that when
             * checking against them.
             */
            // Whether anything in the user changed to warrant an update
            boolean changed = false;

            Map<String, String> badges = Helper.parseBadges(tags.get("badges"));
            if (user.setBadges(badges)) {
                changed = true;
            }

            // Update color
            String color = tags.get("color");
            if (color != null && !color.isEmpty()) {
                user.setColor(color);
            }

            // Update user status
            boolean turbo = tags.isTrue("turbo") || badges.containsKey("turbo") || badges.containsKey("premium");
            boolean subscriber = badges.containsKey("subscriber") || badges.containsKey("founder");
            if (user.setSubscriber(subscriber)) {
                changed = true;
            }
            if (user.setVip(badges.containsKey("vip"))) {
                changed = true;
            }
            if (user.setModerator(badges.containsKey("moderator"))) {
                changed = true;
            }
            if (user.setStaff(badges.containsKey("staff"))) {
                changed = true;
            }

            // Temporarily check both for containing a value as Twitch is
            // changing it
//            String userType = tags.get("user-type");
//            if (user.setModerator("mod".equals(userType))) {
//                changed = true;
//            }
//            if (user.setStaff("staff".equals(userType))) {
//                changed = true;
//            }
//            if (user.setAdmin("admin".equals(userType))) {
//                changed = true;
//            }
//            if (user.setGlobalMod("global_mod".equals(userType))) {
//                changed = true;
//            }

            user.setId(tags.get("user-id"));

            if (changed && user != users.specialUser) {
                listener.onUserUpdated(user);
            }
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
        void onNotice(String nick, String from, String text) {
            if (this != liveChat) {
                return;
            }
            // Should only be from the server for now
            listener.onNotice(text);
        }

        @Override
        void onNotice(String channel, String text, MsgTags tags) {
            if (this != liveChat) {
                return;
            }
            if (tags.isValue("msg-id", "whisper_invalid_login")) {
                listener.onInfo(text);
            } else if (onChannel(channel)) {
                infoMessage(channel, text, tags);
            } else if (isChannelOpen(channel)) {
                infoMessage(channel, text, tags);

                Room room = rooms.getRoom(channel);
                if (room.isChatroom()) {
                    if (tags.isValue("msg-id", "no_permission")) {
                        info(room, "Cancelled trying to join channel.", null);
                        //joinChecker.cancel(channel);
                    }
                }
            } else {
                listener.onInfo(String.format("[Info/%s] %s", rooms.getRoom(channel), text));
            }
        }


        @Override
        void onQueryMessage(String nick, String from, String text) {
            if (this != liveChat) {
                return;
            }
            if (nick.startsWith("*")) {
                listener.onSpecialMessage(nick, text);
            }
            if (nick.equals("jtv")) {
                listener.onInfo("[Info] "+text);
            }
        }

        /**
         * Any kind of info message. This can be either from jtv (legacy) or the
         * new NOTICE messages to the channel.
         *
         * @param channel
         * @param text
         */
        private void infoMessage(String channel, String text, MsgTags tags) {
            info(channel, "[Info] " + text, tags);
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
        public void onUserstate(String channel, MsgTags tags) {
            if (onChannel(channel)) {
                updateUserstate(channel, tags);
            }
        }

        @Override
        public void onGlobalUserstate(MsgTags tags) {
            updateUserstate(null, tags);
        }

        private void updateUserstate(String channel, MsgTags tags) {
            if (channel != null) {
                /*
                 * Update state for the local user in the given channel, also
                 * assuming the user is now in that channel and thus adding the
                 * user if necessary.
                 */
                User user = localUserJoined(channel);
                updateUserFromTags(user, tags);
            } else {
                /*
                 * Update all existing users with the local name, assuming that
                 * all the state is global if no channel is given.
                 */
                for (User user : users.getUsersByChannelID(channel_id)) {
                    updateUserFromTags(user, tags);
                }
            }

            /**
             * Update special user which can be used to initialize newly created
             * local users on other channels. This may be necessary when some
             * info is only being send in the GLOBALUSERSTATE command, which may
             * not be send after every join or message.
             *
             * This may be updated with local and global info, however only the
             * global info is used to initialize newly created local users.
             */
            updateUserFromTags(users.specialUser, tags);

            //--------------------------
            // Emotesets
            //--------------------------
            listener.onEmotesets(Emoticons.parseEmotesets(tags.get("emote-sets")));
        }

        @Override
        void onJoinAttempt(String channel) {
            if (this == liveChat) {
                listener.onJoinAttempt(rooms.getRoom(channel));
                openChannels.add(channel);
            }
        }

        @Override
        public void onClearMsg(MsgTags tags, String channel, String msg) {
            String login = tags.get("login");
            String targetMsgId = tags.get("target-msg-id");
            if (!StringUtil.isNullOrEmpty(login, targetMsgId)) {
                User user = users.getUserIfExists(channel, login);
                if (user != null) {
                    listener.onMsgDeleted(user, targetMsgId, msg);
                }
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

    public void info(String channel, String message, MsgTags tags) {
        listener.onInfo(rooms.getRoom(channel), message, tags);
    }

    public void info(Room room, String message, MsgTags tags) {
        listener.onInfo(room, message, tags);
    }

    public void info(String message) {
        listener.onInfo(message);
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
