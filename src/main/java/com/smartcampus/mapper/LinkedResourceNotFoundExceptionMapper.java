package com.smartcampus.mapper;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
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
