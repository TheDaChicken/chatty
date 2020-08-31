
package chatty;

import chatty.gui.colors.UsercolorManager;
import chatty.util.api.usericons.UsericonManager;
import chatty.util.StringUtil;
import chatty.util.settings.Settings;
import java.util.Map.Entry;
import java.util.*;
import java.util.logging.Logger;

/**
 * Provides methods to get a (maybe new) User object for a channel/username 
 * combination and search for User objects by channel, username etc.
 * 
 * With IRCv3 this doesn't save a lot of state anymore, because every message
 * has the user type etc. it is only saved directly in the User objects.
 * Although it could be useful to add some caching again (e.g. for showing
 * user type in userlist before the user said something).
 * 
 * @author tduva
 */
public class UserManager {

    private static final Logger LOGGER = Logger.getLogger(UserManager.class.getName());
    
    private static final int CLEAR_MESSAGES_TIMER = 1*60*60*1000;
    
    private final Set<UserManagerListener> listeners = new HashSet<>();
    
    private volatile String localChannelID;
    public final User specialUser = new User("[specialUser]", Room.createRegular("[nochannel]"));
    
    private final HashMap<String, HashMap<String, User>> users = new HashMap<>();
    private final HashMap<String, String> cachedColors = new HashMap<>();
    
    private final User errorUser = new User("[Error]", Room.createRegular("#[error]"));
    
    // Stupid hack to get Usericons in ChannelTextPane without a user (twitchnotify messages)
    public final User dummyUser = new User("", Room.createRegular("#[error]"));

    private CustomNames customNamesManager;
    private UsericonManager usericonManager;
    private UsercolorManager usercolorManager;
    private Addressbook addressbook;
    private Settings settings;
    
