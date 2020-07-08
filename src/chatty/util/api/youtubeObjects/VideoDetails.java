package chatty.util.api.youtubeObjects;

public class VideoDetails {

    public final String video_id;
    public final String title;
    public final String channel_id;

    public VideoDetails(String video_id, String title, String channel_id) {
        this.video_id = video_id;
        this.title = title;
        this.channel_id = channel_id;
    }
}
