package me.shail.resource;

import me.shail.proxy.DownstreamUnavailableException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Errors as {@code {"message": "..."}}. */
public class ErrorMappers {

    private static final Logger LOG = Logger.getLogger(ErrorMappers.class);

    public record Error(String message) {
    }

    @ServerExceptionMapper
    public RestResponse<Error> downstreamUnavailable(DownstreamUnavailableException e) {
        LOG.warnf("%s: %s", e.getMessage(), e.getCause() == null ? "" : e.getCause().toString());
        return RestResponse.status(RestResponse.Status.BAD_GATEWAY, new Error(e.getMessage()));
    }
}
