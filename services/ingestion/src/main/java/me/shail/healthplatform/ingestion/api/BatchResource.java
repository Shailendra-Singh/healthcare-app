package me.shail.healthplatform.ingestion.api;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import me.shail.healthplatform.ingestion.batch.BatchReprocessor;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ingestion status for admins (through the gateway): list batches, show one,
 * and re-publish a batch's event. An unknown or malformed batch ID is 404.
 */
@Path("/batches")
@Produces(MediaType.APPLICATION_JSON)
public class BatchResource {

    private static final int MAX_LIMIT = 200;

    private final BatchReprocessor reprocessor;

    BatchResource(BatchReprocessor reprocessor) {
        this.reprocessor = reprocessor;
    }

    /** Newest first. */
    @GET
    @WithSession
    public Uni<List<BatchSummary>> list(@RestQuery @DefaultValue("50") int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        return Batch.<Batch>findAll(Sort.descending("receivedAt"))
                .page(Page.ofSize(size))
                .list()
                .map(batches -> batches.stream().map(BatchSummary::of).toList());
    }

    @GET
    @Path("{batchId}")
    @WithSession
    public Uni<Response> get(@RestPath UUID batchId) {
        return findDetail(batchId).map(detail -> detail == null
                ? notFound(batchId)
                : Response.ok(detail).build());
    }

    /**
     * 200 with the updated batch; 404 unknown batch; 409 batch still being created
     * or its stored copies are gone; 503 the event could not be published.
     */
    @POST
    @Path("{batchId}/reprocess")
    public Uni<Response> reprocess(@RestPath UUID batchId) {
        return reprocessor.reprocess(batchId)
                .chain(outcome -> switch (outcome) {
                    // Fresh session: the reprocess session still holds the files as they were before the update.
                    case REPUBLISHED -> Panache.withSession(() -> findDetail(batchId))
                            .map(detail -> Response.ok(detail).build());
                    case NOT_FOUND -> Uni.createFrom().item(notFound(batchId));
                    case IN_PROGRESS -> Uni.createFrom().item(error(Status.CONFLICT,
                            "Batch " + batchId + " is still being created"));
                    case FILES_MISSING -> Uni.createFrom().item(error(Status.CONFLICT,
                            "Stored files of batch " + batchId + " are missing"));
                })
                .onFailure().recoverWithItem(e -> {
                    Log.errorf(e, "Reprocessing batch %s failed", batchId);
                    return error(Status.SERVICE_UNAVAILABLE, "Could not publish batch " + batchId);
                });
    }

    /** Null when there is no such batch. Needs an open session. */
    private static Uni<BatchDetail> findDetail(UUID batchId) {
        return Batch.<Batch>find("batchId", batchId).firstResult().chain(batch -> batch == null
                ? Uni.createFrom().nullItem()
                : BatchFile.<BatchFile>list("batch.id = ?1 order by fileType", batch.id)
                        .map(files -> BatchDetail.of(batch, files)));
    }

    private static Response notFound(UUID batchId) {
        return error(Status.NOT_FOUND, "Batch " + batchId + " not found");
    }

    private static Response error(Status status, String message) {
        return Response.status(status).entity(Map.of("error", message)).build();
    }
}
