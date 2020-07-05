
package chatty.gui.components.userinfo;

import chatty.util.api.ChannelInfo;

/**
 *
 * @author tduva
 */
public interface UserInfoRequester {

    ChannelInfo getCachedChannelInfo(String channel, String id);
}
