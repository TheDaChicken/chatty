package chatty.util.api.youtubeObjects.LiveChat.actions.Items;

import chatty.util.api.youtubeObjects.LiveChat.actions.ChatItemAction;
import chatty.util.api.youtubeObjects.ModerationData;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class liveChatModerationMessageRenderer extends BaseMessageItem {

    private final JSONObject item;

    public liveChatModerationMessageRenderer(ChatItemAction action, JSONObject item) {
        super(action, "liveChatModerationMessageRenderer", item);
        this.item = item;
    }


    public ModerationData parseModerationData(String stream_name) {
        JSONObject deletedStateMessage = (JSONObject) item.get("message");
        if(deletedStateMessage != null) {
            JSONArray run = (JSONArray) deletedStateMessage.get("runs");

            ArrayList<String> usernames = new ArrayList<>();
            int timed_out_seconds = -3;

            String moderation_action = "";

            for(Object obj : run) {
                JSONObject json = (JSONObject) obj;
                String text = (String) json.get("text");
                if(json.get("bold") != null && (boolean)json.get("bold")) { // Usually the created_by is bolded around the end
                    usernames.add(text);
                } else if(text.chars().allMatch(Character::isDigit)){
                    timed_out_seconds = Integer.parseInt(text);
                } else {
                    if(text.contains("hidden")) {
                        moderation_action = "ban";
                        timed_out_seconds = -1;
                    } else if(text.contains("timed out")) {
                        moderation_action = "timeout";
                    }
                }
            }

            String created_by = "";
            String username = "";

            if(usernames.size() == 1) {
                created_by = usernames.get(0);
            } else if(usernames.size() > 1) {
                username = usernames.get(0);
                created_by = usernames.get(1);
            }

            List<String> args = new ArrayList<>();
            args.add(username);
            args.add(String.valueOf(timed_out_seconds));

            return new ModerationData(stream_name, created_by, null, moderation_action, username, args, timed_out_seconds);
        }
        return null;
    }

}
