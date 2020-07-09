package chatty.util.api.youtubeObjects.LiveChat;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LiveChatContinuation {

    public final JSONObject jsonObject;

    public LiveChatContinuation(JSONObject liveChatContinuation) {
        this.jsonObject = liveChatContinuation;
    }

    public LiveChatTimedContinuationData getTimedContinuationData() {
        JSONArray continuationsList = (JSONArray) this.jsonObject.get("continuations");
        JSONObject continuations = (JSONObject) continuationsList.get(0);
        for (Map.Entry<String, JSONObject> t : (Set<Map.Entry<String, JSONObject>>) continuations.entrySet()) {
            return new LiveChatTimedContinuationData(t.getValue());
        }
        return null;
    }

    public JSONArray getEmotes() {
        JSONArray emotes = (JSONArray) this.jsonObject.get("emojis");
        return emotes;
    }

    public List<LiveChatAction> getActions() {
        JSONArray actions = (JSONArray) this.jsonObject.get("actions");
        List<LiveChatAction> result = new ArrayList<>();
        if(actions != null) {
            for (Object o : actions) {
                JSONObject obj = (JSONObject) o;
                for(Map.Entry<String, JSONObject> entrySet : (Set<Map.Entry<String, JSONObject>>) obj.entrySet()) {
                    JSONObject addChatItemAction = entrySet.getValue();
                    JSONObject item = (JSONObject) addChatItemAction.get("item");
                    if(item == null) {
                        LiveChatAction action = new LiveChatAction(entrySet.getKey(), entrySet.getValue());
                        result.add(action);
                    } else {
                        for (Map.Entry<String, JSONObject> subEntrySet : (Set<Map.Entry<String, JSONObject>>) item.entrySet()) {
                            LiveChatAction action = new LiveChatAction(subEntrySet.getKey(), subEntrySet.getValue());
                            result.add(action);
                        }
                    }
                }
            }
        }
        return result;
    }

    public String getViewerName() {
        return (String) this.jsonObject.get("viewerName");
    }

    public LiveChatActionPanel getPanel() {
        JSONObject actionPanel = (JSONObject) this.jsonObject.get("actionPanel");
        if(actionPanel.keySet().size() == 0) {
            return null;
        }
        String actionName = (String) actionPanel.keySet().toArray()[0];
        JSONObject jsonObject = (JSONObject) actionPanel.get(actionName);

        return new LiveChatActionPanel(jsonObject);
    }

}
