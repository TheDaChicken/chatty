package chatty.util;

import java.util.Objects;

public class TwitchEmotesApi {

    public static class EmotesetInfo {

        public final String emoteset_id;
        public final String product;
        public final String stream_name;
        public final String stream_id;

        public EmotesetInfo(String emoteset_id, String stream_name, String stream_id, String product) {
            this.emoteset_id = emoteset_id;
            this.product = product;
            this.stream_name = stream_name;
            this.stream_id = stream_id;
        }

        @Override
        public String toString() {
            return String.format("%s(%s,%s,%s)", emoteset_id, stream_name, stream_id, product);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final EmotesetInfo other = (EmotesetInfo) obj;
            if (!Objects.equals(this.emoteset_id, other.emoteset_id)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.emoteset_id);
            return hash;
        }

    }

}
