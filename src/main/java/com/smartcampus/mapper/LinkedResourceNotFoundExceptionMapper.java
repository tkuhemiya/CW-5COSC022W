package com.smartcampus.mapper;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class LinkedResourceNotFoundExceptionMapper implements ExceptionMapper<LinkedResourceNotFoundException> {

    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Linked resource not found");
        body.put("message", exception.getMessage());
        body.put("resourceType", exception.getResourceType());
        body.put("resourceId", exception.getResourceId());
        return Response.status(422)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .build();
    }
}
