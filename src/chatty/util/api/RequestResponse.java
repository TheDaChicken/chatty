package chatty.util.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RequestResponse {

    public final int status_code;
    private final InputStream inputStream;

    public RequestResponse(int status_code, InputStream response) {
        this.status_code = status_code;
        this.inputStream = response;
    }

    public String getResponse() {
        Charset charset = StandardCharsets.UTF_8;
        // Read response
        StringBuilder response = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.inputStream, charset))) {
            String line;
            response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return response.toString();
    }

}
