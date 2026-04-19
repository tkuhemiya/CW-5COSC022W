package com.smartcampus.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of(
                "error", "Internal server error",
                "message", "An unexpected error occurred"
            ))
            .build();
    }
}
