package chatty.util.api.youtubeObjects.LiveChat.actions;

import chatty.util.api.youtubeObjects.ModerationData;
import chatty.util.irc.MsgTags;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ChatItemsByAuthorAsDeletedAction extends BaseAction {

    private final JSONObject action;

    public ChatItemsByAuthorAsDeletedAction(JSONObject jsonObject) {
        super("markChatItemsByAuthorAsDeletedAction");
        this.action = jsonObject;
    }

    public String getTargetChannelId() {
        return (String) action.get("externalChannelId");
    }

    public MsgTags parse() {
        Map<String, String> tags_map = new HashMap<>();
        return new MsgTags(tags_map);
    }

}
