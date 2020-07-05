
package chatty.util.api;

import chatty.Room;
import chatty.util.api.usericons.Usericon;
import chatty.util.api.YouTubeApi.RequestResultCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

import java.util.List;
import java.util.Set;

/**
 * Interface definition for API response results.
 * 
 * @author tduva
 */
public interface TwitchApiResultListener {
    //void receivedEmoticons(EmoticonUpdate emoteUpdate);
    //void receivedCheerEmoticons(Set<CheerEmoticon> emoticons);
    void receivedUsericons(List<Usericon> icons);
    void tokenVerified(GoogleCredential credential, TokenInfo tokenInfo);
    void tokenRevoked(String error);
    void runCommercialResult(String stream, String text, RequestResultCode result);
    void putChannelInfoResult(RequestResultCode result);
    void receivedChannelInfo(String channel, ChannelInfo info, RequestResultCode result);
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
