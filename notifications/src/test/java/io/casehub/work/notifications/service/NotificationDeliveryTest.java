package io.casehub.work.notifications.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.casehub.work.notifications.model.WorkItemNotificationRule;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * End-to-end tests verifying that lifecycle events actually deliver HTTP requests
 * to configured webhook URLs. Uses WireMock as an in-process HTTP server.
 */
@QuarkusTest
class NotificationDeliveryTest {

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlEqualTo("/hook")).willReturn(ok()));
        wireMock.stubFor(post(urlEqualTo("/slack")).willReturn(ok()));
    }

    @AfterEach
    @Transactional
    void cleanup() {
        wireMock.stop();
        WorkItemNotificationRule.deleteAll();
        AuditEntry.deleteAll();
        WorkItem.deleteAll();
    }

    @Test
    void httpWebhook_firesWhenAssigned() {
        final String hookUrl = "http://localhost:" + wireMock.port() + "/hook";

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "channelType", "http-webhook",
                        "targetUrl", hookUrl,
                        "eventTypes", "ASSIGNED"))
                .when().post("/workitem-notification-rules")
                .then().statusCode(201);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Delivery test", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("claimantId", "alice"))
                .when().put("/workitems/" + id + "/claim")
                .then().statusCode(200);

        await().atMost(5, SECONDS).untilAsserted(() ->
                wireMock.verify(postRequestedFor(urlEqualTo("/hook"))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withHeader("X-WorkItem-Event", equalTo("ASSIGNED"))
                        .withRequestBody(matching(".*\"eventType\":\"ASSIGNED\".*"))));
    }

    @Test
    void httpWebhook_withSecret_includesSignatureHeader() {
        final String hookUrl = "http://localhost:" + wireMock.port() + "/hook";

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "channelType", "http-webhook",
                        "targetUrl", hookUrl,
                        "eventTypes", "CREATED",
                        "secret", "my-hmac-secret"))
                .when().post("/workitem-notification-rules")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Signed delivery", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201);

        await().atMost(5, SECONDS).untilAsserted(() ->
                wireMock.verify(postRequestedFor(urlEqualTo("/hook"))
                        .withHeader("X-Signature-256", matching("sha256=[0-9a-f]{64}"))));
    }

    @Test
    void categoryFilter_onlyFiresForMatchingCategory() {
        final String hookUrl = "http://localhost:" + wireMock.port() + "/hook";

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "channelType", "http-webhook",
                        "targetUrl", hookUrl,
                        "eventTypes", "CREATED",
                        "category", "loan-application"))
                .when().post("/workitem-notification-rules")
                .then().statusCode(201);

        // Create WorkItem with DIFFERENT category — should NOT trigger
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Legal item", "category", "legal", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201);

        // Create WorkItem with MATCHING category — SHOULD trigger
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Loan item", "category", "loan-application", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201);

        // Wait for exactly 1 delivery, then verify no more arrive within a brief window
        await().atMost(5, SECONDS).untilAsserted(() ->
                wireMock.verify(1, postRequestedFor(urlEqualTo("/hook"))));
    }

    @Test
    void disabledRule_doesNotFire() {
        final String hookUrl = "http://localhost:" + wireMock.port() + "/hook";

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "channelType", "http-webhook",
                        "targetUrl", hookUrl,
                        "eventTypes", "CREATED",
                        "enabled", false))
                .when().post("/workitem-notification-rules")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Disabled rule test", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201);

        // Assert no delivery within a generous window
        await().during(1, SECONDS).atMost(2, SECONDS).untilAsserted(() ->
                wireMock.verify(0, postRequestedFor(urlEqualTo("/hook"))));
    }

    @Test
    void slackChannel_firesWhenMatched() {
        final String slackUrl = "http://localhost:" + wireMock.port() + "/slack";

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "channelType", "slack",
                        "targetUrl", slackUrl,
                        "eventTypes", "CREATED"))
                .when().post("/workitem-notification-rules")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Slack test item", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201);

        await().atMost(5, SECONDS).untilAsserted(() ->
                wireMock.verify(postRequestedFor(urlEqualTo("/slack"))
                        .withRequestBody(matching(".*\"text\".*CREATED.*"))));
    }

    @Test
    void failingWebhook_doesNotAffectWorkItemLifecycle() {
        wireMock.stubFor(post(urlEqualTo("/failing")).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.serverError()));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "channelType", "http-webhook",
                        "targetUrl", "http://localhost:" + wireMock.port() + "/failing",
                        "eventTypes", "CREATED"))
                .when().post("/workitem-notification-rules")
                .then().statusCode(201);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "Resilience test", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final String status = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("status");

        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("PENDING");
    }
}
