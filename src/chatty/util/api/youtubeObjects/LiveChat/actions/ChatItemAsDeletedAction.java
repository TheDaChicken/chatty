package chatty.util.api.youtubeObjects.LiveChat.actions;

import chatty.util.api.youtubeObjects.ModerationData;
import chatty.util.irc.MsgTags;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

public class ChatItemAsDeletedAction extends BaseAction {

    private final JSONObject action;

    public ChatItemAsDeletedAction(JSONObject jsonObject) {
        super("markChatItemAsDeletedAction");
        this.action = jsonObject;
    }

    public String getTargetItemId() {
        return (String) action.get("targetItemId");
    }

    public MsgTags parse() {
        Map<String, String> tags_map = new HashMap<>();
        tags_map.put("target-msg-id", getTargetItemId());
        return new MsgTags(tags_map);
    }

    public ModerationData parseModerationData(String stream_name) {
        JSONObject deletedStateMessage = (JSONObject) action.get("deletedStateMessage");
        JSONArray run = (JSONArray) deletedStateMessage.get("runs");

        String created_by = "";
        for(Object obj : run) {
            JSONObject json = (JSONObject) obj;
            String text = (String) json.get("text");
            if(json.get("bold") != null && (boolean)json.get("bold")) { // Usually the created_by is bolded around the end
                created_by = text;
            }
        }

        List<String> args = new ArrayList<>();
        args.add(getTargetItemId());

        return new ModerationData(stream_name, created_by, getTargetItemId(), "delete", null, args, -2);
    }

}
