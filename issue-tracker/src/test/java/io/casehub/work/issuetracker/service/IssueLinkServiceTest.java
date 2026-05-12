package io.casehub.work.issuetracker.service;

import io.casehub.work.issuetracker.github.GitHubIssueTrackerConfig;
import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.issuetracker.spi.ExternalIssueRef;
import io.casehub.work.issuetracker.spi.IssueTrackerException;
import io.casehub.work.issuetracker.spi.IssueTrackerProvider;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IssueLinkServiceTest {

    private IssueLinkStore linkStore;
    private IssueTrackerProvider provider;
    private GitHubIssueTrackerConfig githubConfig;
    private IssueLinkService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        linkStore = mock(IssueLinkStore.class);
        provider = mock(IssueTrackerProvider.class);
        githubConfig = mock(GitHubIssueTrackerConfig.class);

        when(provider.trackerType()).thenReturn("github");
        when(githubConfig.autoCloseOnComplete()).thenReturn(false);
        when(linkStore.save(any())).thenAnswer(inv -> {
            final WorkItemIssueLink link = inv.getArgument(0);
            if (link.id == null) link.id = UUID.randomUUID();
            return link;
        });

        final Instance<IssueTrackerProvider> instances = mock(Instance.class);
        when(instances.iterator()).thenAnswer(inv -> List.<IssueTrackerProvider>of(provider).iterator());
        when(instances.stream()).thenAnswer(inv -> List.<IssueTrackerProvider>of(provider).stream());

        service = new IssueLinkService(instances, githubConfig, linkStore);
    }

    // ── linkExistingIssue ─────────────────────────────────────────────────────

    @Test
    void linkExisting_fetchesAndSavesSnapshot() {
        final UUID workItemId = UUID.randomUUID();
        when(linkStore.findByRef(workItemId, "github", "owner/repo#42")).thenReturn(Optional.empty());
        when(provider.fetchIssue("owner/repo#42"))
                .thenReturn(new ExternalIssueRef("github", "owner/repo#42", "Fix the bug", "https://gh/42", "open"));

        final WorkItemIssueLink result =
                service.linkExistingIssue(workItemId, "github", "owner/repo#42", "alice");

        assertThat(result.workItemId).isEqualTo(workItemId);
        assertThat(result.title).isEqualTo("Fix the bug");
        assertThat(result.status).isEqualTo("open");
        assertThat(result.linkedBy).isEqualTo("alice");
        verify(linkStore).save(any(WorkItemIssueLink.class));
    }

    @Test
    void linkExisting_isIdempotent_returnsExistingLink() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink existing = existingLink(workItemId, "github", "owner/repo#10");
        when(linkStore.findByRef(workItemId, "github", "owner/repo#10")).thenReturn(Optional.of(existing));

        final WorkItemIssueLink result =
                service.linkExistingIssue(workItemId, "github", "owner/repo#10", "bob");

        assertThat(result.id).isEqualTo(existing.id);
        verify(provider, never()).fetchIssue(any());
        verify(linkStore, never()).save(any());
    }

    @Test
    void linkExisting_throws_whenProviderThrowsNotFound() {
        final UUID workItemId = UUID.randomUUID();
        when(linkStore.findByRef(any(), any(), any())).thenReturn(Optional.empty());
        when(provider.fetchIssue("owner/repo#99999"))
                .thenThrow(IssueTrackerException.notFound("owner/repo#99999"));

        assertThatThrownBy(() ->
                service.linkExistingIssue(workItemId, "github", "owner/repo#99999", "alice"))
                .isInstanceOf(IssueTrackerException.class)
                .matches(e -> ((IssueTrackerException) e).isNotFound());
    }

    @Test
    void linkExisting_throws_whenNoProviderRegistered() {
        final UUID workItemId = UUID.randomUUID();
        when(linkStore.findByRef(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.linkExistingIssue(workItemId, "jira", "PROJ-1", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jira");
    }

    // ── createAndLink ─────────────────────────────────────────────────────────

    @Test
    void createAndLink_createsIssueAndReturnsLink() {
        final UUID workItemId = UUID.randomUUID();
        when(provider.createIssue(eq(workItemId), eq("Fix it"), any()))
                .thenReturn(Optional.of("owner/repo#99"));
        when(provider.fetchIssue("owner/repo#99"))
                .thenReturn(new ExternalIssueRef("github", "owner/repo#99", "Fix it", "https://gh/99", "open"));

        final WorkItemIssueLink link =
                service.createAndLink(workItemId, "github", "Fix it", "Details", "system");

        assertThat(link.externalRef).isEqualTo("owner/repo#99");
        assertThat(link.title).isEqualTo("Fix it");
        verify(linkStore).save(any(WorkItemIssueLink.class));
    }

    @Test
    void createAndLink_throws_whenProviderDoesNotSupportCreation() {
        when(provider.createIssue(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createAndLink(UUID.randomUUID(), "github", "Title", "Body", "alice"))
                .isInstanceOf(IssueTrackerException.class)
                .hasMessageContaining("does not support issue creation")
                .matches(e -> !((IssueTrackerException) e).isNotFound());
    }

    // ── listLinks ─────────────────────────────────────────────────────────────

    @Test
    void listLinks_delegatesToStore() {
        final UUID workItemId = UUID.randomUUID();
        final List<WorkItemIssueLink> links = List.of(
                existingLink(workItemId, "github", "owner/repo#1"),
                existingLink(workItemId, "github", "owner/repo#2"));
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(links);

        assertThat(service.listLinks(workItemId)).hasSize(2);
        verify(linkStore).findByWorkItemId(workItemId);
    }

    // ── removeLink ────────────────────────────────────────────────────────────

    @Test
    void removeLink_found_deletesAndReturnsTrue() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = existingLink(workItemId, "github", "owner/repo#3");
        when(linkStore.findById(link.id)).thenReturn(Optional.of(link));

        assertThat(service.removeLink(link.id, workItemId)).isTrue();
        verify(linkStore).delete(link);
    }

    @Test
    void removeLink_notFound_returnsFalse() {
        when(linkStore.findById(any())).thenReturn(Optional.empty());

        assertThat(service.removeLink(UUID.randomUUID(), UUID.randomUUID())).isFalse();
        verify(linkStore, never()).delete(any());
    }

    @Test
    void removeLink_wrongWorkItem_returnsFalse() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = existingLink(workItemId, "github", "owner/repo#4");
        when(linkStore.findById(link.id)).thenReturn(Optional.of(link));

        assertThat(service.removeLink(link.id, UUID.randomUUID())).isFalse();
        verify(linkStore, never()).delete(any());
    }

    // ── syncLinks ─────────────────────────────────────────────────────────────

    @Test
    void syncLinks_updatesStatusAndSaves() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = existingLink(workItemId, "github", "owner/repo#6");
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of(link));
        when(provider.fetchIssue("owner/repo#6"))
                .thenReturn(new ExternalIssueRef("github", "owner/repo#6", "Issue", "https://gh/6", "closed"));

        final int synced = service.syncLinks(workItemId);

        assertThat(synced).isEqualTo(1);
        assertThat(link.status).isEqualTo("closed");
        verify(linkStore).save(link);
    }

    @Test
    void syncLinks_continuesOnPartialFailure() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink good = existingLink(workItemId, "github", "owner/repo#7");
        final WorkItemIssueLink broken = existingLink(workItemId, "github", "owner/repo#BROKEN");
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of(good, broken));
        when(provider.fetchIssue("owner/repo#7"))
                .thenReturn(new ExternalIssueRef("github", "owner/repo#7", "Good", "https://gh/7", "open"));
        when(provider.fetchIssue("owner/repo#BROKEN"))
                .thenThrow(IssueTrackerException.notFound("owner/repo#BROKEN"));

        final int synced = service.syncLinks(workItemId);

        assertThat(synced).isEqualTo(1);
        verify(linkStore).save(good);
        verify(linkStore, never()).save(broken);
    }

    // ── onLifecycleEvent ──────────────────────────────────────────────────────

    @Test
    void onLifecycleEvent_callsSyncToIssue_forEachLink() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link1 = existingLink(workItemId, "github", "owner/repo#20");
        final WorkItemIssueLink link2 = existingLink(workItemId, "github", "owner/repo#21");
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of(link1, link2));

        final WorkItem wi = workItem(workItemId, WorkItemStatus.IN_PROGRESS, WorkItemPriority.HIGH);
        service.onLifecycleEvent(WorkItemLifecycleEvent.of("STARTED", wi, "alice", null));

        verify(provider).syncToIssue("owner/repo#20", wi);
        verify(provider).syncToIssue("owner/repo#21", wi);
    }

    @Test
    void onLifecycleEvent_noLinks_doesNotSync() {
        final UUID workItemId = UUID.randomUUID();
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of());

        final WorkItem wi = workItem(workItemId, WorkItemStatus.PENDING, WorkItemPriority.MEDIUM);
        service.onLifecycleEvent(WorkItemLifecycleEvent.of("CREATED", wi, "system", null));

        verify(provider, never()).syncToIssue(any(), any());
    }

    @Test
    void onLifecycleEvent_autoClose_closesIssueAndSavesLink() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = existingLink(workItemId, "github", "owner/repo#30");
        link.status = "open";
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of(link));
        when(githubConfig.autoCloseOnComplete()).thenReturn(true);

        final WorkItem wi = workItem(workItemId, WorkItemStatus.COMPLETED, WorkItemPriority.MEDIUM);
        service.onLifecycleEvent(WorkItemLifecycleEvent.of("COMPLETED", wi, "alice", "done"));

        verify(provider).syncToIssue(eq("owner/repo#30"), eq(wi));
        verify(provider).closeIssue(eq("owner/repo#30"), any());
        assertThat(link.status).isEqualTo("closed");
        verify(linkStore).save(link);
    }

    @Test
    void onLifecycleEvent_unknownProvider_skipsAndContinues() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink jiraLink = existingLink(workItemId, "jira", "PROJ-1");
        final WorkItemIssueLink ghLink = existingLink(workItemId, "github", "owner/repo#40");
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of(jiraLink, ghLink));

        final WorkItem wi = workItem(workItemId, WorkItemStatus.ASSIGNED, WorkItemPriority.LOW);
        service.onLifecycleEvent(WorkItemLifecycleEvent.of("ASSIGNED", wi, "alice", null));

        verify(provider).syncToIssue("owner/repo#40", wi);
        verify(provider, never()).syncToIssue(eq("PROJ-1"), any());
    }

    @Test
    void onLifecycleEvent_syncFailure_swallowed() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink link = existingLink(workItemId, "github", "owner/repo#50");
        when(linkStore.findByWorkItemId(workItemId)).thenReturn(List.of(link));
        doThrow(new RuntimeException("network error")).when(provider).syncToIssue(any(), any());

        final WorkItem wi = workItem(workItemId, WorkItemStatus.IN_PROGRESS, WorkItemPriority.HIGH);

        // Should not throw
        service.onLifecycleEvent(WorkItemLifecycleEvent.of("STARTED", wi, "alice", null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItemIssueLink existingLink(
            final UUID workItemId, final String trackerType, final String externalRef) {
        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.id = UUID.randomUUID();
        link.workItemId = workItemId;
        link.trackerType = trackerType;
        link.externalRef = externalRef;
        link.status = "open";
        link.linkedBy = "test";
        return link;
    }

    private WorkItem workItem(final UUID id, final WorkItemStatus status, final WorkItemPriority priority) {
        final WorkItem wi = new WorkItem();
        wi.id = id;
        wi.status = status;
        wi.priority = priority;
        wi.title = "Test WorkItem";
        return wi;
    }
}
