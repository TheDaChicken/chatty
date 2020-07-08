package chatty.gui.components;

import chatty.gui.MainGui;
import chatty.lang.Language;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CookiesPasteDialog extends JDialog implements ItemListener, ActionListener  {

    private static final String INFO = "<html><body>Login using Data from Browser ([help:login ?]):<br />"
            + "1. Login to Google Account on a browser<br />"
            + "2. Export Cookies using a browser extension as JSON<br />"
            + "3. Paste Exported cookies here.";
    private final LinkLabel info;
    private final JTextArea jsonField = new JTextArea(5, 20);
    private final JLabel status = new JLabel();

    private final JButton saveButton = new JButton("Parse & Save");
    private final JButton close = new JButton("Close");

    public CookiesPasteDialog(MainGui owner) {
        super(owner, Language.getString("login.title"), true);
        this.setResizable(false);
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(owner.getWindowListener());

        info = new LinkLabel(INFO, owner.getLinkLabelListener());

        setLayout(new GridBagLayout());

        GridBagConstraints gbc;
        gbc = makeGridBagConstraints(0,0,2,1,GridBagConstraints.CENTER);
        gbc.insets = new Insets(5,5,10,5);
        add(info, gbc);

        int y = 1;

        // URL Display and Buttons
        gbc = makeGridBagConstraints(0,y+1,2,1,GridBagConstraints.CENTER);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(10,5,10,5);
        jsonField.setEditable(true);
        JScrollPane urlFieldScroll = new JScrollPane(jsonField);
        urlFieldScroll.setPreferredSize(new Dimension(150, 150));
        urlFieldScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        add(urlFieldScroll, gbc);

        add(status,makeGridBagConstraints(0,y+3,2,1,GridBagConstraints.CENTER));
        add(close,makeGridBagConstraints(1,y+4,1,1,GridBagConstraints.EAST));
        add(saveButton,makeGridBagConstraints(1,y+4,1,1,GridBagConstraints.WEST));

        saveButton.addActionListener(owner.getActionListener());
        close.addActionListener(owner.getActionListener());

        reset();
        pack();
    }

    public JButton getCloseButton() {
        return close;
    }

    public JButton getSaveButton() { return saveButton; }

    public String getJsonString() {
        return jsonField.getText();
    }

    public final void reset() {
        setStatus("Please wait..");
    }

    public void ready() {
        jsonField.setEnabled(true);
        setStatus("Ready.");
    }

    public void error(String errorMessage) {
        setStatus("Error: "+errorMessage);
    }

    public void cookiesReceived() {
        setStatus("Cookies parsed... completing..");
    }

    public void failedParseCookies() {
        setStatus("failed parsing JSON cookies");
    }

    private void setStatus(String text) {
        status.setText("<html><body style='width:150px;text-align:center'>"+text);
        pack();
    }

    private GridBagConstraints makeGridBagConstraints(int x, int y,int w, int h, int anchor) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = w;
        constraints.gridheight = h;
        constraints.insets = new Insets(5,5,5,5);
        constraints.anchor = anchor;
        return constraints;
    }

    @Override
    public void itemStateChanged(ItemEvent e) { }

    @Override
    public void actionPerformed(ActionEvent e) {

    }

}
