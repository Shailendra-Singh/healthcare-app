package me.shail.proxy;

import java.nio.charset.StandardCharsets;

/** A downstream service's answer: status, content type (possibly null) and body. */
public record DownstreamResponse(int status, String contentType, byte[] body) {

    public static DownstreamResponse json(int status, String json) {
        return new DownstreamResponse(status, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
