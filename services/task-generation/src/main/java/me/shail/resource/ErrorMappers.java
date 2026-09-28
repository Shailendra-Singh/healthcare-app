package me.shail.resource;

import jakarta.persistence.OptimisticLockException;
import me.shail.service.GenerationInProgressException;
import me.shail.service.InvalidTaskChangeException;
import me.shail.service.NoEvaluationException;
import org.hibernate.StaleStateException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Turns the service's refusals into responses that say why: {@code {"message": "..."}}. */
public class ErrorMappers {

    public record Error(String message) {
    }

    /** 409 when the task's state forbids the change (it is closed), 400 when the change itself is invalid. */
    @ServerExceptionMapper
    public RestResponse<Error> invalidTaskChange(InvalidTaskChangeException e) {
        return RestResponse.status(e.conflict() ? RestResponse.Status.CONFLICT : RestResponse.Status.BAD_REQUEST,
                new Error(e.getMessage()));
    }

    /** A person and a reconciliation changed the same task at once; the later write is rejected. */
    @ServerExceptionMapper({OptimisticLockException.class, StaleStateException.class})
    public RestResponse<Error> concurrentChange(RuntimeException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT,
                new Error("The task changed at the same time; reload it and try again"));
    }

    @ServerExceptionMapper
    public RestResponse<Error> generationInProgress(GenerationInProgressException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, new Error(e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<Error> noEvaluation(NoEvaluationException e) {
        return RestResponse.status(RestResponse.Status.NOT_FOUND, new Error(e.getMessage()));
    }
}
