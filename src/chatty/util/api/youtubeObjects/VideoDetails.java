package chatty.util.api.youtubeObjects;

import org.json.simple.JSONObject;

public class VideoDetails {

    private final String video_id;
    private final String channel_id;

    public VideoDetails(JSONObject videoDetails) {
        this.video_id = (String) videoDetails.get("videoId");
        this.channel_id = (String) videoDetails.get("channelId");
    }

    public String getVideoId() {
        return this.video_id;
    }

    public String getChannelId() {
        return this.channel_id;
    }

}
