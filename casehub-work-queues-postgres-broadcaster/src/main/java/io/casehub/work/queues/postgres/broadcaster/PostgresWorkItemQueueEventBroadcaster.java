package io.casehub.work.queues.postgres.broadcaster;

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

import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.queues.service.WorkItemQueueEventBroadcaster;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.subscription.BackPressureFailure;
import io.vertx.mutiny.pgclient.PgConnection;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;

/**
 * Distributed {@link WorkItemQueueEventBroadcaster} backed by PostgreSQL LISTEN/NOTIFY.
 *
 * <p>
 * When a queue membership changes on any node, the event is serialised to JSON and
 * published to the {@value #CHANNEL} channel via {@code SELECT pg_notify(...)}, fired
 * <em>after the transaction commits</em> ({@link TransactionPhase#AFTER_SUCCESS}). All
 * other nodes subscribed to the channel receive the payload, deserialise it, and emit
 * it to their local SSE clients via the in-process {@link BroadcastProcessor}.
 *
 * <h2>Zero new infrastructure</h2>
 * <p>
 * Uses the datasource already required by the core extension and {@code casehub-work-queues}.
 * Adding this module to the classpath alongside {@code casehub-work-queues} is the only
 * change needed for distributed queue SSE.
 *
 * <h2>No CDI re-fire on receive</h2>
 * <p>
 * Events received from the PostgreSQL channel are pushed directly to the local
 * {@link BroadcastProcessor} — they are NOT re-fired as CDI events. This avoids
 * double-processing on nodes where the originating CDI event already ran all observers.
 *
 * <h2>Wire format</h2>
 * <p>
 * {@link WorkItemQueueEvent} is a plain Java record — Jackson serialises and deserialises
 * it directly without a separate DTO.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class PostgresWorkItemQueueEventBroadcaster implements WorkItemQueueEventBroadcaster {

    private static final Logger LOG = Logger.getLogger(PostgresWorkItemQueueEventBroadcaster.class);

    /** PostgreSQL channel name. Must be a valid unquoted identifier (underscores, lowercase). */
    static final String CHANNEL = "casehub_work_queue_events";

    private final BroadcastProcessor<WorkItemQueueEvent> processor = BroadcastProcessor.create();

    @Inject
    PgPool pool;

    @Inject
    ObjectMapper objectMapper;

    /** Dedicated connection held open for LISTEN; closed on @PreDestroy. */
    private PgConnection subscriberConnection;

    @PostConstruct
    void startListening() {
        // PgPool always returns PgConnection delegates — unwrap and re-wrap to access
        // notificationHandler(), which is not available on the SqlConnection Mutiny wrapper.
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
     * CDI observer: fires AFTER the transaction commits to avoid publishing events
     * whose originating transaction was subsequently rolled back.
     */
    public void onEvent(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) final WorkItemQueueEvent event) {
        try {
            final String json = objectMapper.writeValueAsString(event);
            pool.preparedQuery("SELECT pg_notify($1, $2)")
                    .execute(Tuple.of(CHANNEL, json))
                    .subscribe().with(
                            ok -> {},
                            err -> LOG.warnf("pg_notify failed on '%s': %s", CHANNEL, err.getMessage()));
        } catch (final JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialise WorkItemQueueEvent for channel '%s'", CHANNEL);
        }
    }

    /**
     * Called when another node (or this node, loopback) publishes to the channel.
     * Emits directly to the local processor — does NOT re-fire as a CDI event.
     */
    void handleNotification(final String payload) {
        try {
            final WorkItemQueueEvent event = objectMapper.readValue(payload, WorkItemQueueEvent.class);
            emit(event);
        } catch (final Exception e) {
            LOG.warnf("Failed to deserialise queue notification payload: %s", e.getMessage());
        }
    }

    /** Package-private — allows unit tests to exercise filter logic without a database. */
    void emit(final WorkItemQueueEvent event) {
        try {
            processor.onNext(event);
        } catch (final BackPressureFailure ignored) {
            // No SSE subscribers connected — hot stream drops events with no listener.
        }
    }

    @Override
    public Multi<WorkItemQueueEvent> stream(final UUID queueViewId) {
        Multi<WorkItemQueueEvent> source = processor.toHotStream();
        if (queueViewId != null) {
            source = source.filter(e -> queueViewId.equals(e.queueViewId()));
        }
        return source;
    }
}
