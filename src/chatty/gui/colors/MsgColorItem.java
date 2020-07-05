
package chatty.gui.colors;

import chatty.Addressbook;
import chatty.User;
import chatty.util.irc.MsgTags;
import java.awt.Color;

/**
 *
 * @author tduva
 */
public class MsgColorItem extends ColorItem {


    public MsgColorItem(String item,
            Color foreground, boolean foregroundEnabled,
            Color background, boolean backgroundEnabled) {
        super(item, foreground, foregroundEnabled, background, backgroundEnabled);
    }

}
