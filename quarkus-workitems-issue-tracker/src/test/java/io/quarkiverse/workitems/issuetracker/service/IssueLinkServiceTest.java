package io.quarkiverse.workitems.issuetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.issuetracker.StubIssueTrackerProvider;
import io.quarkiverse.workitems.issuetracker.model.WorkItemIssueLink;
import io.quarkiverse.workitems.issuetracker.spi.IssueTrackerException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

// @TestTransaction rolls back after each test — no deleteAll() needed, and the service's
// own @Transactional methods join this test transaction correctly (no REST calls here).
@QuarkusTest
@TestTransaction
class IssueLinkServiceTest {

    @Inject
    IssueLinkService service;

    @Inject
    StubIssueTrackerProvider stub;

    @BeforeEach
    void reset() {
        stub.reset();
    }

    // ── linkExistingIssue ─────────────────────────────────────────────────────

    @Test
    void linkExisting_fetchesAndPersistsSnapshot() {
        stub.seed("owner/repo#42", "Fix the bug", "open");
        final UUID workItemId = UUID.randomUUID();

        final WorkItemIssueLink link = service.linkExistingIssue(
                workItemId, "github", "owner/repo#42", "alice");

        assertThat(link.id).isNotNull();
        assertThat(link.workItemId).isEqualTo(workItemId);
        assertThat(link.trackerType).isEqualTo("github");
        assertThat(link.externalRef).isEqualTo("owner/repo#42");
        assertThat(link.title).isEqualTo("Fix the bug");
        assertThat(link.status).isEqualTo("open");
        assertThat(link.linkedBy).isEqualTo("alice");
        assertThat(link.linkedAt).isNotNull();
    }

    @Test
    void linkExisting_isIdempotent_returnsSameLink() {
        stub.seed("owner/repo#10", "Duplicate link test", "open");
        final UUID workItemId = UUID.randomUUID();

        final WorkItemIssueLink first = service.linkExistingIssue(workItemId, "github", "owner/repo#10", "alice");
        final WorkItemIssueLink second = service.linkExistingIssue(workItemId, "github", "owner/repo#10", "bob");

        assertThat(second.id).isEqualTo(first.id);
        assertThat(service.listLinks(workItemId)).hasSize(1);
    }

    @Test
    void linkExisting_throws_whenIssueNotFound() {
        final UUID workItemId = UUID.randomUUID();

        assertThatThrownBy(() -> service.linkExistingIssue(workItemId, "github", "owner/repo#99999", "alice"))
                .isInstanceOf(IssueTrackerException.class)
                .matches(e -> ((IssueTrackerException) e).isNotFound());
    }

    @Test
    void linkExisting_throws_whenNoProviderRegistered() {
        final UUID workItemId = UUID.randomUUID();

        assertThatThrownBy(() -> service.linkExistingIssue(workItemId, "jira", "PROJ-1", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jira");
    }

    // ── createAndLink ─────────────────────────────────────────────────────────

    @Test
    void createAndLink_createsIssueAndReturnsLink() {
        final UUID workItemId = UUID.randomUUID();

        final WorkItemIssueLink link = service.createAndLink(
                workItemId, "github", "Security triage needed", "CVE details...", "system");

        assertThat(link.externalRef).startsWith("stub/repo#");
        assertThat(link.title).isEqualTo("Security triage needed");
        assertThat(link.status).isEqualTo("open");
        assertThat(stub.created()).hasSize(1).contains(link.externalRef);
    }

    // ── listLinks ─────────────────────────────────────────────────────────────

    @Test
    void listLinks_returnsAllForWorkItem() {
        stub.seed("owner/repo#1", "Issue 1", "open");
        stub.seed("owner/repo#2", "Issue 2", "closed");
        final UUID workItemId = UUID.randomUUID();

        service.linkExistingIssue(workItemId, "github", "owner/repo#1", "alice");
        service.linkExistingIssue(workItemId, "github", "owner/repo#2", "alice");

        assertThat(service.listLinks(workItemId)).hasSize(2);
    }

    @Test
    void listLinks_isolatesAcrossWorkItems() {
        stub.seed("owner/repo#5", "Issue 5", "open");
        final UUID item1 = UUID.randomUUID();
        final UUID item2 = UUID.randomUUID();

        service.linkExistingIssue(item1, "github", "owner/repo#5", "alice");

        assertThat(service.listLinks(item1)).hasSize(1);
        assertThat(service.listLinks(item2)).isEmpty();
    }

    // ── removeLink ────────────────────────────────────────────────────────────

    @Test
    void removeLink_deletesLink() {
        stub.seed("owner/repo#3", "Removable", "open");
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = service.linkExistingIssue(workItemId, "github", "owner/repo#3", "alice");

        final boolean removed = service.removeLink(link.id, workItemId);

        assertThat(removed).isTrue();
        assertThat(service.listLinks(workItemId)).isEmpty();
    }

    @Test
    void removeLink_returnsFalse_whenNotFound() {
        assertThat(service.removeLink(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void removeLink_returnsFalse_whenWrongWorkItem() {
        stub.seed("owner/repo#4", "Wrong owner", "open");
        final UUID item1 = UUID.randomUUID();
        final UUID item2 = UUID.randomUUID();
        final WorkItemIssueLink link = service.linkExistingIssue(item1, "github", "owner/repo#4", "alice");

        assertThat(service.removeLink(link.id, item2)).isFalse();
        assertThat(service.listLinks(item1)).hasSize(1); // still there
    }

    // ── syncLinks ─────────────────────────────────────────────────────────────

    @Test
    void syncLinks_updatesStatusFromRemote() {
        stub.seed("owner/repo#6", "Synced issue", "open");
        final UUID workItemId = UUID.randomUUID();
        service.linkExistingIssue(workItemId, "github", "owner/repo#6", "alice");

        // Change state in stub (simulating remote close)
        stub.seed("owner/repo#6", "Synced issue", "closed");

        final int synced = service.syncLinks(workItemId);

        assertThat(synced).isEqualTo(1);
        final WorkItemIssueLink updated = service.listLinks(workItemId).get(0);
        assertThat(updated.status).isEqualTo("closed");
    }

    @Test
    void syncLinks_continuesOnPartialFailure() {
        stub.seed("owner/repo#7", "Good issue", "open");
        final UUID workItemId = UUID.randomUUID();
        service.linkExistingIssue(workItemId, "github", "owner/repo#7", "alice");

        // Inject a broken link directly
        final WorkItemIssueLink broken = new WorkItemIssueLink();
        broken.workItemId = workItemId;
        broken.trackerType = "github";
        broken.externalRef = "owner/repo#BROKEN";
        broken.status = "unknown";
        broken.linkedBy = "test";
        broken.persistAndFlush();

        // sync — one succeeds, one fails (not found in stub)
        final int synced = service.syncLinks(workItemId);
        assertThat(synced).isEqualTo(1); // only the good one counted
    }
}
