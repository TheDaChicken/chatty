package chatty.util.api.youtubeObjects.LiveChat;

import org.json.simple.JSONObject;

public class LiveChatTimedContinuationData {

    public final JSONObject timedContinuationData;

    public LiveChatTimedContinuationData(JSONObject timedContinuationData) {
        this.timedContinuationData = timedContinuationData;
    }


    public long getPollingIntervalMillis() {
        return (long) timedContinuationData.get("timeoutMs");
    }

    public String getContinuation() {
        return (String) timedContinuationData.get("continuation");
    }



}
