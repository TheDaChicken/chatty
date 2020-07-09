package chatty.util.api.youtubeObjects.LiveChat;

import chatty.util.JSONUtil;
import chatty.util.api.YouTubeWeb;
import org.json.simple.JSONObject;

import java.util.regex.Matcher;

public class LiveChatPage {

    /**
     * Live Page that contains emotes information etc
     */

    public static LiveChatResponse parse(String continuation, String website_page) {
        JSONObject init_data = YouTubeWeb.getInitData(website_page);
        System.out.print(init_data.toJSONString() + "\n");
        if(init_data == null) {
            return null;
        }
        return new LiveChatResponse(init_data);
    }

}
