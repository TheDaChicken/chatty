package chatty.util.api.youtubeObjects.LiveChat;

import chatty.util.JSONUtil;
import chatty.util.api.RequestBuilder;
import chatty.util.api.RequestResponse;
import org.json.simple.JSONObject;

public class LiveChatRequest {

    /*
        Put under thread. yeaaaaaaaa
     */

    public final String video_id;
    public final String continuation;


    public LiveChatRequest(String video_id, String continuation) {
        this.video_id = video_id;
        this.continuation = continuation;
    }

    public LiveChatResponse execute() {
        RequestResponse response = new RequestBuilder("https://www.youtube.com/live_chat/get_live_chat?commandMetadata=%5Bobject%20Object%5D&continuation=" +
                this.continuation + "&hidden=false&pbj=1").build().execute();
        JSONObject obj = JSONUtil.parseJSON(response.getResponse());
        JSONObject response_ = (JSONObject) obj.get("response");
        return new LiveChatResponse(response_);
    }


}
