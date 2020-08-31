package chatty.util.api.youtubeObjects;

import chatty.util.StringUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ModerationData  {

    public enum Type {
        AUTOMOD_REJECTED, AUTOMOD_APPROVED, AUTOMOD_DENIED, OTHER, UNMODDED
    }

    public final long created_at = System.currentTimeMillis();

    /**
     * The name of the action. Can never be null.
     */
    public final String moderation_action;

    /**
     * The name of the user this action orginiated from. Can never be null.
     */
    public final String created_by;

    /**
     * The stream/room this action originated in. May be null if some kind of
     * error occured.
     */
    public final String stream;

    /**
     * The msg_id value. If not present, an empty value.
     */
    public final String msgId;


    /**
     * Banned username or unbanned username
     */
    public String username;

    /**
     * Banned channelname
     */
    public String channel_id;

    /**
     * The args associated with this action. An empty list if not present.
     */
    public final List<String> args;

    /**
     * Determine some known types of actions (but not all).
     */
    public final Type type;

    public final int timed_out_seconds;

    public ModerationData(String stream_name, String created_by, String msgId, String moderation_action, String username, List<String> args,
                          int timed_out_seconds) {
        this.moderation_action = moderation_action;
        this.created_by = created_by;
        this.msgId = msgId;
        this.stream = stream_name;
        this.username = username;
        this.args = args;
        this.timed_out_seconds = timed_out_seconds;

        switch (moderation_action) {
            case "twitchbot_rejected":
            case "automod_rejected":
            case "rejected_automod_message":
            case "automod_cheer_rejected":
                // Just guessing at this point D:
                type = Type.AUTOMOD_REJECTED;
                break;
            case "approved_automod_message":
                type = Type.AUTOMOD_APPROVED;
                break;
            case "denied_automod_message":
                type = Type.AUTOMOD_DENIED;
                break;
            default:
                type = Type.OTHER;
                break;
        }
    }

    public String getCommandAndParameters() {
        return moderation_action+" "+ "";
    }

}
