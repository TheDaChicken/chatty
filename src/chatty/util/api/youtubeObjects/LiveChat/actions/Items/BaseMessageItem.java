package chatty.util.api.youtubeObjects.LiveChat.actions.Items;

import chatty.util.api.youtubeObjects.LiveChat.LiveChatText;
import chatty.util.api.youtubeObjects.LiveChat.actions.ActionItem;
import chatty.util.api.youtubeObjects.LiveChat.actions.ChatItemAction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class BaseMessageItem extends ActionItem {

    private ChatItemAction action;
    private final JSONObject item;

    public BaseMessageItem(ChatItemAction action, String name, JSONObject item) {
        super(name, item);
        this.item = item;
        this.action = action;
    }

    public LiveChatText getMessage() {
        JSONObject message = (JSONObject) item.get("message");
        JSONArray runs = (JSONArray) message.get("runs");
        return new LiveChatText(runs);
    }

    public ChatItemAction getAction() {
        return this.action;
    }

}
