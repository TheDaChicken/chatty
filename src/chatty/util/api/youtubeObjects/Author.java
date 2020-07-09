package chatty.util.api.youtubeObjects;

import org.json.simple.JSONArray;

public class Author {

    private final String name;
    private final String channel_id;
    private final JSONArray badges;

    public Author(String name, String channel_id, JSONArray badges) {
        this.name = name;
        this.channel_id = channel_id;
        this.badges = badges;
    }

    public String getName() {
        return this.name;
    }

    public String getChannelId() {
        return this.channel_id;
    }

    public JSONArray getBadges() { return this.badges; }

}
