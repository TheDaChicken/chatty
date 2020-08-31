package chatty.util.api.youtubeObjects.LiveChat;

import chatty.Helper;
import chatty.util.api.Emoticon;
import chatty.util.api.youtubeObjects.LiveChat.actions.BaseAction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.spi.LocationAwareLogger;


import java.util.*;

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

    public Set<Emoticon> getEmotes() {
        Set<Emoticon> emotions = new HashSet<>();
        for(Object obj : (JSONArray) jsonObject.get("emojis")) {
            if(obj instanceof JSONObject) {
                JSONObject emote = (JSONObject) obj;
                String shortcut = (String) ((JSONArray)emote.get("shortcuts")).get(0);
                String searchTerm = (String) ((JSONArray)emote.get("searchTerms")).get(0);
                String id = (String) emote.get("emojiId");

                JSONObject image = (JSONObject) emote.get("image");
                JSONArray thumbnails = (JSONArray) image.get("thumbnails");
                JSONObject thumbnail = (JSONObject) thumbnails.get(0);
                String imageId = Helper.extract_image_id((String) thumbnail.get("url"));

                Emoticon.Builder builder = new Emoticon.Builder(Emoticon.Type.TWITCH,
                        shortcut, (String) thumbnail.get("url"));

                builder.setCreator("YouTube Gaming.");
                builder.setStringId(id);
                builder.setName(searchTerm);
                builder.setStringIdAlias(imageId); // Image ID is just another id ¯\_(ツ)_/¯
                emotions.add(builder.build());
            }
        }
        return emotions;
    }

    public List<BaseAction> getActions() {
        JSONArray actions = (JSONArray) this.jsonObject.get("actions");
        List<BaseAction> result = new ArrayList<>();
        if(actions != null) {
            for (Object o : actions) {
                JSONObject obj = (JSONObject) o;
                Iterator it = obj.entrySet().iterator();
                if(it.hasNext()) {
                    Map.Entry<String,Object> entry = (Map.Entry<String,Object>) it.next();
                    BaseAction action = BaseAction.parseAction(entry.getKey(), (JSONObject) entry.getValue());
                    result.add(action);
                }
            }
        }
        return result;
    }

    public String getViewerName() {
        return (String) this.jsonObject.get("viewerName");
    }

}
