package com.smartcampus.mapper;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class LinkedResourceNotFoundExceptionMapper implements ExceptionMapper<LinkedResourceNotFoundException> {

    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        return Response.status(422)
            .entity(Map.of(
                "error", "Linked resource not found",
                "message", exception.getMessage(),
                "resourceType", exception.getResourceType(),
                "resourceId", exception.getResourceId()
            ))
            .build();
    }
}
