package chatty.util.api.youtubeObjects.Pages;

import chatty.Helper;
import chatty.util.api.youtubeObjects.LiveChat.LiveChatResponse;
import org.json.simple.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveChatPage {

    /**
     * Live Page that contains emotes information etc
     */

    private final LiveChatResponse response;

    public LiveChatPage(LiveChatResponse response) {
        this.response = response;
    }

    public LiveChatResponse getLiveChatResponse() {
        return this.response;
    }


    public static LiveChatPage parse(String website_page) {
        JSONObject init_data = Helper.parse_yt_init_data(website_page);
        if(init_data == null) {
            return null;
        }
        //System.out.print("init_data: " + init_data.toJSONString() + "\n");
        return new LiveChatPage(new LiveChatResponse(init_data));
    }

}
