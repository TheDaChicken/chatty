package chatty.util.api.youtubeObjects.LiveChat;

public class LiveChatMessages {

    public String video_id;
    public String continuation;

    public LiveChatMessages() {
    }

    public void setVideoId(String video_id) {
        this.video_id = video_id;
    }

    public LiveChatMessages setContinuation(String continuation) {
        this.continuation = continuation;
        return this;
    }

    public LiveChatRequest build() {
        return new LiveChatRequest(this.video_id, this.continuation);
    }

}
