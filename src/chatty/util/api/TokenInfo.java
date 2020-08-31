
package chatty.util.api;

/**
 * Holds info (username, scopes) about the current access token.
 * 
 * @author tduva
 */
public class TokenInfo {

    public final String channel_name;
    public final String channel_id;
    public final boolean valid;
    
    public TokenInfo() {
        valid = false;
        channel_name = null;
        channel_id = null;
    }
    
    public TokenInfo(String channel_name, String channel_id) {
        this.channel_name = channel_name;
        this.channel_id = channel_id;
        valid = true;
    }
}
