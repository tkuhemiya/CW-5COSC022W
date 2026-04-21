package com.smartcampus.mapper;

import com.smartcampus.exception.SensorUnavailableException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
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
