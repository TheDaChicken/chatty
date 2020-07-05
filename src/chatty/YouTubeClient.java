package chatty;

import chatty.gui.GuiUtil;
import chatty.gui.LaF;
import chatty.gui.MainGui;
import chatty.gui.colors.UsercolorManager;
import chatty.gui.components.menus.UserContextMenu;
import chatty.lang.Language;
import chatty.splash.Splash;
import chatty.util.DateTime;
import chatty.util.UserRoom;
import chatty.util.Webserver;
import chatty.util.api.*;
import chatty.util.api.usericons.Usericon;
import chatty.util.api.usericons.UsericonManager;
import chatty.util.chatlog.ChatLog;
import chatty.util.commands.CustomCommands;
import chatty.util.commands.Parameters;
import chatty.util.irc.MsgTags;
import chatty.util.settings.FileManager;
import chatty.util.settings.Settings;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.services.youtube.YouTube;

import java.util.*;
import java.util.logging.Logger;


/**
 * The main client class, responsible for managing most parts of the program.
 *
 * @author TheDaChicken
 */
public class YouTubeClient {

    private static final Logger LOGGER = Logger.getLogger(YouTubeClient.class.getName());

    private volatile boolean shuttingDown = false;
    private volatile boolean settingsAlreadySavedOnExit = false;

    /**
     * The URL to get a token. Needs to end with the scopes so other ones can be
     * added.
     */
    public static final String REQUEST_TOKEN_URL = GoogleOAuthConstants.AUTHORIZATION_SERVER_URL;

    /**
     * Holds the Settings object, which is used to store and retrieve renametings
     */
    public final Settings settings;

    public final ChatLog chatLog;

    private final YouTubeConnection c;

    public final ChannelFavorites channelFavorites;

    public final RoomManager roomManager;

    /**
     * Holds the TwitchApi object, which is used to make API requests
     */
    public final YouTubeApi api;

    /**
     * A reference to the Main Gui.
     */
    protected MainGui g;

    private final List<String> cachedDebugMessages = new ArrayList<>();
    private final List<String> cachedWarningMessages = new ArrayList<>();

    private Webserver webserver;
    private final SettingsManager settingsManager;
    public final CustomCommands customCommands;
    //public final Commands commands = new Commands();

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

        api = new YouTubeApi(new TwitchApiResults());

        customCommands = new CustomCommands(settings, api, this);
        customCommands.loadFromSettings();

        usercolorManager = new UsercolorManager(settings);
        usericonManager = new UsericonManager(settings);

        customNames = new CustomNames(settings);

        chatLog = new ChatLog(settings);
        chatLog.start();

        roomManager = new RoomManager(new MyRoomUpdatedListener());
        channelFavorites = new ChannelFavorites(settings, roomManager);

