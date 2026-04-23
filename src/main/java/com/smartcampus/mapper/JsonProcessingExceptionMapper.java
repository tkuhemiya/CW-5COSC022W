package com.smartcampus.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(JsonProcessingException exception) {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Invalid request body");
        body.put("message", "One or more fields in the JSON payload are invalid.");

        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .build();
    }
}
