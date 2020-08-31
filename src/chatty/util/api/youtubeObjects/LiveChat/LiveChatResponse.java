package chatty.util.api.youtubeObjects.LiveChat;

import org.json.simple.JSONObject;

public class LiveChatResponse {

    public final JSONObject response;

    public LiveChatResponse(JSONObject jsonObject) {
        this.response = jsonObject;
    }

    public boolean isLiveChatClosed() {
        return response.get("continuationContents") == null;
    }

    public LiveChatContinuation getLiveChatContinuation() {
        JSONObject continuationContents = (JSONObject) response.get("continuationContents");
        JSONObject liveChatContinuation = (JSONObject) continuationContents.get("liveChatContinuation");
        return new LiveChatContinuation(liveChatContinuation);
    }

}