    public UserManager() {
        Timer clearMessageTimer = new Timer("Clear User Messages", true);
        clearMessageTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                clearMessagesOfInactiveUsers();
            }
        }, CLEAR_MESSAGES_TIMER, CLEAR_MESSAGES_TIMER);
    }
    
    public void setLocalChannelID(String channelID) {
        this.localChannelID = channelID;
    }

    public void addListener(UserManagerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void userUpdated(User user) {
        for (UserManagerListener listener : listeners) {
            listener.userUpdated(user);
        }
    }

    public  void updateRoom(Room room) {
        Map<String, User> data = getUsersByChannel(room.getChannel());
        for (User user : data.values()) {
            user.setRoom(room);
        }
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void setUsericonManager(UsericonManager manager) {
        usericonManager = manager;
        dummyUser.setUsericonManager(manager);
    }

    public void setUsercolorManager(UsercolorManager manager) {
        usercolorManager = manager;
    }

    public void setAddressbook(Addressbook addressbook) {
        this.addressbook = addressbook;
    }

    public void setCustomNamesManager(CustomNames m) {
        if (m != null) {
            this.customNamesManager = m;
            m.addListener(new CustomNames.CustomNamesListener() {

                @Override
                public void setName(String channel_id, String customNick) {
                    List<User> users = getUsersByChannelID(channel_id);
                    for (User user : users) {
                        user.setCustomNick(customNick);
                        userUpdated(user);
                    }
                }
            });
        }
    }


    /**
     * Gets a Map of all User objects in the given channel.
     *
     * @param channel
     * @return
     */
    public synchronized HashMap<String, User> getUsersByChannel(String channel) {
        HashMap<String, User> result = users.computeIfAbsent(channel, k -> new HashMap<>());
        return result;
    }

    /**
     * Searches all channels for the given username and returns a List of all
     * the associated User objects. Does not create User object, only return
     * existing ones.
     *
     * @param channel_id The channel id to search for
     * @return The List of User-objects.
     */
    public synchronized List<User> getUsersByChannelID(String channel_id) {
        List<User> result = new ArrayList<>();
        for (HashMap<String, User> channelUsers : users.values()) {
            User user = channelUsers.get(channel_id);
            if (user != null) {
                result.add(user);
            }
        }
        return result;
    }


    /**
     * Returns the user for the given channel and name, but only if an object
     * already exists.
     *
     * @param channel
     * @param channel_id
     * @return The {@code User} object or null if none exists
     */
    public synchronized User getUserIfExists(String channel, String channel_id) {
        return getUsersByChannel(channel).get(channel_id);
    }

    public synchronized User getUserFromUsername(Room room, String name) {
        for (Entry<String, User> entry : getUsersByChannel(room.getChannel()).entrySet()) {
            if (entry.getValue() != null) {
                if(entry.getValue().getName().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns the User with the given name or creates a new User object if none
     * exists for this name.
     *
     * @param room
     * @param name The name of the user
     * @return The matching User object
     * @see User
     */
    public synchronized User getUser(Room room, String channel_id, String name) {
        // Not sure if this makes sense
        if (channel_id == null || channel_id.isEmpty()) {
            return errorUser;
        }
        User user = getUserIfExists(room.getChannel(), channel_id);
        if (user == null) {
            if(name == null) {
                /*
                    Not allowed to create user without a name variable
                 */
                return null;
            }
            user = new User(channel_id, name, room);
            user.setUsercolorManager(usercolorManager);
            user.setAddressbook(addressbook);
            user.setUsericonManager(usericonManager);
            // Initialize some values if present for this name
            if (cachedColors.containsKey(channel_id)) {
                user.setColor(cachedColors.get(channel_id));
            }
            if (channel_id.equals(localChannelID)) {
                user.setId(channel_id);
                user.setLocalUser(true);
                if (!specialUser.hasDefaultColor()) {
                    user.setColor(specialUser.getPlainColor());
                }
            }
            // Put User into the map for the channel
            getUsersByChannel(room.getChannel()).put(channel_id, user);
        }
        return user;
    }


    /**
     * Searches all channels for the given username and returns a Map with
     * all channels the username was found in and the associated User objects.
     *
     * @param name The username to be searched for
     * @return A Map with channel->User association
     */
    public synchronized HashMap<String,User> getChannelsAndUsersByUserName(String name) {
        HashMap<String,User> result = new HashMap<>();

        Iterator<Entry<String, HashMap<String, User>>> it = users.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, HashMap<String, User>> channel = it.next();

            String channelName = channel.getKey();
            HashMap<String,User> channelUsers = channel.getValue();

            User user = channelUsers.get(name);
            if (user != null) {
                result.put(channelName,user);
            }
        }
        return result;
    }

    /**
     * Remove all users.
     */
    public synchronized void clear() {
        users.clear();
    }

    /**
     * Remove all users of the given channel.
     *
     * @param channel
     */
    public synchronized void clear(String channel) {
        getUsersByChannel(channel).clear();
    }

    public synchronized void clearMessagesOfInactiveUsers() {
        if (settings == null) {
            return;
        }
        long clearUserMessages = settings.getLong("clearUserMessages");
        if (clearUserMessages >= 0) {
            int numRemoved = 0;
            for (Map<String, User> chan : users.values()) {
                for (User user : chan.values()) {
                    numRemoved += user.clearMessagesIfInactive(clearUserMessages*60*60*1000);
                }
            }
            LOGGER.info("Cleared "+numRemoved+" user messages");
        }
    }

    /**
     * Set all users offline.
     */
    public synchronized void setAllOffline() {
        Iterator<HashMap<String,User>> it = users.values().iterator();
        while (it.hasNext()) {
            setAllOffline(it.next());
        }
    }

    /**
     * Set all users of the given channel offline.
     *
     * @param channel
     */
    public synchronized void setAllOffline(String channel) {
        if (channel == null) {
            setAllOffline();
        }
        Map<String, User> usersInChannel = users.get(channel);
        if (usersInChannel != null) {
            setAllOffline(usersInChannel);
        }
    }


    /**
     * Set all given users offline. Helper method.
     *
     * @param usersInChannel
     */
    private void setAllOffline(Map<String, User> usersInChannel) {
        for (User user : usersInChannel.values()) {
            user.setOnline(false);
        }
    }


    /**
     * Sets the color of a user across all channels.
     *
     * @param channel_id String The name of the user
     * @param color String The color as a string representation
     */
    protected synchronized void setColorForUsername(String channel_id, String color) {
        cachedColors.put(channel_id,color);

        List<User> userAllChans = getUsersByChannelID(channel_id);
        for (User user : userAllChans) {
            user.setColor(color);
        }
    }

    /**
     * The list of mods received with channel context, set the containing names
     * as mod. Returns the changed users so they can be updated in the GUI.
     *
     * @param channel
     * @param modsList
     * @return
     */
    protected synchronized List<User> modsListReceived(Room room, List<String> modsList) {
        // Demod everyone on the channel
        Map<String,User> usersToDemod = getUsersByChannel(room.getChannel());
        for (User user : usersToDemod.values()) {
            user.setModerator(false);
        }
        // Mod everyone in the list
        LOGGER.info("Setting users as mod for "+room.getChannel()+": "+modsList);
        List<User> changedUsers = new ArrayList<>();
        for (String userName : modsList) {
            if (Helper.isValidChannel(userName)) {
                User user = getUser(room, userName, null);
                if (user.setModerator(true)) {
                    userUpdated(user);
                }
                changedUsers.add(user);
            }
        }
        return changedUsers;
    }

    public static interface UserManagerListener {
        public void userUpdated(User user);
    }
    
}
