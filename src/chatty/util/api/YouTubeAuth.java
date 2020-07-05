package chatty.util.api;

import chatty.Chatty;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class YouTubeAuth {
    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    public static final JsonFactory JSON_FACTORY = new JacksonFactory();
    public static GoogleAuthorizationCodeFlow flow;

    public static YouTube createService(GoogleCredential credential) {
        return new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("YouTube CHATTY")
                .build();
    }

    public static GoogleCredential receiveCredential(String code) {
        try {
            GoogleTokenResponse tokenResponse =
                    new GoogleAuthorizationCodeTokenRequest(
                            HTTP_TRANSPORT, JSON_FACTORY, Chatty.CLIENT_ID, Chatty.CLIENT_SECRET, code, Chatty.REDIRECT_URI)
                            .execute();

            return new GoogleCredential.Builder()
                    .setTransport(HTTP_TRANSPORT)
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(Chatty.CLIENT_ID, Chatty.CLIENT_SECRET)
                    .build()
                    .setFromTokenResponse(tokenResponse);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getAuthURL(List<String> scopes) {
        return new GoogleAuthorizationCodeRequestUrl(
                GoogleOAuthConstants.AUTHORIZATION_SERVER_URL, Chatty.CLIENT_ID,
                Chatty.REDIRECT_URI, scopes)
                .setAccessType("offline").build();
    }

    public static GoogleCredential getCredentials(String access_token, String refresh_token) {
        if(access_token.isEmpty() && refresh_token.isEmpty()) {
            return null;
        }
        GoogleCredential credential =
                new GoogleCredential.Builder()
                        .setTransport(HTTP_TRANSPORT)
                        .setJsonFactory(JSON_FACTORY)
                        .setClientSecrets(Chatty.CLIENT_ID, Chatty.CLIENT_SECRET).build();
        credential.setAccessToken(access_token);
        credential.setRefreshToken(refresh_token);
        return credential;
    }

    public static GoogleCredential getJsonCredentials(String json) {
        if(json.isEmpty()) {
            return null;
        }
        JSONParser parser = new JSONParser();
        JSONObject root;
        try {
            root = (JSONObject)parser.parse(json);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        String access_token = (String)root.get("access_token");
        String refresh_token = (String)root.get("refresh_token");
        return getCredentials(access_token, refresh_token);
    }

    public static String CredentialsToJson(GoogleCredential credential) {
        HashMap<String, String> map = new HashMap<>();
        map.put("access_token", credential.getAccessToken());
        map.put("refresh_token", credential.getRefreshToken());
        JSONObject object = new JSONObject();
        object.putAll(map);
        return object.toJSONString();
    }
}
