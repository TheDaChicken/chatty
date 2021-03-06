package chatty.gui;

import chatty.*;
import chatty.gui.colors.ColorItem;
import chatty.gui.colors.MsgColorItem;
import chatty.gui.colors.MsgColorManager;
import chatty.gui.colors.UsercolorItem;
import chatty.gui.components.*;
import chatty.gui.components.eventlog.EventLog;
import chatty.gui.components.help.About;
import chatty.gui.components.menus.ContextMenuListener;
import chatty.gui.components.menus.EmoteContextMenu;
import chatty.gui.components.menus.StreamChatContextMenu;
import chatty.gui.components.menus.TextSelectionMenu;
import chatty.gui.components.settings.NotificationSettings;
import chatty.gui.components.settings.SettingsDialog;
import chatty.gui.components.textpane.InfoMessage;
import chatty.gui.components.textpane.ModLogInfo;
import chatty.gui.components.textpane.UserMessage;
import chatty.gui.components.textpane.UserNotice;
import chatty.gui.components.userinfo.UserInfoManager;
import chatty.gui.notifications.Notification;
import chatty.gui.notifications.NotificationActionListener;
import chatty.gui.notifications.NotificationManager;
import chatty.gui.notifications.NotificationWindowManager;
import chatty.lang.Language;
import chatty.util.*;
import chatty.util.api.*;
import chatty.util.api.usericons.Usericon;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatTextMessageRenderer;
import chatty.util.api.youtubeObjects.ModerationData;
import chatty.util.commands.CustomCommand;
import chatty.util.commands.Parameters;
import chatty.util.hotkeys.HotkeyManager;
import chatty.util.irc.MsgTags;
import chatty.util.settings.FileManager;
import chatty.util.settings.Setting;
import chatty.util.settings.SettingChangeListener;
import chatty.util.settings.Settings;
import org.json.simple.JSONArray;

import java.awt.*;
import java.awt.event.*;
import java.net.HttpCookie;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

public class MainGui extends JFrame implements Runnable {

    public final Emoticons emoticons = new Emoticons();

    // Reference back to the client to give back data etc.
    YouTubeClient client = null;

    public volatile boolean guiCreated;

    // Parts of the GUI
    private Channels channels;
    private ConnectionDialog connectionDialog;
    private TokenDialog tokenDialog;
    private DebugWindow debugWindow;
    private UserInfoManager userInfoDialog;
    private CookiesPasteDialog cookiesPasteDialog;
    private About aboutDialog;
    private SettingsDialog settingsDialog;
    private HighlightedMessages highlightedMessages;
    private HighlightedMessages ignoredMessages;
    private NotificationManager notificationManager;
    private NotificationWindowManager<String> notificationWindowManager;
    private ErrorMessage errorMessage;
    private EventLog eventLog;
    private MainMenu menu;
    private StreamChat streamChat;
    private EmotesDialog emotesDialog;

    // Helpers
    private final Highlighter highlighter = new Highlighter();
    private final Highlighter ignoreList = new Highlighter();
    private final Highlighter filter = new Highlighter();
    private final MsgColorManager msgColorManager;
    private StyleManager styleManager;
    private TrayIconManager trayIcon;
    private final StateUpdater state = new StateUpdater();
    private WindowStateManager windowStateManager;
    private final IgnoredMessages ignoredMessagesHelper = new IgnoredMessages(this);
    public final HotkeyManager hotkeyManager = new HotkeyManager(this);

    // Listeners that need to be returned by methods
    private ActionListener actionListener;
    private final WindowListener windowListener = new MyWindowListener();
    private final UserListener userListener = new MyUserListener();
    private final LinkLabelListener linkLabelListener = new MyLinkLabelListener();
    private final ContextMenuListener contextMenuListener = new MyContextMenuListener();

    public MainGui(YouTubeClient client) {
        this.client = client;
        msgColorManager = new MsgColorManager(client.settings);
        SwingUtilities.invokeLater(this);
    }

    @Override
    public void run() {
        createGui();
    }

    private Image createImage(String name) {
        return Toolkit.getDefaultToolkit().createImage(getClass().getResource(name));
    }

    /**
     * Sets different sizes of the window icon.
     */
    private void setWindowIcons() {
        ArrayList<Image> windowIcons = new ArrayList<>();
        windowIcons.add(createImage("app_main_16.png"));
        windowIcons.add(createImage("app_main_64.png"));
        windowIcons.add(createImage("app_main_128.png"));
        setIconImages(windowIcons);
    }

    private void setHelpWindowIcons() {
        ArrayList<Image> windowIcons = new ArrayList<>();
        windowIcons.add(createImage("app_help_16.png"));
        windowIcons.add(createImage("app_help_64.png"));
        windowIcons.add(createImage("app_help_128.png"));
        aboutDialog.setIconImages(windowIcons);
    }

    private void setDebugWindowIcons() {
        ArrayList<Image> windowIcons = new ArrayList<>();
        windowIcons.add(createImage("app_debug_16.png"));
        windowIcons.add(createImage("app_debug_64.png"));
        windowIcons.add(createImage("app_debug_128.png"));
        debugWindow.setIconImages(windowIcons);
    }

    /**
     * Creates the gui, run in the EDT.
     */
    private void createGui() {

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setWindowIcons();

        actionListener = new MyActionListener();
        TextSelectionMenu.listener = contextMenuListener;

        // Error/debug stuff
        debugWindow = new DebugWindow(new DebugCheckboxListener());
        errorMessage = new ErrorMessage(this, linkLabelListener);

        // Dialogs and stuff
        connectionDialog = new ConnectionDialog(this);
        GuiUtil.installEscapeCloseOperation(connectionDialog);
        tokenDialog = new TokenDialog(this);
        cookiesPasteDialog = new CookiesPasteDialog(this);
        userInfoDialog = new UserInfoManager(this, client.settings, contextMenuListener);
        EmoteContextMenu.setEmoteManager(emoticons);
        emotesDialog = new EmotesDialog(this, emoticons, this, contextMenuListener);
        GuiUtil.installEscapeCloseOperation(emotesDialog);
        aboutDialog = new About();
        setHelpWindowIcons();

        // Tray/Notifications
        trayIcon = new TrayIconManager();
        trayIcon.addActionListener(new TrayMenuListener());
        if (client.settings.getBoolean("trayIconAlways")) {
            trayIcon.setIconVisible(true);
        }

        notificationWindowManager = new NotificationWindowManager<>(this);
        notificationWindowManager.setNotificationActionListener(new MyNotificationActionListener());
        notificationManager = new NotificationManager(this, client.settings, client.addressbook, client.channelFavorites);

        // Channels/Chat output
        styleManager = new StyleManager(client.settings);
        highlightedMessages = new HighlightedMessages(this, styleManager,
                Language.getString("highlightedDialog.title"),
                Language.getString("highlightedDialog.info"),
                contextMenuListener);
        ignoredMessages = new HighlightedMessages(this, styleManager,
                Language.getString("ignoredDialog.title"),
                Language.getString("ignoredDialog.info"),
                contextMenuListener);
        channels = new Channels(this,styleManager, contextMenuListener);
        channels.getComponent().setPreferredSize(new Dimension(600,300));
        add(channels.getComponent(), BorderLayout.CENTER);
        channels.setChangeListener(new ChannelChangeListener());

        client.settings.addSettingChangeListener(new MySettingChangeListener());
        //client.settings.addSettingsListener(new MySettingsListener());

        streamChat = new StreamChat(this, styleManager, contextMenuListener,
                client.settings.getBoolean("streamChatBottom"));
        StreamChatContextMenu.client = client;

        eventLog = new EventLog(this);

        getSettingsDialog();

        // Main Menu
        MainMenuListener menuListener = new MainMenuListener();
        menu = new MainMenu(menuListener, menuListener);
        setJMenuBar(menu);

        addListeners();
        pack();

        ChattyMisc.request();

        // Window statuses
        windowStateManager = new WindowStateManager(this, client.settings);
        windowStateManager.addWindow(this, "main", true, true);
        windowStateManager.addWindow(emotesDialog, "emotes", true, true);
        windowStateManager.addWindow(streamChat, "streamChat", true, true);
        windowStateManager.addWindow(eventLog, "eventLog", true, true);

        if (System.getProperty("java.version").equals("1.8.0_161")
                || System.getProperty("java.version").equals("1.8.0_162")) {
            GuiUtil.installTextComponentFocusWorkaround();
        }

        ToolTipManager.sharedInstance().setInitialDelay(555);
        ToolTipManager.sharedInstance().setDismissDelay(20*1000);

        guiCreated = true;
    }

    public void setWindowAttached(Window window, boolean attached) {
        windowStateManager.setWindowAttached(window, attached);
    }

    protected void popoutCreated(JDialog popout) {
        hotkeyManager.registerPopout(popout);
    }

