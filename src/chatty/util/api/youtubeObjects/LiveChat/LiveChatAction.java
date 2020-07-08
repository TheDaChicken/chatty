package chatty.util.api.youtubeObjects.LiveChat;

import chatty.util.api.youtubeObjects.Author;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class LiveChatAction {

    public final String type;
    public final JSONObject json;


    public LiveChatAction(String type, JSONObject json) {
        this.type = type;
        this.json = json;
    }

    public String getType() {
        return type;
    }

    public LiveChatText getMessage() {
        JSONObject message = (JSONObject) json.get("message");
        JSONArray runs = (JSONArray) message.get("runs");
        return new LiveChatText(runs);
    }

    public String getId() {
        return (String) json.get("id");
    }

    public Author getAuthorDetails() {
        JSONObject authorName = (JSONObject) json.get("authorName");
        String author_name = (String) authorName.get("simpleText");
        String channel_id = (String) json.get("authorExternalChannelId");

        return new Author(author_name, channel_id);
    }

    public String getTargetMessageId() {
        return (String) json.get("targetItemId");
    }


}
