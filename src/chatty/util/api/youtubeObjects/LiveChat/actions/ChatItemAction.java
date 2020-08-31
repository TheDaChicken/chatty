package chatty.util.api.youtubeObjects.LiveChat.actions;

import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatModerationMessageRenderer;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatTextMessageRenderer;
import chatty.util.api.youtubeObjects.LiveChat.actions.Items.liveChatViewerEngagementMessageRenderer;
import org.json.simple.JSONObject;
import org.slf4j.spi.LocationAwareLogger;

import java.util.Iterator;
import java.util.Map;

public class ChatItemAction extends BaseAction {

    private JSONObject action;

    public ChatItemAction(JSONObject action) {
        super("addChatItemAction");
        this.action = action;
    }

    public ActionItem getItem() {
        JSONObject item = (JSONObject) action.get("item");
        Iterator it = item.entrySet().iterator();
        Map.Entry<String, Object> entry = null;
        if(it.hasNext()) {
            entry = (Map.Entry<String,Object>) it.next();
            switch(entry.getKey()) {
                case "liveChatTextMessageRenderer": {
                    return new liveChatTextMessageRenderer(this, (JSONObject) entry.getValue());
                }
                case "liveChatViewerEngagementMessageRenderer": {
                    return new liveChatViewerEngagementMessageRenderer(this, (JSONObject) entry.getValue());
                }
                case "liveChatModerationMessageRenderer": {
                    return new liveChatModerationMessageRenderer(this, (JSONObject) entry.getValue());
                }
            }
        }
        return new ActionItem("", new JSONObject());
    }

    public String getClientId() {
        return (String) action.get("clientId");
    }



}
