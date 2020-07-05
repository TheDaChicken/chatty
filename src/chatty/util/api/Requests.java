package chatty.util.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Requests {
    private final ExecutorService executor;
    private final YouTubeApi api;
    private final TwitchApiResultListener listener;

    public Requests(YouTubeApi api, TwitchApiResultListener listener) {
        executor = Executors.newCachedThreadPool();
        this.api = api;
        this.listener = listener;
    }

    //=======
    // System
    //=======

    public void verifyToken(String token) {

    }

}
