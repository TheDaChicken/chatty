package chatty.util.api.youtubeObjects.LiveChat;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class LiveChatText {

    public final JSONArray array;

    public LiveChatText(JSONArray array) {
        this.array = array;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();

        for(Object o : array) {
            JSONObject object = (JSONObject) o;
            if(object.containsKey("text")) {
                result.append(object.get("text"));
            } else if(object.containsKey("emoji")) {
                JSONObject emojiInfo = (JSONObject) object.get("emoji");
                JSONArray shortcuts = (JSONArray) emojiInfo.get("shortcuts");
                result.append(shortcuts.get(0));
            }
        }
        return result.toString();
    }

}
