package chatty.util.api.youtubeObjects;

import chatty.util.JSONUtil;
import org.json.simple.JSONObject;

public class YouTubePlayerConfig {

    private JSONObject player_config;

    public YouTubePlayerConfig(JSONObject jsonObject) {
        this.player_config = jsonObject;
    }

    public PlayerResponse getPlayerResponse() {
        JSONObject args = (JSONObject) player_config.get("args");
        String player_response = (String) args.get("player_response");
        return new PlayerResponse((JSONObject) JSONUtil.parseJSON(player_response));
    }

}
