package me.shail.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

/** The clinical-data service's read-only API ({@code quarkus.rest-client.clinical-data.url}). */
@RegisterRestClient(configKey = "clinical-data")
@Path("/api/v1")
public interface ClinicalDataClient {

    /** One page of patients with their facts; {@code page} starts at 1. Visits are split at {@code asOf}. */
    @GET
    @Path("/evaluation-inputs")
    List<EvaluationInput> evaluationInputs(@RestQuery long page, @RestQuery int size, @RestQuery LocalDate asOf);

    /**
     * The latest ETL run with {@code status} (SUCCEEDED).
     *
     * @throws jakarta.ws.rs.WebApplicationException with status 404 before the ETL's first successful run
     */
    @GET
    @Path("/etl-runs/latest")
    EtlRun latestEtlRun(@RestQuery String status);
}
