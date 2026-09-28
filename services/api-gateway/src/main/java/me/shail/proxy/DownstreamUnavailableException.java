package me.shail.proxy;

/** A downstream service could not be reached (down, not started, or timed out). */
public class DownstreamUnavailableException extends RuntimeException {

    private final String service;

    public DownstreamUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable", cause);
        this.service = service;
    }

    public String service() {
        return service;
    }
}
