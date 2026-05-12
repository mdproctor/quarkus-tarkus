package io.casehub.work.postgres.broadcaster;

import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.work.runtime.event.WorkItemEventBroadcaster;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.subscription.BackPressureFailure;
import io.vertx.mutiny.pgclient.PgConnection;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;

/**
 * Distributed {@link WorkItemEventBroadcaster} backed by PostgreSQL LISTEN/NOTIFY.
 *
 * <p>
 * When a WorkItem transitions on any node, the lifecycle event is serialised to JSON and
 * published to the {@value #CHANNEL} channel via {@code SELECT pg_notify(...)}, fired
 * <em>after the transaction commits</em> ({@link TransactionPhase#AFTER_SUCCESS}). All
 * other nodes subscribed to the channel receive the payload, deserialise it, and emit
 * it to their local SSE clients via the in-process {@link BroadcastProcessor}.
 *
 * <h2>Zero new infrastructure</h2>
 * <p>
 * PostgreSQL LISTEN/NOTIFY uses the datasource already required by the core extension.
 * Adding this module to the classpath is the only change needed for distributed SSE.
 *
 * <h2>Thread safety</h2>
 * <p>
 * The {@link BroadcastProcessor} is thread-safe. Notifications arrive on the Vert.x
 * event loop of the subscriber connection; CDI event delivery may run on a different
 * thread. No additional synchronisation is required.
 *
 * <h2>No CDI re-fire on receive</h2>
 * <p>
 * Events received from the PostgreSQL channel are pushed directly to the local
 * {@link BroadcastProcessor} — they are NOT re-fired as CDI events. This avoids
 * double-processing on nodes where the originating CDI event already ran all observers.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class PostgresWorkItemEventBroadcaster implements WorkItemEventBroadcaster {

    private static final Logger LOG = Logger.getLogger(PostgresWorkItemEventBroadcaster.class);

    /** PostgreSQL channel name. Must be a valid unquoted identifier (underscores, lowercase). */
    static final String CHANNEL = "casehub_work_events";

    private final BroadcastProcessor<WorkItemLifecycleEvent> processor = BroadcastProcessor.create();

    @Inject
    PgPool pool;

    @Inject
    ObjectMapper objectMapper;

    /** Dedicated connection held open for LISTEN; closed on @PreDestroy. */
    private PgConnection subscriberConnection;

    @PostConstruct
    void startListening() {
        // Acquire a connection held open for the lifetime of the application.
        // pool.getConnection() returns Uni<SqlConnection> (Mutiny wrapper). The underlying
        // delegate from a PgPool is always a io.vertx.pgclient.PgConnection — we re-wrap it
        // as the Mutiny PgConnection to access notificationHandler().
        pool.getConnection().subscribe().with(
                conn -> {
                    final io.vertx.pgclient.PgConnection pgDelegate =
                            (io.vertx.pgclient.PgConnection) conn.getDelegate();
                    final PgConnection pgConn = PgConnection.newInstance(pgDelegate);
                    subscriberConnection = pgConn;
                    pgConn.notificationHandler(n -> handleNotification(n.getPayload()));
                    pgConn.query("LISTEN " + CHANNEL).execute()
                            .subscribe().with(
                                    ok -> LOG.infof("Subscribed to PostgreSQL channel '%s'", CHANNEL),
                                    err -> LOG.errorf(err, "Failed to LISTEN on '%s'", CHANNEL));
                },
                err -> LOG.errorf(err, "Failed to acquire subscriber connection for '%s'", CHANNEL));
    }

    @PreDestroy
    void stopListening() {
        if (subscriberConnection != null) {
            subscriberConnection.close().subscribe().with(ok -> {}, err -> {});
        }
    }

    /**
     * CDI observer: fires AFTER the transaction commits to avoid notifying subscribers
     * of events whose transaction was subsequently rolled back.
     */
    public void onEvent(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) final WorkItemLifecycleEvent event) {
        try {
            final String json = objectMapper.writeValueAsString(WorkItemEventPayload.from(event));
            pool.preparedQuery("SELECT pg_notify($1, $2)")
                    .execute(Tuple.of(CHANNEL, json))
                    .subscribe().with(
                            ok -> {},
                            err -> LOG.warnf("pg_notify failed on channel '%s': %s", CHANNEL, err.getMessage()));
        } catch (final JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialise WorkItemLifecycleEvent for channel '%s'", CHANNEL);
        }
    }

    /**
     * Called by the subscriber connection's notification handler when another node (or this
     * node, in a loopback scenario) publishes to the channel.
     *
     * <p>
     * Emits the reconstructed event to the local {@link BroadcastProcessor} for all active
     * SSE subscribers. Does NOT re-fire as a CDI event.
     */
    void handleNotification(final String payload) {
        try {
            final WorkItemEventPayload dto = objectMapper.readValue(payload, WorkItemEventPayload.class);
            emit(dto.toEvent());
        } catch (final Exception e) {
            LOG.warnf("Failed to deserialise notification payload: %s", e.getMessage());
        }
    }

    /**
     * Emits an event directly to the local hot stream.
     * Package-private to allow unit tests to exercise filter logic without a database.
     */
    void emit(final WorkItemLifecycleEvent event) {
        try {
            processor.onNext(event);
        } catch (final BackPressureFailure ignored) {
            // No SSE subscribers connected — hot stream drops events with no listener.
        }
    }

    @Override
    public Multi<WorkItemLifecycleEvent> stream(final UUID workItemId, final String type) {
        Multi<WorkItemLifecycleEvent> source = processor.toHotStream();

        if (workItemId != null) {
            source = source.filter(e -> workItemId.equals(e.workItemId()));
        }

        if (type != null && !type.isBlank()) {
            final String suffix = type.toLowerCase();
            source = source.filter(e -> e.type() != null && e.type().toLowerCase().endsWith(suffix));
        }

        return source;
    }
}
