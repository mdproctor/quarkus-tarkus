package io.quarkiverse.work.runtime.api;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkiverse.work.runtime.service.WorkItemNotFoundException;

@Provider
public class WorkItemNotFoundExceptionMapper implements ExceptionMapper<WorkItemNotFoundException> {

    @Override
    public Response toResponse(final WorkItemNotFoundException e) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
