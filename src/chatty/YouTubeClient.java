package chatty;

import chatty.gui.GuiUtil;
import chatty.gui.LaF;
import chatty.gui.MainGui;
import chatty.gui.colors.UsercolorManager;
import chatty.gui.components.menus.UserContextMenu;
import chatty.gui.components.textpane.ModLogInfo;
import chatty.lang.Language;
import chatty.splash.Splash;
import chatty.util.*;
import chatty.util.api.*;
import chatty.util.api.usericons.Usericon;
import chatty.util.api.usericons.UsericonManager;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatTextMessageRenderer;
import chatty.util.api.youtubeObjects.ModerationData;
import chatty.util.chatlog.ChatLog;
import chatty.util.commands.CustomCommand;
import chatty.util.commands.CustomCommands;
import chatty.util.commands.Parameters;
import chatty.util.irc.MsgTags;
import chatty.util.settings.FileManager;
import chatty.util.settings.Settings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class YouTubeClient {

    private static final Logger LOGGER = Logger.getLogger(YouTubeClient.class.getName());

    private volatile boolean shuttingDown = false;
    private volatile boolean settingsAlreadySavedOnExit = false;

    /**
     * Holds the Settings object, which is used to store and retrieve renametings
     */
    public final Settings settings;

    public final ChatLog chatLog;

    /**
     * Holds the YouTubeWeb object, which is used to make web requests
     */
    public final YouTubeWeb web;

    public final EmotesetManager emotesetManager;

    private final YouTubeConnection c;

    public final ChannelFavorites channelFavorites;

    public final RoomManager roomManager;

    /**
     * A reference to the Main Gui.
     */
    protected MainGui g;

    private final List<String> cachedDebugMessages = new ArrayList<>();
    private final List<String> cachedWarningMessages = new ArrayList<>();

    private final SettingsManager settingsManager;
    public final CustomCommands customCommands;
    public final Commands commands = new Commands();

    public final UsercolorManager usercolorManager;
    public final UsericonManager usericonManager;

    public final Addressbook addressbook;

    protected final CustomNames customNames;

    public YouTubeClient(Map<String, String> args) {
        // Logging
        new Logging(this);

        LOGGER.info("### Log start ("+ DateTime.fullDateTime()+")");
        LOGGER.info(Chatty.chattyVersion());
        LOGGER.info(Helper.systemInfo());
        LOGGER.info("[Working Directory] "+System.getProperty("user.dir")
                +" [Settings Directory] "+Chatty.getUserDataDirectory()
                +" [Classpath] "+System.getProperty("java.class.path"));

        settingsManager = new SettingsManager();
        settings = settingsManager.settings;
        settingsManager.defineSettings();
        settingsManager.loadSettingsFromFile();
        settingsManager.loadCommandLineSettings(args);
        settingsManager.overrideSettings();
        settingsManager.debugSettings();
        settingsManager.backupFiles();
        settingsManager.startAutoSave(this);

        //Helper.setDefaultTimezone(settings.getString("timezone"));

        addressbook = new Addressbook(Chatty.getUserDataDirectory()+"addressbook",
                Chatty.getUserDataDirectory()+"addressbookImport.txt", settings);
        if (!addressbook.loadFromSettings()) {
            addressbook.loadFromFile();
        }
        addressbook.setSomewhatUniqueCategories(settings.getString("abUniqueCats"));
        if (settings.getBoolean("abAutoImport")) {
            addressbook.enableAutoImport();
        }

        initDxSettings();

        if (settings.getBoolean("splash")) {
            Splash.initSplashScreen(Splash.getLocation((String)settings.mapGet("windows", "main")));
        }

        web = new YouTubeWeb(new YouTubeResults());
        customCommands = new CustomCommands(settings, this);
        customCommands.loadFromSettings();

        usercolorManager = new UsercolorManager(settings);
        usericonManager = new UsericonManager(settings);

        ImageCache.setDefaultPath(Paths.get(Chatty.getCacheDirectory()+"img"));
        ImageCache.setCachingEnabled(settings.getBoolean("imageCache"));
        ImageCache.deleteExpiredFiles();
        EmoticonSizeCache.loadFromFile();

        customNames = new CustomNames(settings);

        chatLog = new ChatLog(settings);
        chatLog.start();

        roomManager = new RoomManager(new MyRoomUpdatedListener());
        channelFavorites = new ChannelFavorites(settings, roomManager);

        c = new YouTubeConnection(new Messages(), settings, "main", roomManager, web);
        c.setAddressbook(addressbook);
        c.setCustomNamesManager(customNames);
        c.setUsercolorManager(usercolorManager);
        c.setUsericonManager(usericonManager);
        //c.setBotNameManager(botNameManager);
        c.addChannelStateListener(new ChannelStateUpdater());
        //c.setMaxReconnectionAttempts(settings.getLong("maxReconnectionAttempts"));

        LaF.setLookAndFeel(LaF.LaFSettings.fromSettings(settings));
        GuiUtil.addMacKeyboardActions();

        // Create GUI
        LOGGER.info("Create GUI..");
        g = new MainGui(this);
        g.loadSettings();
        emotesetManager = new EmotesetManager(web, g, settings);
        g.showGui();
    }

    public void init() {
        LOGGER.info("GUI shown");
        Splash.closeSplashScreen();

        // Output any cached warning messages
        warning(null);

        //addCommands();
        //g.addGuiCommands();

        // Connect or open connect dialog
        if (settings.getBoolean("connectOnStartup")) {
            prepareConnection();
        } else {
            switch ((int)settings.getLong("onStart")) {
                case 1:
                    g.openConnectDialog(null);
                    break;
                case 2:
                    prepareConnectionWithChannel(settings.getString("autojoinChannel"));
                    break;
                case 3:
                    prepareConnectionWithChannel(settings.getString("previousChannel"));
                    break;
                case 4:
                    prepareConnectionWithChannel(Helper.buildStreamsString(channelFavorites.getFavorites()));
                    break;
            }

        }

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(this)));

        if (Chatty.DEBUG) {
            //textInput(Room.EMPTY, "/test3");
        }

        UserContextMenu.client = this;
    }

    /**
     * Based on the current renametings, rename the system properties to disable
     * Direct3D and/or DirectDraw.
     */
    private void initDxSettings() {
        try {
            Boolean d3d = !settings.getBoolean("nod3d");
            Boolean ddraw = settings.getBoolean("noddraw");
            LOGGER.info(String.format("d3d: %s (%s) / noddraw: %s (%s) / opengl: (%s) / retina: %s",
                    d3d, System.getProperty("sun.java2d.d3d"),
                    ddraw, System.getProperty("sun.java2d.noddraw"),
                    System.getProperty("sun.java2d.opengl"),
                    GuiUtil.hasRetinaDisplay()));
            System.setProperty("sun.java2d.d3d", d3d.toString());
            System.setProperty("sun.java2d.noddraw", ddraw.toString());
        } catch (SecurityException ex) {
            LOGGER.warning("Error setting drawing settings: "+ex.getLocalizedMessage());
        }
    }

    /**
     * Close all channels except the ones in the given Array.
     *
     * @param except
     */
    private void closeAllChannelsExcept(String[] except) {
        Set<String> copy = c.getOpenChannels();
        for (String channel : copy) {
            if (!Arrays.asList(except).contains(channel)) {
                closeChannel(channel);
            }
        }
    }

    /**
     * Close a channel by either parting it if it is currently joined or
     * just closing the tab.
     *
     * @param channel
     */
    public void closeChannel(String channel) {
        if (c.onChannel(channel)) {
            c.partChannel(channel);
        }
        else { // Always remove channel (or try to), so it can be closed even if it bugged out
            Room room = roomManager.getRoom(channel);
            c.closeChannel(channel);
            closeChannelStuff(room);
            g.removeChannel(channel);
            chatLog.closeChannel(room.getFilename());
        }
    }

    private void closeChannelStuff(Room room) {
        // Check if not on any associated channel anymore
        // TODO stuff to close the channell eetc konasfojasfokjnfasasonfjasnas
    }


    public String getChannelID() {
        return c.getChannelID();
    }


    public User getUser(String channel, String channel_id, String name) {
        return c.getUser(channel, channel_id, name);
    }

    public User getExistingUser(String channel, String name) {
        return c.getExistingUser(channel, name);
    }

    public User getLocalUser(String channel) {
        return c.getExistingUser(channel, c.getChannelID());
    }

    public void clearUserList() {
        c.setAllOffline();
        // TODO another function.... not there... pls helpt his function it's disabled
        //g.clearUsers(null);
    }

    public boolean disconnect() {
        return c.disconnect();
    }

    public void joinChannels(Set<String> channels) {
        c.joinChannels(channels);
    }

    public void joinChannel(String channels) {
        c.joinChannel(channels);
    }

    public int getState() {
        return c.getState();
    }

    /**
     * Prepare connection using renametings and default server.
     *
     * @return
     */

    public boolean prepareConnection(boolean rejoinOpenChannels) {
        if (rejoinOpenChannels) {
            return prepareConnection(null);
        } else {
            return prepareConnection();
        }
    }

    public final boolean prepareConnectionWithChannel(String channel) {
        return prepareConnection(channel);
    }

    public boolean prepareConnection() {
        return prepareConnection(settings.getString("channel"));
    }

    public final boolean prepareConnectionAnyChannel() {
        String channel = null;
        if (c.getOpenChannels().isEmpty()) {
            channel = settings.getString("channel");
        }
        return prepareConnection(channel);
    }

    /**
     * Prepares the connection while getting everything from the renametings,
     * except the server/port.
     *
     * @param server
     * @param ports
     * @return
     */
    public boolean prepareConnection(String channel) {
        String username = settings.getString("username");
        String channel_id = settings.getString("userid");
        String cookies = settings.getString("cookies");

        return prepareChannelThreads(channel_id, username, channel, cookies);
    }

    /**
     * Prepares the connection to the given channel with the given credentials.
     *
     * This does stuff that should only be done once, unless the given parameters
     * change. So this shouldn't be repeated for just reconnecting.
     *
     * @param channel The channel(s) to join after connecting, if this is null
     * then it rejoins the currently open channels (if any)
     * @return true if no formal error occured, false otherwise
     */
    public boolean prepareChannelThreads(String channel_id, String name, String channel, String cookies) {
        if (c.getState() > YouTubeLiveChats.STATE_NONE) {
            g.showMessage("Cannot connect: Already connected.");
            return false;
        }

        if (name == null || name.isEmpty()) {
            g.showMessage("Cannot connect: Incomplete login data.");
            return false;
        }

        String[] autojoin;
        Set<String> openChannels = c.getOpenChannels();
        if (channel == null) {
            autojoin = new String[openChannels.size()];
            openChannels.toArray(autojoin);
        } else {
            autojoin = Helper.parseChannels(channel);
        }
        if (autojoin.length == 0) {
            g.showMessage("A channel to join has to be specified.");
            return false;
        }

        closeAllChannelsExcept(autojoin);

        settings.setString("username", name);
        if (channel != null) {
            settings.setString("channel", channel);
        }
        //api.requestUserId(Helper.toStream(autojoin));
//        api.getEmotesByStreams(Helper.toStream(autojoin)); // Removed
        c.autoJoinChannels(channel_id, name, autojoin);
        return true;
    }

    public ChannelState getChannelState(String channel) {
        return c.getChannelState(channel);
    }

    public Collection<String> getOpenChannels() {
        return c.getOpenChannels();
    }

    public Collection<Room> getOpenRooms() {
        return c.getOpenRooms();
    }

    /**
     * Get the currently open rooms, with a User object of the same username
     * attached to each room, if it already exists.
     *
     * @param user
     * @return A new List containing UserRoom objects of currently open rooms
     */
    public List<UserRoom> getOpenUserRooms(User user) {
        List<UserRoom> result = new ArrayList<>();
        Collection<Room> rooms = getOpenRooms();
        for (Room room : rooms) {
            User roomUser = c.getExistingUser(room.getChannel(), user.getName());
            result.add(new UserRoom(room, roomUser));
        }
        return result;
    }

    /**
     * Directly entered into the input box or entered by Custom Commands.
     *
     * This must be safe input (i.e. input directly by the local user) because
     * this can execute all kind of commands.
     *
     * @param room
     * @param commandParameters
     * @param text
     */
    public void textInput(Room room, String text, Parameters commandParameters) {
        if (text.isEmpty()) {
            return;
        }
        text = g.replaceEmojiCodes(text);
        String channel = room.getChannel();
        if (text.startsWith("//")) {
            anonCustomCommand(room, text.substring(1), commandParameters);
        }
        else if (text.startsWith("/")) {
            commandInput(room, text, commandParameters);
        }
        else {
            if (c.onChannel(channel)) {
                JSONArray segments = g.createTextSegments(text);
                sendMessage(channel, text, true, segments);
            }
            else if (channel.startsWith("*")) {

            }
            else {
                // For testing:
                // (Also creates a channel with an empty string)
                if (Chatty.DEBUG) {
                    User user = c.getUser(room.getChannel(), "test", "test");
                    g.printMessage(user,text,false);
                } else {
                    g.printLine("Not in a channel");
                }
            }
        }
    }

    private void sendMessage(String channel, String text) {
        sendMessage(channel, text, false);
    }

    /**
     *
     * @param channel
     * @param text
     * @param allowCommandMessageLocally Commands like !highlight, which
     * normally only working for received messages, will be triggered when
     * sending a message as well
     */
    private void sendMessage(String channel, String text, boolean allowCommandMessageLocally) {
        sendMessage(channel, text, allowCommandMessageLocally, null);
    }

    private void sendMessage(String channel, String text, boolean allowCommandMessageLocally, JSONArray segments) {
        if (c.sendSpamProtectedMessage(channel, text, false, segments)) {
            User user = c.localUserJoined(channel);
            g.printMessage(user, text, false);
            if (allowCommandMessageLocally) {
                //modCommandAddStreamHighlight(user, text, MsgTags.EMPTY);
            }
        } else {
            g.printLine("# Message not sent to prevent ban: " + text);
        }
    }

    /**
     * Checks if the given channel should be open.
     *
     * @param channel The channel name
     * @return
     */
    public boolean isChannelOpen(String channel) {
        return c.isChannelOpen(channel);
    }

    public boolean isUserlistLoaded(String channel) {
        // todo pls do something again sfasf
        //return c.isUserlistLoaded(channel);
        return false;
    }

    public String getHostedChannel(String channel) {
        return c.getChannelState(channel).getHosting();
    }

    /**
     * Execute a command from input, which means the text starts with a '/',
     * followed by the command name and comma-separated arguments.
     *
     * Use {@link #commandInput(Room, String, Parameters)} to carry over extra
     * parameters.
     *
     * @param room The room context
     * @param text The raw text
     * @return
     */
    public boolean commandInput(Room room, String text) {
        return commandInput(room, text, null);
    }

    /**
     * Execute a command from input, which means the text starts with a '/',
     * followed by the command name and comma-separated arguments.
     *
     * @param room The room context
     * @param text The raw text
     * @param parameters The parameters to carry over (args will be overwritten)
     * @return
     */
    public boolean commandInput(Room room, String text, Parameters parameters) {
        String[] split = text.split(" ", 2);
        String command = split[0].substring(1);
        String args = null;
        if (split.length == 2) {
            args = split[1];
        }

        // Overwrite args in Parameters with current
        if (parameters == null) {
            parameters = Parameters.create(args);
        } else {
            parameters.putArgs(args);
        }
        return command(room, command, parameters);
    }

    /**
     * Executes the command with the given name, which can be a built-in or
     * Custom command, with no parameters.
     *
     * @param room The room context
     * @param command The command name (no leading /)
     * @return
     */
    public boolean command(Room room, String command) {
        return command(room, command, Parameters.create(null));
    }

    /**
     * Executes the command with the given name, which can be a built-in or
     * Custom Command.
     *
     * @param room The room context
     * @param command The command name (no leading /)
     * @param parameter The parameter, can be null
     * @return
     */
    public boolean command(Room room, String command, String parameter) {
        return command(room, command, Parameters.create(parameter));
    }

    private void addCommands() {

    }

    /**
     * Executes the command with the given name, which can be a built-in or
     * Custom Command.
     *
     * @param room The room context
     * @param command The command name (no leading /)
     * @param parameters The parameters, can not be null
     * @return
     */
    public boolean command(Room room, String command, Parameters parameters) {
        String channel = room.getChannel();
        // Args could be null
        String parameter = parameters.getArgs();
        command = StringUtil.toLowerCase(command);

        if (commands.performCommand(command, room, parameters)) {
            LOGGER.info("??");
        }
        else if (c.command(channel, command, parameter, null)) {
            // Already done if true
        }
        else if (customCommands.containsCommand(command, room)) {

        }
        else {
            g.printLine(Language.getString("chat.unknownCommand", command));
            return false;
        }
        return false;
    }

    /**
     * Add a debugmessage to the GUI. If the GUI wasn't created yet, add it
     * to a cache that is send to the GUI once it is created. This is done
     * automatically when a debugmessage is added after the GUI was created.
     *
     * @param line
     */
    public void debug(String line) {
        if (shuttingDown) {
            return;
        }
        synchronized(cachedDebugMessages) {
            if (g == null) {
                cachedDebugMessages.add("["+DateTime.currentTimeExact()+"] "+line);
            } else {
                if (!cachedDebugMessages.isEmpty()) {
                    g.printDebug("[Start of cached messages]");
                    for (String cachedLine : cachedDebugMessages) {
                        g.printDebug(cachedLine);
                    }
                    g.printDebug("[End of cached messages]");
                    // No longer used
                    cachedDebugMessages.clear();
                }
                g.printDebug(line);
            }
        }
    }

    /**
     * Output a warning to the user, instead of the debug window.
     *
     * @param line
     */
    public final void warning(String line) {
        if (shuttingDown) {
            return;
        }
        synchronized(cachedWarningMessages) {
            if (g == null) {
                cachedWarningMessages.add(line);
            } else {
                if (!cachedWarningMessages.isEmpty()) {
                    for (String cachedLine : cachedWarningMessages) {
                        g.printLine(cachedLine);
                    }
                    cachedWarningMessages.clear();
                }
                if (line != null) {
                    g.printLine(line);
                }
            }
        }
    }


    /*
        Listeners
     */

    private class MyRoomUpdatedListener implements RoomManager.RoomUpdatedListener {

        @Override
        public void roomUpdated(Room room) {
            if (c != null) {
                c.updateRoom(room);
            }
            if (g != null) {
                g.updateRoom(room);
            }
        }

    }

    private class ChannelStateUpdater implements ChannelStateManager.ChannelStateListener {

        @Override
        public void channelStateUpdated(ChannelState state) {
            g.updateState(true);
        }

    }

    private class Messages implements YouTubeConnection.ConnectionListener {
        @Override
        public void onChannelJoined(User user) {
            channelFavorites.addJoined(user.getRoom());

            g.printLine(user.getRoom(), Language.getString("chat.joined", user.getRoom()));
            if (user.getRoom().hasTopic()) {
                g.printLine(user.getRoom(), user.getRoom().getTopicText());
            }

            // Icons and FFZ/BTTV Emotes
            //api.requestChatIcons(Helper.toStream(channel), false);
            String stream = user.getStream();
            if (Helper.isValidStream(stream)) {

            }
        }

        @Override
        public void onChannelLeft(Room room, boolean closeChannel) {
            chatLog.info(room.getFilename(), "You have left "+room.getDisplayName());
            if (closeChannel) {
                closeChannel(room.getChannel());
            }
            else {
                g.printLine(room, Language.getString("chat.left", room));
            }
        }

        @Override
        public void onUserUpdated(User user) {
            if (showUserInGui(user)) {
                g.updateUser(user);
            }
            g.updateUserinfo(user);
            //checkModLogListen(user);
        }

        @Override
        public void onChannelMessage(User user, String text, boolean action, MsgTags tags) {
            g.printMessage(user, text, action, tags);
            if (!action) {
                //addressbookCommands(user.getChannel(), user, text);
                //modCommandAddStreamHighlight(user, text, tags);
            }
        }

        @Override
        public void onChannelMessage(User user, String client_id, liveChatTextMessageRenderer messageRenderer) {
            g.printMessage(user, client_id, messageRenderer);
        }

        @Override
        public void onNotice(String message) {
            g.printLine("[Notice] "+message);
        }

        @Override
        public void onInfo(Room room, String infoMessage, MsgTags tags) {
            g.printInfo(room, infoMessage, tags);
        }

        @Override
        public void onInfo(String message) {
            g.printLine(message);
        }

        @Override
        public void onGlobalInfo(String message) {

        }

        @Override
        public void onJoin(User user) {
            if (settings.getBoolean("showJoinsParts") && showUserInGui(user)) {
                g.printCompact("JOIN", user);
            }
            g.userJoined(user);
            chatLog.compact(user.getRoom().getFilename(), "JOIN", user.getRegularDisplayNick());
        }

        @Override
        public void onJoinAttempt(Room room) {
            /**
             * This should be the event where the channel is first opened, and
             * the stream info should be output then. If the stream info is
             * already valid, then it is output now, otherwise it is requested
             * by this and output once it is received. Doing this later, like
             * onJoin, won't work because opening the channel will always
             * request stream info, so it might be output twice (once onJoin, a
             * second time because it is new).
             */
            if (!isChannelOpen(room.getChannel())) {
                //g.printStreamInfo(room);
            }
            g.printLine(room, Language.getString("chat.joining", room));
        }

        @Override
        public void onUserAdded(User user) {
            if (showUserInGui(user)) {
                g.addUser(user);
            }
            g.updateUserinfo(user);
        }

        private boolean showUserInGui(User user) {
            if (!settings.getBoolean("ignoredUsersHideInGUI")) {
                return true;
            }
            return !settings.listContains("ignoredUsers", user.getName());
        }

        @Override
        public void onUserRemoved(User user) {
            // TODO do whatever pls just do something with it thx
            //g.removeUser(user);
        }

        @Override
        public void onUserlistCleared(String channel) {

        }

        @Override
        public void onBan(User user, long duration, String reason, String targetMsgId) {
            User localUser = c.getLocalUser(user.getChannel());
            if (localUser != user && !localUser.hasModeratorRights()) {
                // Remove reason if not the affected user and not a mod, to be
                // consistent with other applications
                reason = "";
            }
            g.userBanned(user, duration, reason, targetMsgId);
            // TODO handle this
            //ChannelInfo channelInfo = api.getOnlyCachedChannelInfo(user.getName());
            //chatLog.userBanned(user.getRoom().getFilename(), user.getRegularDisplayNick(),
            //        duration, reason, channelInfo);
        }

        @Override
        public void onMsgDeleted(User user, String targetMsgId, String msg) {
            User localUser = c.getLocalUser(user.getChannel());
            if (localUser == user) {
                g.printLine(user.getRoom(), "Your message was deleted: "+msg);
            } else {
                g.msgDeleted(user, targetMsgId, msg);
            }
            chatLog.msgDeleted(user, msg);
        }

        @Override
        public void onMsgDeleted(User user, User.TextMessage message, String targetMsgId) {
            User localUser = c.getLocalUser(user.getChannel());
            if (localUser == user) {
                g.printLine(user.getRoom(), "Your message was deleted: " + message.getText());
            } else {
                g.msgDeleted(user, targetMsgId, message.getText());
            }
            chatLog.msgDeleted(user, message.getText());
        }

        @Override
        public void onRegistered() {
            g.updateHighlightSetUsername(c.getChannelID());
            //pubsub.listenModLog(c.getUsername(), settings.getString("token"));
        }

        @Override
        public void onMod(User user) {
            boolean modMessagesEnabled = settings.getBoolean("showModMessages");
            if (modMessagesEnabled && showUserInGui(user)) {
                g.printCompact("MOD", user);
            }
            chatLog.compact(user.getRoom().getFilename(), "MOD", user.getRegularDisplayNick());
        }

        @Override
        public void onUnmod(User user) {
            boolean modMessagesEnabled = settings.getBoolean("showModMessages");
            if (modMessagesEnabled && showUserInGui(user)) {
                g.printCompact("UNMOD", user);
            }
            chatLog.compact(user.getRoom().getFilename(), "UNMOD", user.getRegularDisplayNick());
        }

        @Override
        public void onDisconnect(int reason, String reasonMessage) {
            // TODO make something actually good from here LMAO or delete it thx
            //g.clearUsers();

        }

        @Override
        public void onConnectionStateChanged(int state) {
            g.updateState(true);
        }

        @Override
        public void onEmotesets(Set<String> emotesets) {
            //emotesetManager.setIrcEmotesets(emotesets);
        }

        @Override
        public void onConnectError(String message) {
            g.printLine(message);
        }

        @Override
        public void onJoinError(Set<String> toJoin, String errorChannel, YouTubeConnection.JoinError error) {
            if (error == YouTubeConnection.JoinError.ALREADY_JOINED) {
                if (toJoin.size() == 1) {
                    g.switchToChannel(errorChannel);
                } else {
                    g.printLine(Language.getString("chat.joinError.alreadyJoined", errorChannel));
                }
            } else if (error == YouTubeConnection.JoinError.INVALID_NAME) {
                g.printLine(Language.getString("chat.joinError.invalid", errorChannel));
            } else if (error == YouTubeConnection.JoinError.ROOM) {
                g.printLine(Language.getString("chat.joinError.rooms", errorChannel));
            }
        }

        @Override
        public void onRawReceived(String text) {

        }

        @Override
        public void onRawSent(String text) {

        }

        @Override
        public void onHost(Room room, String target) {

        }

        @Override
        public void onChannelCleared(Room room) {

        }

        @Override
        public void onSubscriberNotification(User user, String text, String message, int months, MsgTags tags) {

        }

        @Override
        public void onUsernotice(String type, User user, String text, String message, MsgTags tags) {

        }

        @Override
        public void onSpecialMessage(String name, String message) {

        }

        @Override
        public void onRoomId(String channel, String id) {

        }

        @Override
        public void receivedUsericons(List<Usericon> icons) {
            usericonManager.addDefaultIcons(icons);
        }

        @Override
        public void receivedModerationData(ModerationData data) {
            if(!data.created_by.isEmpty()) {
                g.printModerationAction(data, false);
                User modUser = c.getUserFromUsername(data.stream, data.created_by);
                modUser.addModAction(data);
                g.updateUserinfo(modUser);

                if(ModLogInfo.isBanCommand(data)) {
                    User bannedUser;
                    if(data.channel_id != null) {
                        bannedUser = c.getUser(data.stream, data.channel_id, data.username);
                    } else {
                        bannedUser = c.getUserFromUsername(data.stream, data.username);
                    }
                    if(bannedUser != null) {
                        bannedUser.addBanInfo(data);
                        g.updateUserinfo(bannedUser);
                    }
                }
            }
        }

        @Override
        public void receivedUsername(String username) {
            if(!c.getUsername().equalsIgnoreCase(username)) {
                g.updateUsername(username);
                c.setUsername(username);
            }
        }

        @Override
        public void receivedEmoticons(Set<Emoticon> emoticons) {
            g.addEmoticons(emoticons);
        }

    }

    /**
     * Redirects request results from the API.
     */
    private class YouTubeResults implements YouTubeApiResultListener {

        @Override
        public void cookiesVerified(String cookies, TokenInfo tokenInfo) {
            g.cookiesVerified(cookies, tokenInfo);
        }

        @Override
        public void tokenRevoked(String error) {

        }

        @Override
        public void accessDenied() {
            web.checkToken();
        }

        @Override
        public void receivedDisplayName(String name, String displayName) {

        }

        @Override
        public void receivedServer(String channel, String server) {

        }

        @Override
        public void followResult(String message) {

        }

        @Override
        public void autoModResult(String result, String msgId) {

        }
    }


    private void anonCustomCommand(Room room, String text, Parameters parameters) {
        CustomCommand command = CustomCommand.parse(text);
        if (parameters == null) {
            parameters = Parameters.create(null);
        }
        anonCustomCommand(room, command, parameters);
    }

    public void anonCustomCommand(Room room, CustomCommand command, Parameters parameters) {
        if (command.hasError()) {
            g.printLine("Parse error: "+command.getSingleLineError());
            return;
        }
        if (room == null) {
            g.printLine("Custom command: Not on a channel");
            return;
        }
        String result = customCommands.command(command, parameters, room);
        if (result == null) {
            g.printLine("Custom command: Insufficient parameters/data");
        } else if (result.isEmpty()) {
            g.printLine("Custom command: No action specified");
        } else {
            textInput(room, result, parameters);
        }
    }



    /**
     * Exit the program. Do some cleanup first and save stuff to file (settings,
     * addressbook, chatlogs).
     *
     * Should run in EDT.
     */
    public void exit() {
        shuttingDown = true;
        saveSettings(true, false);
        //logAllViewerstats();
        c.disconnect();
        g.cleanUp();
        chatLog.close();
        System.exit(0);
    }

    /**
     * Save all settings to file.
     *
     * @param onExit If true, this will save the settings only if they haven't
     * already been saved with this being true before
     */
    public List<FileManager.SaveResult> saveSettings(boolean onExit, boolean force) {
        if (onExit) {
            if (settingsAlreadySavedOnExit) {
                return null;
            }
            settingsAlreadySavedOnExit = true;
        }

        // Prepare saving settings
        if (g != null && g.guiCreated) {
            g.saveWindowStates();
        }
        // Actually write settings to file
        if (force || !settings.getBoolean("dontSaveSettings")) {
            LOGGER.info("Saving settings..");
            System.out.println("Saving settings..");
            return settings.saveSettingsToJson(force);
        }
        else {
            LOGGER.info("Not saving settings (disabled)");
        }
        return null;
    }

    public List<FileManager.SaveResult> manualBackup() {
        return settingsManager.fileManager.manualBackup();
    }


}
