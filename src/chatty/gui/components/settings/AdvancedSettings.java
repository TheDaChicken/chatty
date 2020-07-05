
package chatty.gui.components.settings;

import chatty.gui.components.LinkLabel;
import java.awt.GridBagConstraints;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Stuff.
 * 
 * @author tduva
 */
public class AdvancedSettings extends SettingsPanel {
    
    public AdvancedSettings(final SettingsDialog d) {

        JPanel connection = addTitledPanel("Connection", 1);

        connection.add(new JLabel("Server:"),
                d.makeGbc(0, 0, 1, 1, GridBagConstraints.EAST));
        connection.add(d.addSimpleStringSetting("serverDefault", 20, true),
                d.makeGbc(1, 0, 1, 1, GridBagConstraints.WEST));
        
        connection.add(new JLabel("Ports:"),
                d.makeGbc(0, 1, 1, 1, GridBagConstraints.EAST));
        connection.add(d.addSimpleStringSetting("portDefault", 14, true),
                d.makeGbc(1, 1, 1, 1, GridBagConstraints.WEST));
        
        connection.add(new JLabel("(These might be overridden by commandline parameters.)"),
                d.makeGbc(0, 2, 2, 1));
        
        connection.add(d.addSimpleBooleanSetting("membershipEnabled",
                "Correct Userlist (receives joins/parts, userlist)",
                "Enables the membership capability while connecting, which allows receiving of joins/parts/userlist"),
                d.makeGbc(0, 4, 2, 1, GridBagConstraints.NORTHWEST));
        
        JPanel login = addTitledPanel("Login Settings (login under <Main Menu - Login>)", 2);
        
        login.add(d.addSimpleBooleanSetting("allowTokenOverride",
                "<html><body>Allow <code>-token</code> parameter to override existing token", 
                "If enabled, the -token commandline argument will replace an existing token (which can cause issues)"),
                d.makeGbc(0, 5, 2, 1, GridBagConstraints.WEST));
    }
}
