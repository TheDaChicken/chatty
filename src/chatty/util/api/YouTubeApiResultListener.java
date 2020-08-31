
package chatty.util.api;

import chatty.util.api.usericons.Usericon;

import java.net.HttpCookie;
import java.util.List;

/**
 * Interface definition for API response results.
 * 
 * @author tduva
 */
public interface YouTubeApiResultListener {
    //void receivedEmoticons(EmoticonUpdate emoteUpdate);
    //void receivedCheerEmoticons(Set<CheerEmoticon> emoticons);
    void cookiesVerified(String json_cookies, TokenInfo tokenInfo);
    void tokenRevoked(String error);
    //void runCommercialResult(String stream, String text, RequestResultCode result);
    //void putChannelInfoResult(RequestResultCode result);
    //void receivedChannelInfo(String channel, ChannelInfo info, RequestResultCode result);
    void accessDenied();
    //void receivedFollower(String stream, String username, RequestResultCode result, Follower follower);
    /**
     * The correctly capitalized name for a user.
     * 
     * @param name All-lowercase name
     * @param displayName Correctly capitalized name
     */
    void receivedDisplayName(String name, String displayName);
    
    void receivedServer(String channel, String server);
    
    /**
     * Human-readable result message.
     * 
     * @param message 
     */
    void followResult(String message);
    
    void autoModResult(String result, String msgId);
}
