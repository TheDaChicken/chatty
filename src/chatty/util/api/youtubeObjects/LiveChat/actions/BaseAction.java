package chatty.util.api.youtubeObjects.LiveChat.actions;

import org.json.simple.JSONObject;

public class BaseAction {

    private final String unrecognized;

    public BaseAction(String unrecognized) {
        this.unrecognized = unrecognized;
    }

    public String getUnrecognized() {
        return this.unrecognized;
    }

    public static BaseAction parseAction(String action_name, JSONObject jsonObject) {
        switch(action_name) {
            case "addChatItemAction": {
                return new ChatItemAction(jsonObject);
            }
            case "markChatItemAsDeletedAction": {
                return new ChatItemAsDeletedAction(jsonObject);
            }
            case "markChatItemsByAuthorAsDeletedAction": {
                return new ChatItemsByAuthorAsDeletedAction(jsonObject);
            }
            default:
                return new BaseAction(action_name);
        }
    }


}
