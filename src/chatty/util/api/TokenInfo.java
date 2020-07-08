
package chatty.util.api;

/**
 * Holds info (username, scopes) about the current access token.
 * 
 * @author tduva
 */
public class TokenInfo {

    public final String name;
    public final String userId;
    public final boolean valid;
    
    public TokenInfo() {
        valid = false;
        name = null;
        userId = null;
    }
    
    public TokenInfo(String name, String userId) {
        this.name = name;
        this.userId = userId;
        valid = true;
    }
}