    private SettingsDialog getSettingsDialog() {
        if (settingsDialog == null) {
            settingsDialog = new SettingsDialog(this,client.settings);
        }
        return settingsDialog;
    }


    private void addListeners() {
        WindowManager manager = new WindowManager(this);
        //manager.addWindowOnTop(liveStreamsDialog);
        MainWindowListener mainWindowListener = new MainWindowListener();
        addWindowStateListener(mainWindowListener);
        addWindowListener(mainWindowListener);
        addWindowFocusListener(new WindowFocusListener() {

            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (client.settings.getLong("inputFocus") == 1) {
                    channels.setInitialFocus();
                }
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
            }
        });


        hotkeyManager.registerAction("window.toggleCompact", "Window: Toggle Compact Mode", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                toggleCompact(false);
            }
        });

        hotkeyManager.registerAction("window.toggleCompactMaximized", "Window: Toggle Compact Mode (Maximized)", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                toggleCompact(true);
            }
        });
    }

    /**
     * Toggle the main menubar. Also toggle between maximized/normal if maximize
     * is true.
     *
     * @param maximize If true, also toggle between maximized/normal
     */
    public void toggleCompact(final boolean maximize) {
        if (!isVisible()) {
            return;
        }
        final boolean hide = getJMenuBar() != null;

        //menu.setVisible(!hide);
        if (hide) {
            setJMenuBar(null);
        } else {
            setJMenuBar(menu);
            /**
             * Seems like adding the menubar adds the default F10 hotkey again
             * (that opens the menu), so refresh custom hotkeys in case one of
             * them uses F10.
             */
            hotkeyManager.refreshHotkeys(getRootPane());
        }
        revalidate();
        if (maximize) {
            if (hide) {
                setExtendedState(MAXIMIZED_BOTH);
            } else {
                setExtendedState(NORMAL);
            }
        }
    }

    public void showGui() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                if (!guiCreated) {
                    return;
                }

                startUpdatingState();

                channels.setInitialFocus();

                windowStateManager.loadWindowStates();
                windowStateManager.setWindowPosition(MainGui.this);
                setVisible(true);

                // If not invokeLater() seemed to move dialogs on start when
                // maximized and not restoring location due to off-screen
                SwingUtilities.invokeLater(() -> {
                    windowStateManager.setAttachedWindowsEnabled(client.settings.getBoolean("attachedWindows"));
                });

                // Should be done when the main window is already visible, so
                // it can be centered on it correctly, if that is necessary
                reopenWindows();

                //newsDialog.autoRequestNews(true);

                client.init();
            }
        });
    }

    /**
     * Bring the main window into view by bringing it out of minimization (if
     * necessary) and bringing it to the front.
     */
    private void makeVisible() {
        // Set visible was required to show it again after being minimized to tray
        setVisible(true);
        setState(NORMAL);
        toFront();
        //cleanupAfterRestoredFromTray();
        //setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    /**
     * Loads settings
     */
    public void loadSettings() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (guiCreated) {
                    loadSettingsInternal();
                }
            }
        });
    }

    /**
     * Initiates the GUI with settings
     */
    private void loadSettingsInternal() {
        /*
            TODO finish this up
         */
        if (client.settings.getBoolean("bufferStrategy1")) {
            createBufferStrategy(1);
        }

        setAlwaysOnTop(client.settings.getBoolean("ontop"));
        setResizable(client.settings.getBoolean("mainResizable"));
        streamChat.setResizable(client.settings.getBoolean("streamChatResizable"));

        loadMenuSettings();
        updateConnectionDialog(null);
        userInfoDialog.setUserDefinedButtonsDef(client.settings.getString("timeoutButtons"));

        // Set window maximized state
        if (client.settings.getBoolean("maximized")) {
            setExtendedState(MAXIMIZED_BOTH);
        }
        updateHighlight();

        emoticons.setIgnoredEmotes(client.settings.getList("ignoredEmotes"));
        emoticons.loadFavoritesFromSettings(client.settings);
        emoticons.loadCustomEmotes();
        emoticons.addEmoji(client.settings.getString("emoji"));

        String cookies = client.settings.getString("cookies");
        client.web.setCookies(cookies);

        emotesDialog.setEmoteScale((int)client.settings.getLong("emoteScaleDialog"));
        emotesDialog.setCloseOnDoubleClick(client.settings.getBoolean("closeEmoteDialogOnDoubleClick"));

        Sound.setDeviceName(client.settings.getString("soundDevice"));

    }

    public void startUpdatingState() {
        state.update(false);
        javax.swing.Timer timer = new javax.swing.Timer(5000, e -> {
            state.update(false);
        });
        timer.setRepeats(true);
        timer.start();
    }

    private static final String[] menuBooleanSettings = new String[]{
            "showJoinsParts", "ontop", "showModMessages", "attachedWindows",
            "simpleTitle", "globalHotkeysEnabled", "mainResizable", "streamChatResizable",
            "titleShowUptime", "titleShowViewerCount", "titleShowChannelState",
            "titleLongerUptime", "titleConnections"
    };

    /**
     * Initiates the Main Menu with settings
     */
    private void loadMenuSettings() {
        for (String setting : menuBooleanSettings) {
            loadMenuSetting(setting);
        }
    }

    /**
     * Initiates a single setting in the Main Menu
     * @param name The name of the setting
     */
    private void loadMenuSetting(String name) {
        menu.setItemState(name,client.settings.getBoolean(name));
    }

    /**
     * Tells the highlighter the current list of highlight-items from the settings.
     */
    private void updateHighlight() {
        highlighter.update(StringUtil.getStringList(client.settings.getList("highlight")));
        highlighter.updateBlacklist(StringUtil.getStringList(client.settings.getList("highlightBlacklist")));
    }

    /**
     * Should be thread-safe.
     *
     * @param input
     * @return
     */
    public String replaceEmojiCodes(String input) {
        if (client.settings.getBoolean("emojiReplace")) {
            return emoticons.emojiReplace(input);
        }
        return input;
    }

    public JSONArray createTextSegments(String input) {
        return emoticons.createJsonText(input);
    }

    class MyActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            // text input
            Channel chan = channels.getChannelFromInput(event.getSource());
            if (chan != null) {
                client.textInput(chan.getRoom(), chan.getInputText(), null);
            }

            Object source = event.getSource();
            //---------------------------
            // Connection Dialog actions
            //---------------------------

            if (source == connectionDialog.getCancelButton()) {
                connectionDialog.setVisible(false);
                channels.setInitialFocus();
            } else if (source == connectionDialog.getConnectButton()
                    || source == connectionDialog.getChannelInput()) {
                String channel = connectionDialog.getChannel();
                client.settings.setString("channel", channel);
                // Just autojoins channels with without any checking.
                if (client.prepareConnection(connectionDialog.rejoinOpenChannels())) {
                    connectionDialog.setVisible(false);
                    channels.setInitialFocus();
                }
                connectionDialog.setVisible(false);
                channels.setInitialFocus();
            } else if (event.getSource() == connectionDialog.getGetTokenButton()) {
                openTokenDialog();
            } else if (event.getSource() == connectionDialog.getFavoritesButton()) {
                //openFavoritesDialogFromConnectionDialog(connectionDialog.getChannel());
            } //---------------------------
            // Token Dialog actions
            //---------------------------
            else if (event.getSource() == tokenDialog.getDeleteTokenButton()) {
                int result = JOptionPane.showOptionDialog(tokenDialog,
                        "<html><body style='width:400px'>"
                                + Language.getString("login.removeLogin")
                                + "<ul>"
                                + "<li>"+Language.getString("login.removeLogin.revoke")
                                + "<li>"+Language.getString("login.removeLogin.remove")
                                + "</ul>"
                                + Language.getString("login.removeLogin.note"),
                        Language.getString("login.removeLogin.title"),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{Language.getString("login.removeLogin.button.revoke"),
                                Language.getString("login.removeLogin.button.remove"),
                                Language.getString("dialog.button.cancel")},
                        Language.getString("login.removeLogin.button.revoke"));
                if (result == 0) {
                    //client.api.revokeToken(client.settings.getString("token"));
                }
                if (result == 0 || result == 1) {
                    client.settings.setString("cookies", "");
                    client.settings.setBoolean("foreignToken", false);
                    client.settings.setString("username", "");
                    client.settings.setString("userid", "");
                    client.settings.listClear("scopes");
                    updateConnectionDialog(null);
                    tokenDialog.update("", null);
                }
            } else if (event.getSource() == tokenDialog.getRequestTokenButton()) {
                cookiesPasteDialog.setLocationRelativeTo(tokenDialog);
                cookiesPasteDialog.ready();
                cookiesPasteDialog.setVisible(true);

            } else if (event.getSource() == tokenDialog.getDoneButton()) {
                tokenDialog.setVisible(false);
            } else if (event.getSource() == tokenDialog.getVerifyTokenButton()) {
                String cookies = client.settings.getString("cookies");
                try {
                    verifyCookies(cookies, Cookies.loadCookies(cookies));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } // Get token Dialog
            else if (event.getSource() == cookiesPasteDialog.getCloseButton()) {
                tokenGetDialogClosed();
            } else if(event.getSource() == cookiesPasteDialog.getSaveButton()) {
                cookiesReceived(cookiesPasteDialog.getJsonString());
            }
        }
    }


    /**
     * Verify the given Token. This sends a request to the TwitchAPI.
     *
     * @param cookies
     */
    private void verifyCookies(String cookies, List<HttpCookie> parsed_cookies) {
        // TODO do stuff with verify token to get channel id & username of logged in either with cookies etc
        client.web.verifyCookies(cookies, parsed_cookies);
        tokenDialog.verifyingToken();
    }

    public void cookiesVerified(final String json_cookies, final TokenInfo tokenInfo) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                cookiesVerifiedInternal(json_cookies, tokenInfo);
            }
        });
    }

    private String manuallyChangedToken = null;

    /**
     * This does the main work when a response for verifying the token is
     * received from the Twitch API.
     *
     * A Token can be verified manually by pressing the button or automatically
     * when a new Token was received by the webserver. So when this is called
     * the original source can be both.
     *
     * The tokenGetDialog is closed if necessary.
     *
     * @param credential The token that was verified
     * @param username The usernamed that was received for this token. If this
     *      is null then an error occured, if it is empty then the token was
     *      invalid.
     */
    private void cookiesVerifiedInternal(String json_cookies, TokenInfo tokenInfo) {
        // Stopping the webserver here, because it allows the /tokenreceived/
        // page to be delievered, because of the delay of verifying the token.
        // This should probably be solved better.

        String result;
        String currentUsername = client.settings.getString("username");
        // Check if a new token was requested (the get token dialog should still
        // be open at this point) If this is wrong, it just displays the wrong
        // text, this shouldn't be used for something critical.
        boolean getNewLogin = cookiesPasteDialog.isVisible();
        boolean showInDialog = tokenDialog.isVisible();
        boolean changedTokenResponse = Objects.equals(json_cookies, manuallyChangedToken);
        boolean valid = false;
        if (tokenInfo == null) {
            // An error occured when verifying the token
            if (getNewLogin) {
                result = "An error occured completing getting login data.";
            }
            else {
                result = "An error occured verifying login data.";
            }
        }
        else if (!tokenInfo.valid) {
            // There was an answer when verifying the token, but it was invalid
            if (getNewLogin) {
                result = "Invalid token received when getting login data. Please "
                        + "try again.";
                client.settings.setString("cookies", "");
            }
            else if (changedTokenResponse) {
                result = "Invalid token entered. Please try again.";
                client.settings.setString("cookies", "");
            }
            else {
                result = "Login data invalid. [help:login-invalid What does this mean?]";
            }
            if (!showInDialog && !changedTokenResponse) {
                showTokenWarning();
            }
        }
        else {
            // Everything is fine, so save username and token
            valid = true;
            String username = tokenInfo.channel_name;
            if(username == null) {
                username = tokenInfo.channel_id;
            }
            client.settings.setString("username", username);
            client.settings.setString("userid", tokenInfo.channel_id);
            client.settings.setString("cookies", json_cookies);
            tokenDialog.update(username, json_cookies);
            updateConnectionDialog(null);
            if (!currentUsername.isEmpty() && !username.equals(currentUsername)) {
                result = "Login verified and ready to connect (replaced '" +
                        currentUsername + "' with '" + username + "').";
            }
            else {
                result = "Login verified and ready to connect.";
            }
        }
        if (changedTokenResponse) {
            printLine(result);
            manuallyChangedToken = null;
        }
        // Always close the get token dialog, if it's not open, nevermind ;)
        cookiesPasteDialog.setVisible(false);
        // Show result in the token dialog
        tokenDialog.tokenVerified(valid, result);
    }

    public void updateUsername(String username) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                client.settings.setString("username", username);
                updateTokenDialog();
                updateConnectionDialog(null);
            }
        });
    }

    public void showTokenWarning() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                String message = "<html><body style='width:400px;'>Login data was determined "
                        + "invalid, which means you may have to request it again before "
                        + "you can connect to chat or do authorized actions (like "
                        + "getting notified about streams you follow, edit stream title..).";
                String[] options = new String[]{"Close / Configure login","Just Close"};
                int result = GuiUtil.showNonAutoFocusOptionPane(MainGui.this, "Error",
                        message, JOptionPane.ERROR_MESSAGE,
                        JOptionPane.DEFAULT_OPTION, options);
                if (result == 0) {
                    openTokenDialog();
                }
            }
        });
    }

    /**
     * Updates the connection dialog with current settings
     */
    private void updateConnectionDialog(String channelPreset) {
        connectionDialog.setUsername(client.settings.getString("username"));
        if (channelPreset != null) {
            connectionDialog.setChannel(channelPreset);
        } else {
            connectionDialog.setChannel(client.settings.getString("channel"));
        }

        String cookies = client.settings.getString("cookies");
        connectionDialog.update(cookies);
        connectionDialog.setAreChannelsOpen(channels.getChannelCount() > 0);
    }

    private void openTokenDialog() {
        updateTokenDialog();
        if (connectionDialog.isVisible()) {
            tokenDialog.setLocationRelativeTo(connectionDialog);
        } else {
            tokenDialog.setLocationRelativeTo(this);
        }
        tokenDialog.setVisible(true);
    }

    private void updateTokenDialog() {
        String username = client.settings.getString("username");
        String tokens = client.settings.getString("cookies");
        tokenDialog.update(username, tokens);
        tokenDialog.setForeignToken(client.settings.getBoolean("foreignToken"));
    }

    public void updateUserinfo(final User user) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                updateUserInfoDialog(user);
            }
        });
    }


    public UserListener getUserListener() {
        return userListener;
    }

    protected void ignoredMessagesCount(String channel, String message) {
        if (client.settings.getLong("ignoreMode") == IgnoredMessages.MODE_COUNT
                && showIgnoredInfo()) {
            if (channels.isChannel(channel)) {
                channels.getExistingChannel(channel).printLine(message);
            }
        }
    }

    public WindowListener getWindowListener() {
        return windowListener;
    }

    private boolean showIgnoredInfo() {
        return !client.settings.getBoolean("ignoreShowNotDialog") ||
                !ignoredMessages.isVisible();
    }

    public ActionListener getActionListener() {
        return actionListener;
    }

    public void printLine(final String line) {
        SwingUtilities.invokeLater(() -> {
            Channel panel = channels.getLastActiveChannel();
            if (panel != null) {
                printInfo(panel, InfoMessage.createInfo(line));
            }
        });
    }

    public void printSystem(final String line) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Channel panel = channels.getActiveChannel();
                if (panel != null) {
                    printInfo(panel, InfoMessage.createSystem(line));
                }
            }
        });
    }

    public void printLine(final Room room, final String line) {
        SwingUtilities.invokeLater(() -> {
            printInfo(room, line, null);
        });
    }

    public void printInfo(final Room room, final String line, MsgTags tags) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (room == null || room == Room.EMPTY) {
                    printLine(line);
                } else {
                    printInfo(channels.getChannel(room), InfoMessage.createInfo(line, tags));
                }
            }
        });
    }

    public void printLineAll(final String line) {
        SwingUtilities.invokeLater(() -> {
            for (Channel channel : channels.allChannels()) {
                // Separate for each channel, since it could be modified based
                // on channel
                printInfo(channel, InfoMessage.createInfo(line));
            }
        });
    }

    public void printLineByOwnerChannel(final String channel, final String text) {
        SwingUtilities.invokeLater(() -> {
            for (Channel chan : channels.getExistingChannelsByOwner(channel)) {
                printInfo(chan, InfoMessage.createInfo(text));
            }
        });
    }

    /**
     * Central method for printing info messages. Each message is intended for
     * a single channel, so for printing to e.g. all channels at once, this is
     * called once each for all channels.
     *
     * @param channel
     * @param message
     * @return
     */
    private boolean printInfo(Channel channel, InfoMessage message) {
        User user = null;
        if (message instanceof UserNotice) {
            user = ((UserNotice)message).user;
        }
        MsgTags tags = message.tags;
        //----------------
        // Output Message
        //----------------
        if (!message.isHidden()) {
            User localUser = client.getLocalUser(channel.getChannel());
            notificationManager.info(channel.getRoom(), message.text, localUser);
            if (client.settings.getBoolean("msgColorsPrefer")) {
                ColorItem colorItem = msgColorManager.getInfoColor(
                        message.text, channel.getChannel(), client.addressbook, user, localUser, tags);
                if (!colorItem.isEmpty()) {
                    message.color = colorItem.getForegroundIfEnabled();
                    message.bgColor = colorItem.getBackgroundIfEnabled();
                }
            }
        }
        channel.printInfoMessage(message);
        if (channel.getType() == Channel.Type.SPECIAL) {
            channels.setChannelNewMessage(channel);
        }

        //----------
        // Chat Log
        //----------
        if (message.isSystemMsg()) {
            client.chatLog.system(channel.getFilename(), message.text);
        } else if (!message.text.startsWith("[ModAction]")) {
            // ModLog message could be ModLogInfo or generic ModInfo (e.g. for
            // abandoned messages), so just checking the text instead of type or
            // something (ModActions are logged separately)
            client.chatLog.info(channel.getFilename(), message.text);
        }
        return true;
    }

    /**
     * Calls the appropriate method from the given channel
     *
     * @param channel The channel this even has happened in.
     * @param type The type of event.
     * @param user The User object of who was the target of this event (mod/..).
     */
    public void printCompact(final String type, final User user) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                channels.getChannel(user.getRoom()).printCompact(type, user);
            }
        });
    }

    /**
     * Perform search in the currently selected channel. Should only be called
     * from the EDT.
     *
     * @param window
     * @param searchText
     * @return
     */
    public boolean search(final Window window, final String searchText) {
        Channel chan = channels.getChannelFromWindow(window);
        if (chan == null) {
            return false;
        }
        return chan.search(searchText);
    }

    public void resetSearch(final Window window) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Channel chan = channels.getChannelFromWindow(window);
                if (chan != null) {
                    chan.resetSearch();
                }
            }
        });
    }

    public void showMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                if (connectionDialog.isVisible()) {
                    JOptionPane.showMessageDialog(connectionDialog, message);
                }
                else {
                    printLine(message);
                }
            }
        });
    }


    /**
     * If not matching message was found for the ModAction to append the @mod,
     * then output anyway.
     *
     * @param info
     */
    public void printAbandonedModLogInfo(ModLogInfo info) {
        boolean showActions = client.settings.getBoolean("showModActions");
        if (showActions && !info.ownAction) {
            printInfo(info.chan, InfoMessage.createInfo(info.text));
        }
    }

    public void showNotification(String title, String message, Color foreground, Color background, String channel) {
        long setting = client.settings.getLong("nType");
        if (setting == NotificationSettings.NOTIFICATION_TYPE_CUSTOM) {
            notificationWindowManager.showMessage(title, message, foreground, background, channel);
        } else if (setting == NotificationSettings.NOTIFICATION_TYPE_TRAY) {
            trayIcon.displayInfo(title, message);
        } else if (setting == NotificationSettings.NOTIFICATION_TYPE_COMMAND) {
            GuiUtil.showCommandNotification(client.settings.getString("nCommand"),
                    title, message, channel);
        }
        eventLog.add(new chatty.gui.components.eventlog.Event(
                chatty.gui.components.eventlog.Event.Type.NOTIFICATION,
                null, title, message, foreground, background));
    }

    public boolean isAppActive() {
        for (Window frame : Window.getWindows()) {
            if (frame.isActive()) {
                return true;
            }
        }
        return false;
    }

    public boolean isChanActive(String channel) {
        return channels.getLastActiveChannel().getChannel().equals(channel);
    }

    public void setSystemEventCount(int count) {
        menu.setSystemEventCount(count);
    }


    public void updateState() {
        updateState(false);
    }

    public void updateState(final boolean forced) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                state.update(forced);
                //client.testHotkey();
            }
        });
    }

    /**
     * Outputs a line to the debug window
     *
     * @param line
     */
    public void printDebugIrc(final String line) {
        if (SwingUtilities.isEventDispatchThread()) {
            debugWindow.printLineIrc(line);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    debugWindow.printLineIrc(line);
                }
            });
        }
    }

    /**
     * Outputs a line to the debug window
     *
     * @param line
     */
    public void printDebug(final String line) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                debugWindow.printLine(line);
            }
        });
    }

    /**
     * Adds a user to a channel, adding to the userlist
     *
     * @param channel
     * @param user
     */
    public void addUser(final User user) {
        SwingUtilities.invokeLater(() -> {
            if (shouldUpdateUser(user)) {
                Channel c = channels.getChannel(user.getRoom());
                c.addUser(user);
                if (channels.getActiveChannel() == c) {
                    state.update();
                }
            }
        });
    }

    /**
     * Updates a user.
     *
     * @param user
     */
    public void updateUser(final User user) {
        SwingUtilities.invokeLater(() -> {
            if (shouldUpdateUser(user)) {
                channels.getChannel(user.getRoom()).updateUser(user);
                state.update();
            }
        });
    }

    private boolean shouldUpdateUser(User user) {
        return true;
    }

    /**
     * Display an error dialog with the option to quit or continue the program
     * and to report the error.
     *
     * @param error The error as a LogRecord
     * @param previous Some previous debug messages as LogRecord, to provide
     * context
     */
    public void error(final LogRecord error, final LinkedList<LogRecord> previous) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                int result = errorMessage.show(error, previous, client.getOpenChannels().size());
                if (result == ErrorMessage.QUIT) {
                    exit();
                }
            }
        });
    }

    /**
     * Manages updating the current state, mainly the titles and menus.
     */
    private class StateUpdater {

        /**
         * Saves when the state was last updated, so the delay can be measured.
         */
        private final ElapsedTime lastUpdatedET = new ElapsedTime();

        /**
         * Update state no faster than this amount of milliseconds.
         */
        private static final int UPDATE_STATE_DELAY = 500;

        /**
         * Update the title and other things based on the current state and
         * stream/channel information. This is a convenience method that doesn't
         * force the update.
         *
         * @see update(boolean)
         */
        protected void update() {
            update(false);
        }

        /**
         * Update the title and other things based on the current state and
         * stream/channel information.
         *
         * <p>The update is only performed once every {@literal UPDATE_STATE_DELAY}
         * milliseconds, unless {@literal forced} is {@literal true}. This is meant
         * to prevent flickering of the titlebar when a lot of updates would
         * happen, for example when a lot of joins/parts happen at once.</p>
         *
         * <p>Of course this means that the info might not be always up-to-date:
         * The chance is pretty high that the last update is skipped because it
         * came to close to the previous. The UpdateTimer updates every 10s so
         * it shouldn't take too long to be corrected. This also mainly affects
         * the chatter count because it gets updated in many small steps when
         * joins/parts happen (it also already isn't very up-to-date anyway from
         * Twitch's side though).</p>
         *
         * @param forced If {@literal true} the update is performed with every call
         */
        protected void update(boolean forced) {
            if (!guiCreated) {
                return;
            }
            if (!forced && !lastUpdatedET.millisElapsed(UPDATE_STATE_DELAY)) {
                return;
            }
            lastUpdatedET.set();

            int state = client.getState();

            //requestFollowedStreams();
            updateMenuState(state);
            updateTitles(state);
        }

        /**
         * Disables/enables menu items based on the current state.
         *
         * @param state
         */
        private void updateMenuState(int state) {
            if (state > YouTubeLiveChats.STATE_NONE) {
                menu.getMenuItem("connect").setEnabled(false);
            } else {
                menu.getMenuItem("connect").setEnabled(true);
            }

            if (state > YouTubeLiveChats.STATE_NONE) {
                menu.getMenuItem("disconnect").setEnabled(true);
            } else {
                menu.getMenuItem("disconnect").setEnabled(false);
            }
        }

        /**
         * Updates the titles of both the main window and popout dialogs.
         *
         * @param state
         */
        private void updateTitles(int state) {
            // May be necessary to make the title either way, because it also
            // requests stream info
            String mainTitle = makeTitle(channels.getActiveTab(), state);
            String trayTooltip = makeTitle(channels.getLastActiveChannel(), state);
            trayIcon.setTooltipText(trayTooltip);
            if (client.settings.getBoolean("simpleTitle")) {
                setTitle("Chatty");
            } else {
                setTitle(mainTitle);
            }
            Map<Channel, JDialog> popoutChannels = channels.getPopoutChannels();
            for (Channel channel : popoutChannels.keySet()) {
                String title = makeTitle(channel, state);
                popoutChannels.get(channel).setTitle(title);
            }
        }

        /**
         * Assembles the title of the window based on the current state and chat
         * and stream info.
         *
         * @param channel The {@code Channel} object to create the title for
         * @param state The current state
         * @return The created title
         */
        private String makeTitle(Channel channel, int state) {
            /*
                Make Title TODO
             */
            String channelName = channel.getName();
            String chan = channel.getChannel();

            // Current state
            String stateText = "";

            String title = stateText;
            return "Chatty";
        }
    }

    public Settings getSettings() {
        return client.settings;
    }

    public Collection<String> getSettingNames() {
        return client.settings.getSettingNames();
    }

    public Collection<Emoticon> getUsableGlobalEmotes() {
        return emoticons.getLocalTwitchEmotes();
    }

    public Collection<Emoticon> getUsableEmotesPerStream(String stream) {
        return emoticons.getUsableEmotesByStream(stream);
    }

    public String getCustomCompletionItem(String key) {
        return (String)client.settings.mapGet("customCompletion", key);
    }

    public Collection<String> getCustomCommandNames() {
        return client.customCommands.getCommandNames();
    }

    private class MyWindowListener extends WindowAdapter {

        @Override
        public void windowClosing(WindowEvent e) {
            if (e.getSource() == cookiesPasteDialog) {
                tokenGetDialogClosed();
            }
        }
    }

    private class MainWindowListener extends WindowAdapter {

        private boolean liveStreamsHidden;

        @Override
        public void windowStateChanged(WindowEvent e) {
            if (e.getComponent() == MainGui.this) {
                saveState(e.getComponent());
                if (isMinimized()) {
                    //if (liveStreamsDialog.isVisible()
                    //        && client.settings.getBoolean("hideStreamsOnMinimize")) {
                    //    liveStreamsDialog.setVisible(false);
                    //    liveStreamsHidden = true;
                    //}
                    if (client.settings.getBoolean("minimizeToTray")) {
                        minimizeToTray();
                    }
                } else {
                    // Only cleanup from tray if not minimized, when minimized
                    // cleanup should never be done
                    cleanupAfterRestoredFromTray();
                    //if (liveStreamsHidden) {
                    //    liveStreamsDialog.setVisible(true);
                    //    liveStreamsHidden = false;
                    //}
                }
            }
        }

        @Override
        public void windowClosing(WindowEvent evt) {
            if (evt.getComponent() == MainGui.this) {
                if (client.settings.getBoolean("closeToTray")) {
                    minimizeToTray();
                } else {
                    exit();
                }
            }
        }
    }

    /**
     * Saves whether the window is currently maximized.
     */
    private void saveState(Component c) {
        if (c == this) {
            client.settings.setBoolean("maximized", isMaximized());
        }
    }

    /**
     * Returns if the window is currently maximized.
     *
     * @return true if the window is maximized, false otherwise
     */
    private boolean isMaximized() {
        return (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
    }

    /**
     * Remove tray icon if applicable.
     */
    private void cleanupAfterRestoredFromTray() {
        if (client.settings.getLong("nType") != NotificationSettings.NOTIFICATION_TYPE_TRAY
                && !client.settings.getBoolean("trayIconAlways")) {
            trayIcon.setIconVisible(false);
        }
    }

    public void showPopupMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, text);
        });
    }

    private void openHelp(String ref) {
        openHelp(null, ref, false);
    }

    public void openHelp(String page, String ref) {
        openHelp(page, ref, false);
    }

    public void openHelp(String page, String ref, boolean keepPage) {
        if (!aboutDialog.isVisible()) {
            aboutDialog.setLocationRelativeTo(this);
        }
        if (!keepPage) {
            aboutDialog.open(page, ref);
        }
        // Set ontop setting, so it won't be hidden behind the main window
        aboutDialog.setAlwaysOnTop(client.settings.getBoolean("ontop"));
        aboutDialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        aboutDialog.toFront();
        aboutDialog.setState(NORMAL);
        aboutDialog.setVisible(true);
    }

    public java.util.List<UsercolorItem> getUsercolorData() {
        return new ArrayList<>();
    }

    public void setUsercolorData(java.util.List<UsercolorItem> data) {

    }

    public java.util.List<MsgColorItem> getMsgColorData() {
        return msgColorManager.getData();
    }

    public void setMsgColorData(java.util.List<MsgColorItem> data) {
        msgColorManager.setData(data);
    }

    public java.util.List<Usericon> getUsericonData() {
        return new ArrayList<>();
    }

    public void setUsericonData(java.util.List<Usericon> data) {

    }

    public Set<String> getTwitchBadgeTypes() {
        return new HashSet<>();
    }

    public java.util.List<Notification> getNotificationData() {
        return notificationManager.getData();
    }

    public void setNotificationData(java.util.List<Notification> data) {
        notificationManager.setData(data);
    }

    public void insert(final String text, final boolean spaces) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (text != null) {
                    channels.getLastActiveChannel().insertText(text, spaces);
                }
            }
        });
    }

    /**
     * Only call out of the EDT.
     *
     * @param user
     * @param msgId
     */
    public void openUserInfoDialog(User user, String msgId, String autoModMsgId) {
        openUserInfoDialog(user, msgId, autoModMsgId, false);
    }

    /**
     * Only call out of the EDT.
     *
     * @param user
     * @param msgId
     */
    public void openUserInfoDialog(User user, String msgId, String autoModMsgId, boolean keepPosition) {
        windowStateManager.setWindowPosition(userInfoDialog.getDummyWindow(), getActiveWindow());
        userInfoDialog.show(getActiveWindow(), user, msgId, autoModMsgId, client.getChannelID(), keepPosition);
    }

    public void updateEmotesDialog(Set<String> emotesets) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                emotesDialog.updateEmotesets(emotesets);
            }
        });
    }

    public void updateEmoticons(final EmoticonUpdate update) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                emoticons.updateEmoticons(update);
                emotesDialog.update();
            }
        });
    }

    public void addEmoticons(final Set<Emoticon> emotes) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                emoticons.addEmoticons(emotes);
                emotesDialog.update();
            }
        });
    }


    public void refreshEmotes(String type) {
        if (type.equals("user")) {
            client.emotesetManager.requestUserEmotes();
        }
    }

    public void reconnect() {

    }

    public void clearHistory() {

    }

    public LinkLabelListener getLinkLabelListener() {
        return linkLabelListener;
    }


    private class DebugCheckboxListener implements ItemListener {
        @Override
        public void itemStateChanged(ItemEvent e) {
            boolean state = e.getStateChange() == ItemEvent.SELECTED;
            if (e.getSource() == debugWindow.getLogIrcCheckBox()) {
                client.settings.setBoolean("debugLogIrc", state);
            }
        }
    }

    private class MyLinkLabelListener implements LinkLabelListener {
        @Override
        public void linkClicked(String type, String ref) {
            if (type.equals("help")) {
                openHelp(ref);
            } else if (type.equals("help-settings")) {
                openHelp("help-settings.html", ref);
            } else if (type.equals("help-commands")) {
                openHelp("help-custom_commands.html", ref);
            } else if (type.equals("help-admin")) {
                openHelp("help-admin.html", ref);
            } else if (type.equals("help-livestreamer")) {
                openHelp("help-livestreamer.html", ref);
            } else if (type.equals("help-whisper")) {
                openHelp("help-whisper.html", ref);
            } else if (type.equals("help-laf")) {
                openHelp("help-laf.html", ref);
            } else if (type.equals("url")) {
                UrlOpener.openUrlPrompt(MainGui.this, ref);
            } else if (type.equals("update")) {
                if (ref.equals("show")) {
                    //openUpdateDialog();
                }
            } else if (type.equals("announcement")) {
                if (ref.equals("show")) {
                    //newsDialog.showDialog();
                }
            }
        }
    }


    private boolean isOwnChannelID(String channel_id) {
        String ownUsername = client.getChannelID();
        return ownUsername != null && ownUsername.equalsIgnoreCase(channel_id);
    }

    public void msgDeleted(final User user, String targetMsgId, String msg) {
        SwingUtilities.invokeLater(() -> {
            channels.getChannel(user.getRoom()).userBanned(user, -2, null, targetMsgId);
            user.addMsgDeleted(targetMsgId, msg);
            updateUserInfoDialog(user);
            if (client.settings.listContains("streamChatChannels", user.getChannel())) {
                streamChat.userBanned(user, -2, null, targetMsgId);
            }
        });
    }

    public void userBanned(final User user, final long duration, final String reason, final String id) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                channels.getChannel(user.getRoom()).userBanned(user, duration, reason, id);
                user.addBan(duration, reason, id);
                updateUserInfoDialog(user);
                if (client.settings.listContains("streamChatChannels", user.getChannel())) {
                    streamChat.userBanned(user, duration, reason, id);
                }
            }
        });
    }

    public void userBannedUpdate(final User user, final long duration, final String reason, final String id) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                channels.getChannel(user.getRoom()).userBanned(user, duration, reason, id);
            }
        });
    }

    public void printModerationAction(final ModerationData data,
                                      final boolean ownAction) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                //moderationLog.add(data);
                //autoModDialog.addData(data);

                String channel = data.stream;

                // Moderator Actions apparently apply to all rooms
                Collection<Channel> chans = channels.getExistingChannelsByOwner(channel);
                if (!chans.isEmpty()
                        && data.type != ModerationData.Type.AUTOMOD_REJECTED
                        && data.type != ModerationData.Type.UNMODDED) {
                    boolean showActions = client.settings.getBoolean("showModActions");
                    boolean showActionsRestrict = client.settings.getBoolean("showModActionsRestrict");
                    boolean showMessage =
                            showActions
                                    && (!ownAction || ModLogInfo.isIndirectAction(data))
                                    && !(showActionsRestrict && ModLogInfo.isAssociated(data));
                    boolean showActionby = client.settings.getBoolean("showActionBy");
                    for (Channel chan : chans) {
                        // Create for each channel, just in case (since they get
                        // modified)
                        // TODO: Output that output of reason or by isn't affected by ignore etc.
                        ModLogInfo infoMessage = new ModLogInfo(chan, data, showActionby, ownAction);
                        infoMessage.setHidden(!showMessage);
                        printInfo(chan, infoMessage);
                    }
                }

            }
        });
    }

    /**
     * Checks the dedicated user ignore list. The regular ignore list may still
     * ignore the user.
     *
     * @param user
     * @param whisper
     * @return
     */
    private boolean userIgnored(User user, boolean whisper) {
        String setting = whisper ? "ignoredUsersWhisper" : "ignoredUsers";
        return client.settings.listContains(setting, user.getName());
    }

    private String processMessage(String text) {
        int mode = (int)client.settings.getLong("filterCombiningCharacters");
        return Helper.filterCombiningCharacters(text, "****", mode);
    }

    private void updateUserInfoDialog(User user) {
        userInfoDialog.update(user, client.getChannelID());
    }

    private boolean checkHighlight(Highlighter.HighlightItem.Type type, String text,
                                   String channel, Addressbook ab, User user, User localUser, MsgTags tags, Highlighter hl,
                                   String setting, boolean isOwnMessage) {
        if (client.settings.getBoolean(setting + "Enabled")) {
            if (client.settings.getBoolean(setting + "OwnText") ||
                    !isOwnMessage) {
                return hl.check(type, text, channel, ab, user, localUser, tags);
            }
        }
        return false;
    }

    private boolean checkMsg(Highlighter hl, String setting, String text,
                             User user, User localUser, MsgTags tags, boolean isOwnMessage) {
        return checkHighlight(Highlighter.HighlightItem.Type.REGULAR, text, null, null,
                user, localUser, tags, hl, setting, isOwnMessage);
    }

    /**
     * Tells the highlighter the current username and whether it should be used
     * for highlight. Used to initialize on connect, when the username is fixed
     * for the duration of the connection.
     *
     * @param username The current username.
     */
    public void updateHighlightSetUsername(String username) {
        highlighter.setUsername(username);
        highlighter.setHighlightUsername(client.settings.getBoolean("highlightUsername"));
    }

    public Window getActiveWindow() {
        for (Window frame : Window.getWindows()) {
            if (frame.isActive()) {
                return frame;
            }
        }
        return this;
    }

    /* ############
     * # Messages #
     */

    public void printMessage(User user, String text, boolean action) {
        printMessage(user, text, action, MsgTags.EMPTY);
    }

    public void printMessage(User user, String text, boolean action, MsgTags tags) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Channel chan;
                String channel = user.getChannel();
                int bitsAmount = tags.getBits();
                User localUser = client.getLocalUser(channel);

                chan = channels.getChannel(user.getRoom());
                // If channel was changed from the given one, change accordingly
                channel = chan.getChannel();

                boolean isOwnMessage = isOwnChannelID(user.getId()) || (action);
                boolean ignoredUser = (userIgnored(user, false) && !isOwnMessage);
                boolean ignored = checkMsg(ignoreList, "ignore", text, user, localUser, tags, isOwnMessage) || ignoredUser;

                if (!ignored || client.settings.getBoolean("logIgnored")) {
                    client.chatLog.bits(chan.getFilename(), user, bitsAmount);
                    client.chatLog.message(chan.getFilename(), user, text, action);
                }

                boolean highlighted = false;
                List<Highlighter.Match> highlightMatches = null;
                if ((client.settings.getBoolean("highlightIgnored") || !ignored)
                        && !client.settings.listContains("noHighlightUsers", user.getName())) {
                    highlighted = checkMsg(highlighter, "highlight", text, user, localUser, tags, isOwnMessage);
                }

                Emoticons.TagEmotes tagEmotes = Emoticons.parseEmotesTag(tags.getRawEmotes());

                // Do stuff if highlighted, without printing message
                if (highlighted) {
                    highlightMatches = highlighter.getLastTextMatches();
                    if (!highlighter.getLastMatchNoNotification()) {
                        channels.setChannelHighlighted(chan);
                    } else {
                        channels.setChannelNewMessage(chan);
                    }
                    notificationManager.highlight(user, localUser, text, tags,
                            highlighter.getLastMatchNoNotification(),
                            highlighter.getLastMatchNoSound(),
                            isOwnMessage, false, bitsAmount > 0);
                } else if (!ignored) {
                    notificationManager.message(user, localUser, text, tags, isOwnMessage,
                            bitsAmount > 0);
                    if (!isOwnMessage) {
                        channels.setChannelNewMessage(chan);
                    }
                }

                // Do stuff if ignored, without printing message
                if (ignored) {
                    List<Highlighter.Match> ignoreMatches = null;
                    if (!ignoredUser) {
                        // Text matches might not be valid if ignore was through
                        // ignored users list
                        ignoreMatches = ignoreList.getLastTextMatches();
                    }
                    ignoredMessages.addMessage(channel, user, text, action,
                            tagEmotes, 0, false, ignoreMatches,
                            tags);
                    ignoredMessagesHelper.ignoredMessage(channel);
                }
                long ignoreMode = client.settings.getLong("ignoreMode");

                // Print or don't print depending on ignore
                if (ignored && (ignoreMode <= IgnoredMessages.MODE_COUNT ||
                        !showIgnoredInfo())) {
                    // Don't print message
                    if (isOwnMessage && channels.isChannel(channel)) {
                        // Don't log to file
                        printInfo(chan, InfoMessage.createInfo("Own message ignored."));
                    }
                } else {
                    boolean hasReplacements = checkMsg(filter, "filter", text, user, localUser, tags, isOwnMessage);

                    // Print message, but determine how exactly
                    UserMessage message = new UserMessage(user, text, tagEmotes, tags.getId(), 0,
                            highlightMatches,
                            hasReplacements ? filter.getLastTextMatches() : null,
                            hasReplacements ? filter.getLastReplacement() : null,
                            tags);

                    // Custom color
                    boolean hlByPoints = tags.isHighlightedMessage() && client.settings.getBoolean("highlightByPoints");
                    if (highlighted) {
                        message.color = highlighter.getLastMatchColor();
                        message.backgroundColor = highlighter.getLastMatchBackgroundColor();
                    }
                    if (!(highlighted || hlByPoints) || client.settings.getBoolean("msgColorsPrefer")) {
                        ColorItem colorItem = msgColorManager.getMsgColor(user, localUser, text, tags);
                        if (!colorItem.isEmpty()) {
                            message.color = colorItem.getForegroundIfEnabled();
                            message.backgroundColor = colorItem.getBackgroundIfEnabled();
                        }
                    }
                    message.whisper = false;
                    message.action = action;
                    if (highlighted || hlByPoints) {
                        // Only set message.highlighted instead of highlighted
                        // if hlByPoints, since that would affect other stuff as
                        // well
                        message.highlighted = true;
                    } else if (ignored && ignoreMode == IgnoredMessages.MODE_COMPACT) {
                        message.ignored_compact = true;
                    }
                    chan.printMessage(message);
                    if (highlighted) {
                        highlightedMessages.addMessage(channel, message);
                    }
                    if (client.settings.listContains("streamChatChannels", channel)) {
                        streamChat.printMessage(message);
                    }
                }

                CopyMessages.copyMessage(client.settings, user, text, highlighted);

                // Update User
                user.addMessage(processMessage(text), action, tags.getId());
                if (highlighted) {
                    user.setHighlighted();
                }
                updateUserInfoDialog(user);
            }
        });
    }

    public void printMessage(User user, String client_id, liveChatTextMessageRenderer messageRenderer) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                String channel = user.getChannel();

                User localUser = client.getLocalUser(channel);
                Channel chan = channels.getChannel(user.getRoom());
                // If channel was changed from the given one, change accordingly
                channel = chan.getChannel();

                MsgTags tags = messageRenderer.parse();
                String text = messageRenderer.getMessage().toString();

                boolean isOwnMessage = isOwnChannelID(user.getChannelID());
                boolean ignoredUser = (userIgnored(user, false) && !isOwnMessage);
                boolean ignored = checkMsg(ignoreList, "ignore", text, user, localUser, tags, isOwnMessage) || ignoredUser;

                if (!ignored || client.settings.getBoolean("logIgnored")) {
                    client.chatLog.bits(chan.getFilename(), user, 0);
                    client.chatLog.message(chan.getFilename(), user, text, false);
                }

                boolean highlighted = false;
                List<Highlighter.Match> highlightMatches = null;
                if ((client.settings.getBoolean("highlightIgnored") || !ignored)
                        && !client.settings.listContains("noHighlightUsers", user.getName())) {
                    highlighted = checkMsg(highlighter, "highlight", text, user, localUser, tags, isOwnMessage);
                }

                Emoticons.TagEmotes tagEmotes = Emoticons.parseEmotesTag(tags.getRawEmotes());

                // Do stuff if highlighted, without printing message
                if (highlighted) {
                    highlightMatches = highlighter.getLastTextMatches();
                    if (!highlighter.getLastMatchNoNotification()) {
                        channels.setChannelHighlighted(chan);
                    } else {
                        channels.setChannelNewMessage(chan);
                    }
                    notificationManager.highlight(user, localUser, text, tags,
                            highlighter.getLastMatchNoNotification(),
                            highlighter.getLastMatchNoSound(),
                            isOwnMessage, false, false);
                } else if (!ignored) {
                    notificationManager.message(user, localUser, text, tags, isOwnMessage,
                            false);
                    if (!isOwnMessage) {
                        channels.setChannelNewMessage(chan);
                    }
                }

                long ignoreMode = client.settings.getLong("ignoreMode");

                if (ignored && (ignoreMode <= IgnoredMessages.MODE_COUNT ||
                        !showIgnoredInfo())) {
                    // Don't print message
                    if (isOwnMessage && channels.isChannel(channel)) {
                        // Don't log to file
                        printInfo(chan, InfoMessage.createInfo("Own message ignored."));
                    }
                } else {
                    boolean hasReplacements = checkMsg(filter, "filter", messageRenderer.getMessage().toString(), user, localUser, tags, isOwnMessage);

                    UserMessage message = new UserMessage(user, messageRenderer.getMessage(), null, tags.getId(), 0,
                            highlightMatches,
                            hasReplacements ? filter.getLastTextMatches() : null,
                            hasReplacements ? filter.getLastReplacement() : null,
                            tags);
                    // Custom color
                    boolean hlByPoints = tags.isHighlightedMessage() && client.settings.getBoolean("highlightByPoints");
                    if (highlighted) {
                        message.color = highlighter.getLastMatchColor();
                        message.backgroundColor = highlighter.getLastMatchBackgroundColor();
                    }
                    if (!(highlighted || hlByPoints) || client.settings.getBoolean("msgColorsPrefer")) {
                        ColorItem colorItem = msgColorManager.getMsgColor(user, localUser, text, tags);
                        if (!colorItem.isEmpty()) {
                            message.color = colorItem.getForegroundIfEnabled();
                            message.backgroundColor = colorItem.getBackgroundIfEnabled();
                        }
                    }
                    message.whisper = false;
                    message.action = false;
                    if (highlighted || hlByPoints) {
                        // Only set message.highlighted instead of highlighted
                        // if hlByPoints, since that would affect other stuff as
                        // well
                        message.highlighted = true;
                    } else if (ignored && ignoreMode == IgnoredMessages.MODE_COMPACT) {
                        message.ignored_compact = true;
                    }
                    chan.printMessage(message);
                    if (highlighted) {
                        highlightedMessages.addMessage(channel, message);
                    }
                    if (client.settings.listContains("streamChatChannels", channel)) {
                        streamChat.printMessage(message);
                    }
                }

                CopyMessages.copyMessage(client.settings, user, text, highlighted);

                // Update User
                user.addMessage(processMessage(text), false, tags.getId());
                if (highlighted) {
                    user.setHighlighted();
                }
                updateUserInfoDialog(user);
            }
        });
    }

    public void anonCustomCommand(Room room, CustomCommand command, Parameters parameters) {
        client.anonCustomCommand(room, command, parameters);
    }


    public ChannelInfo getCachedChannelInfo(String channel, String id) {
        return null;
    }

    /**
     * Checks if the main window is currently minimized.
     *
     * @return true if minimized, false otherwise
     */
    private boolean isMinimized() {
        return (getExtendedState() & ICONIFIED) == ICONIFIED;
    }

    /**
     * Minimize window to tray.
     */
    private void minimizeToTray() {
        //trayIcon.displayInfo("Minimized to tray", "Double-click icon to show again..");

        trayIcon.setIconVisible(true);
        if (!isMinimized()) {
            setExtendedState(getExtendedState() | ICONIFIED);
        }
        if (trayIcon.isAvailable()) {
            // Set visible to false, so it is removed from the taskbar, but only
            // if tray icon is actually added
            setVisible(false);
        }
    }


    private class TrayMenuListener implements ActionListener {

        private final ElapsedTime lastEvent = new ElapsedTime();

        @Override
        public void actionPerformed(ActionEvent e) {
            String cmd = e.getActionCommand();
            if (cmd == null
                    || cmd.equals("show")
                    || cmd.equals("doubleClick")
                    || (cmd.equals("singleClick") && client.settings.getBoolean("singleClickTrayOpen"))) {
                /**
                 * Prevent hiding/showing too quickly, for example when both the
                 * mouse listener and the action listener fire (could also be
                 * platform dependent).
                 */
                if (lastEvent.millisElapsed(80)) {
                    lastEvent.set();
                    if (isMinimized()) {
                        makeVisible();
                    }
                    else {
                        minimizeToTray();
                    }
                }
            }
            else if (cmd.equals("exit")) {
                exit();
            }
        }

    }

    private class MyNotificationActionListener implements NotificationActionListener<String> {

        /**
         * Right-clicked on a notification.
         *
         * @param data
         */
        @Override
        public void notificationAction(String data) {
            if (data != null) {
                makeVisible();
                client.joinChannel(data);
            }
        }
    }

    /**
     * Listener for the Main Menu
     */
    private class MainMenuListener implements ItemListener, ActionListener, MenuListener {

        @Override
        public void itemStateChanged(ItemEvent e) {
            String setting = menu.getSettingByMenuItem(e.getSource());
            boolean state = e.getStateChange() == ItemEvent.SELECTED;

            if (setting != null) {
                client.settings.setBoolean(setting, state);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String cmd = e.getActionCommand();
            if (cmd.equals("debug")) {
                if (!debugWindow.isShowing()) {
                    debugWindow.setLocationByPlatform(true);
                    debugWindow.setPreferredSize(new Dimension(500, 400));
                }
                debugWindow.setVisible(true);
            } else if (cmd.equals("connect")) {
                //openConnectDialogInternal(null);
            } else if (cmd.equals("disconnect")) {
                //client.disconnect();
            } else if (cmd.equals("about")) {
                openHelp("");
            } else if (cmd.equals("news")) {
                //newsDialog.showDialog();
            } else if (cmd.equals("settings")) {
                getSettingsDialog().showSettings();
            } else if (cmd.equals("saveSettings")) {
                int result = JOptionPane.showOptionDialog(MainGui.this,
                        Language.getString("saveSettings.text")+"\n\n"+Language.getString("saveSettings.textBackup"),
                        Language.getString("saveSettings.title"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null,
                        new String[]{
                                Language.getString("dialog.button.save"),
                                Language.getString("saveSettings.saveAndBackup"),
                                Language.getString("dialog.button.cancel")
                        }, null);
                if (result == 0) {
                    List<FileManager.SaveResult> saveResult = client.saveSettings(false, true);
                    JOptionPane.showMessageDialog(MainGui.this,
                            Helper.makeSaveResultInfo(saveResult),
                            Language.getString("saveSettings.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                }
                else if (result == 1) {
                    List<FileManager.SaveResult> saveResult = client.saveSettings(false, true);
                    List<FileManager.SaveResult> backupResult = client.manualBackup();
                    JOptionPane.showMessageDialog(MainGui.this,
                            Helper.makeSaveResultInfo(saveResult)+"\nManual Backup:\n"+Helper.makeSaveResultInfo(backupResult),
                            Language.getString("saveSettings.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } else if (cmd.equals("website")) {
                UrlOpener.openUrlPrompt(MainGui.this, Chatty.WEBSITE, true);
            } else if (cmd.equals("unhandledException")) {
                String[] array = new String[0];
                String a = array[1];
            } else if (cmd.equals("errorTest")) {
                Logger.getLogger(MainGui.class.getName()).log(Level.SEVERE, null, new ArrayIndexOutOfBoundsException(2));
            } else if (cmd.equals("addressbook")) {
                //openAddressbook(null)
            } else if (cmd.equals("livestreamer")) {
                //livestreamerDialog.open(null, null);
            } else if (cmd.equals("configureLogin")) {
                openTokenDialog();
            } else if (cmd.equals("addStreamHighlight")) {
                //client.commandAddStreamHighlight(channels.getActiveChannel().getRoom(), null);
            } else if (cmd.equals("openStreamHighlights")) {
                //client.commandOpenStreamHighlights(channels.getActiveChannel().getRoom());
            } else if (cmd.equals("srcOpen")) {
                //client.speedruncom.openCurrentGame(channels.getActiveChannel());
            } else if (cmd.startsWith("room:")) {
                String channel = cmd.substring("room:".length());
                client.joinChannel(channel);
            } else if (cmd.equals("dialog.chattyInfo")) {
                //openEventLog(1);
            }
        }

        @Override
        public void menuSelected(MenuEvent e) {
            if (e.getSource() == menu.srlStreams) {
                ArrayList<String> popoutStreams = new ArrayList<>();
                for (Channel channel : channels.getPopoutChannels().keySet()) {
                    if (channel.getStreamName() != null) {
                        popoutStreams.add(channel.getStreamName());
                    }
                }
                menu.updateSrlStreams(channels.getActiveTab().getStreamName(), popoutStreams);
            } else if (e.getSource() == menu.view) {
                menu.updateCount(highlightedMessages.getNewCount(),
                        highlightedMessages.getDisplayedCount(),
                        ignoredMessages.getNewCount(),
                        ignoredMessages.getDisplayedCount());
            }
        }

        @Override
        public void menuDeselected(MenuEvent e) {
        }

        @Override
        public void menuCanceled(MenuEvent e) {
        }

    }

    public void updateRoom(Room room) {
        SwingUtilities.invokeLater(() -> {
            channels.updateRoom(room);
        });
    }


    /**
     * Listener for all kind of context menu events
     */
    class MyContextMenuListener implements ContextMenuListener {

        @Override
        public void userMenuItemClicked(ActionEvent e, User user, String msgId, String autoModMsgId) {

        }

        @Override
        public void urlMenuItemClicked(ActionEvent e, String url) {

        }

        @Override
        public void menuItemClicked(ActionEvent e) {

        }

        @Override
        public void textMenuItemClick(ActionEvent e, String selected) {

        }

        @Override
        public void roomsMenuItemClicked(ActionEvent e, Collection<Room> rooms) {
            roomsStuff(e, rooms);
        }

        @Override
        public void channelMenuItemClicked(ActionEvent e, Channel channel) {

        }

        @Override
        public void streamsMenuItemClicked(ActionEvent e, Collection<String> streams) {

        }

        @Override
        public void streamInfosMenuItemClicked(ActionEvent e, Collection<StreamInfo> streamInfos) {

        }

        /**
         * Handles context menu events that can be applied to one or more
         * streams or channels. Checks if any valid stream parameters are
         * present and outputs an error otherwise. Since this can also be called
         * if it's not one of the commands that actually require a stream (other
         * listeners may be registered), it also checks if it's actually one of
         * the commands it handles.
         *
         * @param cmd The command
         * @param streams The list of stream or channel names
         */
        private void roomsStuff(ActionEvent e, Collection<Room> rooms) {
            Collection<String> channels = new ArrayList<>();
            Collection<String> streams = new ArrayList<>();
            for (Room room : rooms) {
                channels.add(room.getChannel());
                if (room.hasStream()) {
                    streams.add(room.getStream());
                }
            }
            channelStuff(e, channels);
            //streamStuff(e, streams);
        }

        private void channelStuff(ActionEvent e, Collection<String> channels) { ;
            String cmd = e.getActionCommand();
            //TwitchUrl.removeInvalidStreams(channels);
            if (cmd.equals("join")) {
                makeVisible();
                client.joinChannels(new HashSet<>(channels));
            }
            else if (cmd.equals("favoriteChannel")) {
                for (String chan : channels) {
                    client.channelFavorites.addFavorite(chan);
                }
                /**
                 * Manually update data when changed from the outside, instead
                 * of just using ChannelFavorites change listener (since changes
                 * through the favoritesDialog itself get handled differently
                 * but would also cause additional updates).
                 */
                //favoritesDialog.updateData();
            }
            else if (cmd.equals("unfavoriteChannel")) {
                for (String chan : channels) {
                    client.channelFavorites.removeFavorite(chan);
                }
                //favoritesDialog.updateData();
            }
        }


        @Override
        public void emoteMenuItemClicked(ActionEvent e, Emoticon.EmoticonImage emote) {

        }

        @Override
        public void usericonMenuItemClicked(ActionEvent e, Usericon usericon) {

        }
    }

    private class ChannelChangeListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {

        }
    }

    private class MyUserListener implements UserListener {

        @Override
        public void userClicked(User user, String msgId, String autoModMsgId, MouseEvent e) {
            if (e == null || (!e.isControlDown() && !e.isAltDown())) {
                openUserInfoDialog(user, msgId, autoModMsgId);
                return;
            }
            String command = client.settings.getString("commandOnCtrlClick").trim();
        }

        @Override
        public void emoteClicked(Emoticon emote, MouseEvent e) {
            if (e.isControlDown()) {
                insert(Emoticons.toWriteable(emote.code), true);
            } else {
                //openEmotesDialogEmoteDetails(emote);
            }
        }

        @Override
        public void usericonClicked(Usericon usericon, MouseEvent e) {
            if (!usericon.metaUrl.isEmpty()) {
                UrlOpener.openUrlPrompt(MainGui.this, usericon.metaUrl);
            }
        }
    }


    /**
     * Puts the updated state of the windows/dialogs/popouts into the settings.
     */
    public void saveWindowStates() {
        userInfoDialog.aboutToSaveSettings();
        windowStateManager.saveWindowStates();
        client.settings.putList("popoutAttributes", channels.getPopoutAttributes());
    }

    /**
     * Reopen some windows if enabled.
     */
    private void reopenWindows() {
        for (Window window : windowStateManager.getWindows()) {
            reopenWindow(window);
        }
    }

    /**
     * Open the given Component if enabled and if it was open before.
     *
     * @param window
     */
    private void reopenWindow(Window window) {
        if (windowStateManager.shouldReopen(window)) {

        }
    }

    private void tokenGetDialogClosed() {
        cookiesPasteDialog.setVisible(false);
    }

    /**
     * Token received from input.
     *
     * @param cookies_json
     */
    private void cookiesReceived(String cookies_json) {
        List<HttpCookie> cookies = null;
        try {
            cookies = Cookies.loadCookies(cookies_json);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(cookies == null) {
            cookiesPasteDialog.failedParseCookies();
            return;
        }
        client.settings.setString("cookies", cookies_json);
        client.settings.setBoolean("foreignToken", false);
        if (cookiesPasteDialog.isVisible()) {
            cookiesPasteDialog.cookiesReceived();
        }
        tokenDialog.update("", cookies_json);
        updateConnectionDialog(null);
        verifyCookies(cookies_json, cookies);
    }

    private class MySettingChangeListener implements SettingChangeListener {

        /**
         * Since this can also be called from other threads, run in EDT if
         * necessary.
         *
         * @param setting
         * @param type
         * @param value
         */
        @Override
        public void settingChanged(final String setting, final int type, final Object value) {
            if (SwingUtilities.isEventDispatchThread()) {
                settingChangedInternal(setting, type, value);
            } else {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        settingChangedInternal(setting, type, value);
                    }
                });
            }
        }


        private void settingChangedInternal(String setting, int type, Object value) {
            if (type == Setting.STRING) {
                if(setting.equals("cookies")) {
                    String cookie_json = (String) value;
                    client.web.setCookies(cookie_json);
                }
            }
        }
    }

    public void openConnectDialog(final String channelPreset) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                openConnectDialogInternal(channelPreset);
            }
        });
    }

    private void openConnectDialogInternal(String channelPreset) {
        updateConnectionDialog(channelPreset);
        connectionDialog.setLocationRelativeTo(this);
        connectionDialog.setVisible(true);
    }

    /*
     * Channel Management
     */

    public void removeChannel(final String channel) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                channels.removeChannel(channel);
                state.update();
            }
        });
    }

    public void switchToChannel(final String channel) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                channels.switchToChannel(channel);
            }
        });
    }

    public void userJoined(final User user) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                notificationManager.userJoined(user);
            }
        });
    }

    public void userLeft(final User user) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                notificationManager.userLeft(user);
            }
        });
    }


    /**
     * Exit the program.
     */
    private void exit() {
        client.exit();
    }

    public void cleanUp() {
        if (SwingUtilities.isEventDispatchThread()) {
            hotkeyManager.cleanUp();
            setVisible(false);
            dispose();
        }
    }

}
