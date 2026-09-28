package me.shail.support;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Runs repository calls inside a stateless reactive session, as the services do in the app.
 * Repositories cannot be called without one.
 */
@ApplicationScoped
public class ReactiveSessions {

    @WithSession(stateless = true)
    public <T> Uni<T> read(Supplier<Uni<T>> work) {
        return Uni.createFrom().deferred(work::get);
    }

    @WithTransaction(stateless = true)
    public <T> Uni<T> write(Supplier<Uni<T>> work) {
        return Uni.createFrom().deferred(work::get);
    }
}
