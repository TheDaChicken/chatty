package chatty.util.api.youtubeObjects;

public class Author {

    public final String name;
    public final String channel_id;

    public Author(String name, String channel_id) {
        this.name = name;
        this.channel_id = channel_id;
    }

    public String getName() {
        return this.name;
    }

    public String getChannelId() {
        return this.channel_id;
    }

}
