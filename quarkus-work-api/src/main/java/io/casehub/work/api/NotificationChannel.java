package io.casehub.work.api;

/**
 * SPI for outbound notification delivery.
 *
 * <p>
 * Implementations are CDI {@code @ApplicationScoped} beans discovered at startup.
 * The {@link #channelType()} string is matched against
 * {@code WorkItemNotificationRule.channelType} in the database to route each rule
 * to the correct channel implementation.
 *
 * <p>
 * Built-in implementations: {@code "http-webhook"}, {@code "slack"}, {@code "teams"}.
 * Custom implementations: provide a CDI bean with any {@code channelType()} string,
 * persist rules with that type, and it will be called automatically.
 *
 * <p>
 * {@code send()} is called from a worker thread — it must not assume any active
 * JTA transaction. Implementations should handle delivery failures gracefully
 * (log and continue) to avoid disrupting the WorkItem lifecycle.
 */
public interface NotificationChannel {

    /**
     * Identifies this channel — matched against {@code WorkItemNotificationRule.channelType}.
     *
     * @return the channel type string, e.g. {@code "http-webhook"}, {@code "slack"}
     */
    String channelType();

    /**
     * Send a notification for the given payload.
     *
     * <p>
     * Called on a worker thread after the WorkItem lifecycle event has been committed.
     * Implementations must not throw unchecked exceptions — log failures and return.
     *
     * @param payload the notification payload containing the lifecycle event and matched rule
     */
    void send(NotificationPayload payload);
}
