
package chatty.gui.components;

import chatty.gui.MainGui;
import chatty.lang.Language;
import org.slf4j.spi.LocationAwareLogger;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 *
 * @author tduva
 */
public class TokenDialog extends JDialog {
    
    private final static ImageIcon OK_IMAGE = new ImageIcon(TokenDialog.class.getResource("ok.png"));
    private final static ImageIcon NO_IMAGE = new ImageIcon(TokenDialog.class.getResource("no.png"));
    
    private final JLabel nameLabel = new JLabel(Language.getString("login.accountName"));
    private final JLabel name = new JLabel("<no account>");
    private final LinkLabel accessLabel;
    private final JPanel access;
    
    private final Map<String, JLabel> accessScopes = new HashMap<>();
    
    private final JButton deleteToken = new JButton(Language.getString("login.button.removeLogin"));
    private final JButton requestToken = new JButton(Language.getString("login.button.requestLogin"));
    private final JButton verifyToken = new JButton(Language.getString("login.button.verifyLogin"));
    private final LinkLabel tokenInfo;
    private final LinkLabel foreignTokenInfo;
    private final LinkLabel otherInfo;
    private final JButton done = new JButton(Language.getString("dialog.button.close"));
    
    private String currentUsername = "";
    private String currentCookies = null;
    
    public TokenDialog(MainGui owner) {
        super(owner, Language.getString("login.title"), true);
        this.setResizable(false);
       
        this.setLayout(new GridBagLayout());
        
        accessLabel = new LinkLabel("Access: [help:login (help)]", owner.getLinkLabelListener());
        //tokenInfo = new JLabel();
        tokenInfo = new LinkLabel("", owner.getLinkLabelListener());
        foreignTokenInfo = new LinkLabel("<html><body style='width:170px'>"
                    + "Login data set externally with -token parameter.", owner.getLinkLabelListener());
        foreignTokenInfo.setVisible(false);
        otherInfo = new LinkLabel("<html><body style='width:170px'>To add or "
                + "reduce access remove login and request again.", owner.getLinkLabelListener());
        
        GridBagConstraints gbc;
        
        add(nameLabel, makeGridBagConstraints(0,0,1,1,GridBagConstraints.WEST));
        add(name, makeGridBagConstraints(0,1,2,1,GridBagConstraints.CENTER,new Insets(0,5,5,5)));
        
        add(accessLabel, makeGridBagConstraints(0,2,1,1,GridBagConstraints.WEST));
        
        access = new JPanel();
        access.setLayout(new GridBagLayout());
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.anchor = GridBagConstraints.WEST;

        gbc = makeGridBagConstraints(0,3,2,1,GridBagConstraints.CENTER,new Insets(0,5,5,5));
        add(access, gbc);
        
        gbc = makeGridBagConstraints(0, 4, 2, 1, GridBagConstraints.WEST);
        add(otherInfo, gbc);
        
        gbc = makeGridBagConstraints(0,5,2,1,GridBagConstraints.WEST);
        add(tokenInfo, gbc);
        
        gbc = makeGridBagConstraints(0,6,2,1,GridBagConstraints.WEST);
        add(foreignTokenInfo, gbc);
        
        gbc = makeGridBagConstraints(0,7,1,1,GridBagConstraints.WEST);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(deleteToken, gbc);
        
        gbc = makeGridBagConstraints(0,7,2,1,GridBagConstraints.CENTER);
        add(requestToken, gbc);
        
        gbc = makeGridBagConstraints(1,7,1,1,GridBagConstraints.WEST);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(verifyToken, gbc);
        
        gbc = makeGridBagConstraints(0,8,2,1,GridBagConstraints.EAST);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(done, gbc);
        
        ActionListener actionListener = owner.getActionListener();
        requestToken.addActionListener(actionListener);
        deleteToken.addActionListener(actionListener);
        verifyToken.addActionListener(actionListener);
        done.addActionListener(actionListener);

        pack();
    }
    
    public JButton getRequestTokenButton() {
        return requestToken;
    }
    
    public JButton getDeleteTokenButton() {
        return deleteToken;
    }
    
    public JButton getVerifyTokenButton() {
        return verifyToken;
    }
    
    public JButton getDoneButton() {
        return done;
    }
    
    public void update() {
        boolean empty = currentUsername.isEmpty() || currentCookies == null;
        deleteToken.setVisible(!empty);
        requestToken.setVisible(empty);
        verifyToken.setVisible(!empty);
        otherInfo.setVisible(!empty);
        pack();
    }
    
    public void update(String username, String cookies_json) {
        this.currentUsername = username;
        this.currentCookies = cookies_json;
        if (currentUsername.isEmpty() || cookies_json.isEmpty()) {
            name.setText(Language.getString("login.createLogin"));
        }
        else {
            name.setText(currentUsername);
        }
        //setTokenInfo("");
        update();
    }

    public void update(String username) {
        this.currentUsername = username;
        name.setText(currentUsername);
        //setTokenInfo("");
        update();
    }
    
    /**
     * Change status to verifying token.
     */
    public void verifyingToken() {
        setTokenInfo(Language.getString("login.verifyingLogin"));
        verifyToken.setEnabled(false);
    }
    
    /**
     * Set the result of the token verification (except the scopes).
     * 
     * @param valid
     * @param result 
     */
    public void tokenVerified(boolean valid, String result) {
        setTokenInfo(result);
        verifyToken.setEnabled(true);
        update();
    }
    
    private void setTokenInfo(String info) {
        tokenInfo.setText("<html><body style='width:170px'>"+info);
        pack();
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                pack();
            }
        });
    }
    
    public void setForeignToken(boolean foreign) {
        foreignTokenInfo.setVisible(foreign);
        pack();
    }
    
    private GridBagConstraints makeGridBagConstraints(int x, int y,int w, int h, int anchor, Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = w;
        constraints.gridheight = h;
        constraints.insets = insets;
        constraints.anchor = anchor;
        return constraints;
    }
    
    private GridBagConstraints makeGridBagConstraints(int x, int y,int w, int h, int anchor) {
        return makeGridBagConstraints(x,y,w,h,anchor,new Insets(5,5,5,5));
        
    }
    
}
