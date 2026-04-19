package com.smartcampus.mapper;

import com.smartcampus.exception.SensorUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class SensorUnavailableExceptionMapper implements ExceptionMapper<SensorUnavailableException> {

    @Override
    public Response toResponse(SensorUnavailableException exception) {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of(
                "error", "Sensor unavailable for new readings",
                "message", exception.getMessage(),
                "sensorId", exception.getSensorId(),
                "status", exception.getStatus()
            ))
            .build();
    }
}
