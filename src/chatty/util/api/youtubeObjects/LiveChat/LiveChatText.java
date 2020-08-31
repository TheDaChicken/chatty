package chatty.util.api.youtubeObjects.LiveChat;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class LiveChatText {

    public final JSONArray array;
    private String temp_string = null;

    public LiveChatText(JSONArray array) {
        this.array = array;
    }

    public String toString() {
        if(temp_string == null) {
            StringBuilder result = new StringBuilder();

            for (Object o : array) {
                JSONObject object = (JSONObject) o;
                if (object.containsKey("text")) {
                    result.append(object.get("text"));
                } else if (object.containsKey("emoji")) {
                    JSONObject emojiInfo = (JSONObject) object.get("emoji");
                    JSONArray shortcuts = (JSONArray) emojiInfo.get("shortcuts");
                    result.append(shortcuts.get(0));
                }
            }
            temp_string = result.toString();
        }
        return this.temp_string;
    }

    public JSONArray getArray() {
        return this.array;
    }

}