        c = new YouTubeConnection(new Messages(), settings, "main", roomManager, api);
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
        //emotesetManager = new EmotesetManager(api, g, settings);
        g.showGui();
    }

    public void init() {
        LOGGER.info("GUI shown");
        Splash.closeSplashScreen();

        // Output any cached warning messages
        warning(null);

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
            // TODO 1
            //g.saveWindowStates();
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

    public Collection<String> getOpenChannels() {
        return null;
    }

    public User getExistingUser(String channel, String name) {
        return c.getExistingUser(channel, name);
    }

    public User getLocalUser(String channel) {
        return c.getExistingUser(channel, c.getChannelID());
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
            //logViewerstats(channel);
            c.closeChannel(channel);
            //closeChannelStuff(room);
            g.removeChannel(channel);
            chatLog.closeChannel(room.getFilename());
        }
    }


    public String getChannelID() {
        return c.getChannelID();
    }


    /**
     * Redirects request results from the API.
     */
    private class TwitchApiResults implements TwitchApiResultListener {

        @Override
        public void receivedUsericons(List<Usericon> icons) {

        }

        @Override
        public void tokenVerified(GoogleCredential credential, TokenInfo tokenInfo) {
            g.tokenVerified(credential, tokenInfo);
        }

        @Override
        public void tokenRevoked(String error) {

        }

        @Override
        public void runCommercialResult(String stream, String text, YouTubeApi.RequestResultCode result) {

        }

        @Override
        public void putChannelInfoResult(YouTubeApi.RequestResultCode result) {

        }

        @Override
        public void receivedChannelInfo(String channel, ChannelInfo info, YouTubeApi.RequestResultCode result) {

        }

        @Override
        public void accessDenied() {

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

    public Collection<Room> getOpenRooms() {
        return new HashSet<>();
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
            //User roomUser = c.getExistingUser(room.getChannel(), user.getName());
            //result.add(new UserRoom(room, roomUser));
        }
        return result;
    }

    private class ChannelStateUpdater implements ChannelStateManager.ChannelStateListener {

        @Override
        public void channelStateUpdated(ChannelState state) {
            g.updateState(true);
        }

    }

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

    public List<FileManager.SaveResult> manualBackup() {
        return settingsManager.fileManager.manualBackup();
    }

    private class Messages implements YouTubeConnection.ConnectionListener {
        @Override
        public void onJoinAttempt(Room room) {
            if (!isChannelOpen(room.getChannel())) {
                //g.printStreamInfo(room);
            }
            g.printLine(room, Language.getString("chat.joining", room));
        }

        @Override
        public void onChannelJoined(User user) {
            channelFavorites.addJoined(user.getRoom());

            g.printLine(user.getRoom(), Language.getString("chat.joined", user.getRoom()));
            if (user.getRoom().hasTopic()) {
                g.printLine(user.getRoom(), user.getRoom().getTopicText());
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
        public void onJoin(User user) {
            LOGGER.info("onJoin();");
        }

        @Override
        public void onPart(User user) {
            LOGGER.info("onPart();");
        }

        @Override
        public void onUserAdded(User user) {

        }

        @Override
        public void onUserRemoved(User user) {

        }

        @Override
        public void onUserlistCleared(String channel) {
            LOGGER.info("onUserlistCleared();");
        }

        @Override
        public void onUserUpdated(User user) {

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
        public void onWhisper(User user, String message, String emotes) {

        }

        @Override
        public void onNotice(String message) {

        }

        @Override
        public void onInfo(Room room, String infoMessage, MsgTags tags) {
            LOGGER.info("onInfo();");
        }

        @Override
        public void onInfo(String infoMessage) {
            LOGGER.info("onInfo x();");
        }

        @Override
        public void onGlobalInfo(String message) {

        }

        @Override
        public void onBan(User user, long length, String reason, String targetMsgId) {

        }

        @Override
        public void onMsgDeleted(User user, String targetMsgId, String msg) {

        }

        @Override
        public void onRegistered() {

        }

        @Override
        public void onDisconnect(int reason, String reasonMessage) {

        }

        @Override
        public void onMod(User user) {

        }

        @Override
        public void onUnmod(User user) {

        }

        @Override
        public void onConnectionStateChanged(int state) {
            LOGGER.info("onConnectionStateChanged();");
            g.updateState(true);
        }

        @Override
        public void onEmotesets(Set<String> emotesets) {

        }

        @Override
        public void onConnectError(String message) {

        }

        @Override
        public void onJoinError(Set<String> toJoin, String errorChannel, YouTubeConnection.JoinError error) {

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

    }

    // Webserver

    public void startWebserver() {
        if (webserver == null) {
            webserver = new Webserver(new WebserverListener());
            new Thread(webserver).start();
        }
        else {
            LOGGER.warning("Webserver already running");
            // When webserver is already running, it should be started
            g.webserverStarted();
        }
    }

    public void stopWebserver() {
        if (webserver != null) {
            webserver.stop();
        }
        else {
            LOGGER.info("No webserver running, can't stop it");
        }
    }

    private class WebserverListener implements Webserver.WebserverListener {

        @Override
        public void webserverStarted() {
            g.webserverStarted();
        }

        @Override
        public void webserverStopped() {
            webserver = null;
        }

        @Override
        public void webserverError(String error) {
            g.webserverError(error);
            webserver = null;
        }

        @Override
        public void webserverCodeReceived(String code) {
            LOGGER.info(code);
            GoogleCredential credential = YouTubeAuth.receiveCredential(code);
            g.webserverCodeReceived(credential);
        }
    };

    private String getServer() {
        String serverDefault = settings.getString("serverDefault");
        String serverTemp = settings.getString("server");
        return serverTemp.length() > 0 ? serverTemp : serverDefault;
    }

    private String getPorts() {
        String portDefault = settings.getString("portDefault");
        String portTemp = settings.getString("port");
        return portTemp.length() > 0 ? portTemp : portDefault;
    }

    /**
     * Prepare connection using renametings and default server.
     *
     * @return
     */
    public final boolean prepareConnection() {
        return prepareConnection(getServer(), getPorts());
    }

    public boolean prepareConnection(boolean rejoinOpenChannels) {
        if (rejoinOpenChannels) {
            return prepareConnection(getServer(), getPorts(), null);
        } else {
            return prepareConnection();
        }
    }

    public final boolean prepareConnectionWithChannel(String channel) {
        return prepareConnection(getServer(), getPorts(), channel);
    }

    public boolean prepareConnection(String server, String ports) {
        return prepareConnection(server, ports, settings.getString("channel"));
    }

    public final boolean prepareConnectionAnyChannel(String server, String ports) {
        String channel = null;
        if (c.getOpenChannels().isEmpty()) {
            channel = settings.getString("channel");
        }
        return prepareConnection(server, ports, null);
    }

    /**
     * Prepares the connection while getting everything from the renametings,
     * except the server/port.
     *
     * @param server
     * @param ports
     * @return
     */
    public boolean prepareConnection(String server, String ports, String channel) {
        String username = settings.getString("username");
        String password = settings.getString("password");
        boolean usePassword = settings.getBoolean("usePassword");
        String token = settings.getString("tokens");

        return prepareConnection(username,null, channel,server, ports);
    }

    /**
     * Prepares the connection to the given channel with the given credentials.
     *
     * This does stuff that should only be done once, unless the given parameters
     * change. So this shouldn't be repeated for just reconnecting.
     *
     * @param name The username to use for connecting.
     * @param password The password to connect with.
     * @param channel The channel(s) to join after connecting, if this is null
     * then it rejoins the currently open channels (if any)
     * @param server The server to connect to.
     * @param ports The port to connect to.
     * @return true if no formal error occured, false otherwise
     */
    public boolean prepareConnection(String name, String password,
                                     String channel, String server, String ports) {
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

        if (server == null || server.isEmpty()) {
            g.showMessage("Invalid server specified.");
            return false;
        }

        settings.setString("username", name);
        if (channel != null) {
            settings.setString("channel", channel);
        }

        c.connect(server, ports, name, password, autojoin);
        return true;
    }

    public boolean disconnect() {
        return c.disconnect();
    }

    public void joinChannels(Set<String> channels) {
        LOGGER.info("joinChannels();");
        c.joinChannels(channels);
    }


    public void joinChannel(String channels) {
        LOGGER.info("joinChannel();");
        c.joinChannel(channels);
    }

    public int getState() {
        return c.getState();
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


    public String getHostedChannel(String channel) {
        return c.getChannelState(channel).getHosting();
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
     * Exit the program. Do some cleanup first and save stuff to file (settings,
     * addressbook, chatlogs).
     *
     * Should run in EDT.
     */
    public void exit() {
        shuttingDown = true;
        saveSettings(true, false);
        g.cleanUp();
        chatLog.close();
        System.exit(0);
    }
}
