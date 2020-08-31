package chatty.util.api.youtubeObjects;

import org.json.simple.JSONObject;

public class PlayerResponse {

    private final JSONObject player_response;

    public PlayerResponse(JSONObject jsonObject) {
        this.player_response = jsonObject;
    }

    public VideoDetails getVideoDetails() {
        JSONObject videoDetails = (JSONObject) player_response.get("videoDetails");
        return new VideoDetails(videoDetails);
    }

}
