package chatty.util.api.youtubeObjects.LiveChat.actions.Items;

import chatty.util.api.youtubeObjects.LiveChat.actions.ChatItemAction;
import org.json.simple.JSONObject;

public class liveChatViewerEngagementMessageRenderer extends BaseMessageItem {

    public liveChatViewerEngagementMessageRenderer(ChatItemAction action, JSONObject item) {
        super(action, "liveChatViewerEngagementMessageRenderer", item);
    }
}
