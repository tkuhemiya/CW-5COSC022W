package com.smartcampus.mapper;

import com.smartcampus.exception.SensorUnavailableException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class SensorUnavailableExceptionMapper implements ExceptionMapper<SensorUnavailableException> {

    @Override
    public Response toResponse(SensorUnavailableException exception) {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Sensor unavailable for new readings");
        body.put("message", exception.getMessage());
        body.put("sensorId", exception.getSensorId());
        body.put("status", exception.getStatus());
        return Response.status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON)
            .entity(body)
            .build();
    }
}
