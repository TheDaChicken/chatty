package chatty.exceptions;

public class PrivateStream extends Exception {

    public PrivateStream(String channel_id) {
        super(channel_id + " has streams set to private!");
    }

}
