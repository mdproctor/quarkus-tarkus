package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WorkItemInstancesResourceTest {

    @Inject
    WorkItemTemplateService templateService;

    @Test
    void getInstancesReturnsChildrenWithGroupSummary() {
        // Data created in a committed transaction before the HTTP call
        String parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "InstancesTest";
            t.candidateGroups = "g";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id.toString();
        });

        given()
                .when().get("/workitems/" + parentId + "/instances")
                .then()
                .statusCode(200)
                .body("parentId", equalTo(parentId))
                .body("instanceCount", equalTo(3))
                .body("requiredCount", equalTo(2))
                .body("completedCount", equalTo(0))
                .body("groupStatus", equalTo("IN_PROGRESS"))
                .body("instances", hasSize(3));
    }

    @Test
    void getInstancesReturns404ForNonExistentParent() {
        given()
                .when().get("/workitems/" + UUID.randomUUID() + "/instances")
                .then()
                .statusCode(404);
    }

    @Test
    void getInstancesReturns404ForNonMultiInstanceWorkItem() {
        // Regular (non-multi-instance) WorkItem — no spawn group with requiredCount
        String itemId = inTx(() -> {
            WorkItem item = new WorkItem();
            item.title = "Regular";
            item.status = WorkItemStatus.PENDING;
            item.priority = WorkItemPriority.MEDIUM;
            item.createdBy = "test";
            item.persist();
            return item.id.toString();
        });

        given()
                .when().get("/workitems/" + itemId + "/instances")
                .then()
                .statusCode(404);
    }

    @Transactional
    <T> T inTx(final Supplier<T> s) {
        return s.get();
    }
}
