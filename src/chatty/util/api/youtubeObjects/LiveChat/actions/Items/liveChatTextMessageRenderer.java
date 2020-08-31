package chatty.util.api.youtubeObjects.LiveChat.actions.Items;

import chatty.util.api.youtubeObjects.LiveChat.LiveChatText;
import chatty.util.api.youtubeObjects.LiveChat.actions.ActionItem;
import chatty.util.api.youtubeObjects.LiveChat.actions.ChatItemAction;
import chatty.util.irc.MsgTags;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class liveChatTextMessageRenderer extends BaseMessageItem {

    private JSONObject item;

    public liveChatTextMessageRenderer(ChatItemAction action, JSONObject jsonObject) {
        super(action, "liveChatTextMessageRenderer", jsonObject);
        this.item = jsonObject;
    }

    public String getAuthorName() {
        JSONObject jsonObject = (JSONObject) item.get("authorName");
        return (String) jsonObject.get("simpleText");
    }

    public String getAuthorChannelId() {
        return (String) item.get("authorExternalChannelId");
    }


    public String getBadges() {
        if(item.get("authorBadges") == null) {
            return null;
        }
        return ((JSONArray) item.get("authorBadges")).toJSONString();
    }

    public MsgTags parse() {
        Map<String, String> tags_map = new HashMap<>();
        tags_map.put("id", getId());
        tags_map.put("badges", getBadges());
        tags_map.put("user-id", getAuthorChannelId());
        return new MsgTags(tags_map);
    }

}
