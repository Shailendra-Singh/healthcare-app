package me.shail.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/** The rules-engine's API ({@code quarkus.rest-client.rules-engine.url}). */
@RegisterRestClient(configKey = "rules-engine")
@Path("/api/v1/evaluations")
public interface RulesEngineClient {

    /** @throws jakarta.ws.rs.WebApplicationException with status 404 before the first successful run */
    @GET
    @Path("/latest")
    EvaluationRun latestSucceeded(@RestQuery String status);

    /** Every care need of a successful run, in a stable order; {@code page} starts at 1. */
    @GET
    @Path("/{runId}/care-needs")
    List<CareNeed> careNeeds(@RestPath Long runId, @RestQuery int page, @RestQuery int size);
}
