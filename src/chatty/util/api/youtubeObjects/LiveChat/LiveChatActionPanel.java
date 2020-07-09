package chatty.util.api.youtubeObjects.LiveChat;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class LiveChatActionPanel {

    private final JSONObject jsonObject;

    public LiveChatActionPanel(JSONObject actionPanel) {
        this.jsonObject = actionPanel;
    }

    public JSONArray getBadges() {
        return (JSONArray) jsonObject.get("authorBadges");
    }


}
