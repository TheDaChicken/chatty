package chatty.exceptions;

public class YouTube404 extends Exception {

    public YouTube404(String channel_id) {
        super(channel_id + " doesn't seem to exist as a channel id.");
    }

}
