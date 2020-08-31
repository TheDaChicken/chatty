package chatty.util.api.youtubeObjects.LiveChat.actions;

import org.json.simple.JSONObject;

public class ActionItem {

    private final String type;
    private final String id;

    public ActionItem(String type, JSONObject jsonObject) {
        this.type = type;
        this.id = (String) jsonObject.get("id");
    }

    public String getType() {
        return this.type;
    }

    public String getId() {
        return this.id;
    }

}
