package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and end-to-end tests for the WorkItem SSE event stream.
 *
 * REST Assured tries to read the full response body — SSE never closes, so it
 * would hang forever. These tests use java.net.http.HttpClient instead: headers
 * arrive immediately, body stream is lazy and only read when we choose.
 */
@QuarkusTest
class WorkItemSSETest {

    @TestHTTPResource("/")
    URI baseUri;

    // Integration: reachability

    @Test
    void sseEndpoint_returns200_withSseContentType() throws Exception {
        final HttpResponse<InputStream> response = connectSse("/workitems/events");
        try (InputStream ignored = response.body()) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/event-stream");
        }
    }

    @Test
    void sseEndpoint_withWorkItemIdFilter_isReachable() throws Exception {
        final HttpResponse<InputStream> response = connectSse("/workitems/events?workItemId=" + UUID.randomUUID());
        try (InputStream ignored = response.body()) {
            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    @Test
    void sseEndpoint_withTypeFilter_isReachable() throws Exception {
        final HttpResponse<InputStream> response = connectSse("/workitems/events?type=created");
        try (InputStream ignored = response.body()) {
            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    @Test
    void perWorkItemEndpoint_returns200_withSseContentType() throws Exception {
        final String itemId = createWorkItem();
        final HttpResponse<InputStream> response = connectSse("/workitems/" + itemId + "/events");
        try (InputStream ignored = response.body()) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/event-stream");
        }
    }

    // Happy path: create WorkItem → CREATED event in stream

    @Test
    void happyPath_createWorkItem_createdEventAppearsInStream() throws Exception {
        final List<String> dataLines = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread sseThread = Thread.ofVirtual().start(() -> {
            try {
                connectSseLinesAsync("workitems/events?type=created", dataLines, latch);
            } catch (Exception ignored) {
            }
        });
        Thread.sleep(400);
        final String itemId = createWorkItem();
        assertThat(latch.await(4, TimeUnit.SECONDS)).as("Expected CREATED event for " + itemId).isTrue();
        assertThat(dataLines.get(0)).contains("created");
        sseThread.interrupt();
    }

    // E2E: workItemId filter isolates events

    @Test
    void e2e_workItemIdFilter_receivesOnlyTargetWorkItemEvents() throws Exception {
        final String targetId = createWorkItem();
        final List<String> dataLines = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread sseThread = Thread.ofVirtual().start(() -> {
            try {
                connectSseLinesAsync("workitems/events?workItemId=" + targetId, dataLines, latch);
            } catch (Exception ignored) {
            }
        });
        Thread.sleep(400);
        createWorkItem(); // noise — should NOT appear
        given().contentType(ContentType.JSON).body("{\"assigneeId\":\"alice\"}")
                .put("/workitems/" + targetId + "/claim").then().statusCode(200);
        assertThat(latch.await(4, TimeUnit.SECONDS)).as("Expected event for target within 4s").isTrue();
        assertThat(dataLines).isNotEmpty();
        assertThat(dataLines).allMatch(line -> line.contains(targetId));
        sseThread.interrupt();
    }

    // E2E: per-WorkItem alias

    @Test
    void e2e_perWorkItemAlias_deliversEvents() throws Exception {
        final String itemId = createWorkItem();
        final List<String> dataLines = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread sseThread = Thread.ofVirtual().start(() -> {
            try {
                connectSseLinesAsync("workitems/" + itemId + "/events", dataLines, latch);
            } catch (Exception ignored) {
            }
        });
        Thread.sleep(400);
        given().contentType(ContentType.JSON).body("{\"assigneeId\":\"bob\"}")
                .put("/workitems/" + itemId + "/claim").then().statusCode(200);
        assertThat(latch.await(4, TimeUnit.SECONDS)).as("Expected event via per-WorkItem alias").isTrue();
        assertThat(dataLines.get(0)).contains(itemId);
        sseThread.interrupt();
    }

    // E2E: type filter

    @Test
    void e2e_typeFilter_onlyDeliversMatchingEventType() throws Exception {
        final String itemId = createWorkItem();
        final List<String> dataLines = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread sseThread = Thread.ofVirtual().start(() -> {
            try {
                connectSseLinesAsync("workitems/events?type=assigned", dataLines, latch);
            } catch (Exception ignored) {
            }
        });
        Thread.sleep(400);
        createWorkItem(); // fires CREATED — should not trigger latch (filter=assigned)
        given().contentType(ContentType.JSON).body("{\"assigneeId\":\"carol\"}")
                .put("/workitems/" + itemId + "/claim").then().statusCode(200);
        assertThat(latch.await(4, TimeUnit.SECONDS)).as("Expected ASSIGNED event").isTrue();
        assertThat(dataLines.get(0)).contains("assigned");
        sseThread.interrupt();
    }

    // Helpers

    private HttpResponse<InputStream> connectSse(final String path) throws Exception {
        final HttpClient client = HttpClient.newHttpClient();
        final String p = path.startsWith("/") ? path.substring(1) : path;
        return client.send(HttpRequest.newBuilder().uri(baseUri.resolve(p))
                .header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private void connectSseLinesAsync(final String path, final List<String> collector,
            final CountDownLatch latch) throws Exception {
        final HttpClient client = HttpClient.newHttpClient();
        final String p = path.startsWith("/") ? path.substring(1) : path;
        client.send(HttpRequest.newBuilder().uri(baseUri.resolve(p))
                .header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data:"))
                .peek(collector::add)
                .findFirst()
                .ifPresent(line -> latch.countDown());
    }

    private String createWorkItem() {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"SSE test item\",\"createdBy\":\"sse-test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }
}
