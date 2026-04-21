package com.smartcampus.mapper;

import com.smartcampus.exception.RoomNotEmptyException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class RoomNotEmptyExceptionMapper implements ExceptionMapper<RoomNotEmptyException> {

    @Override
    public Response toResponse(RoomNotEmptyException exception) {
        return Response.status(Response.Status.CONFLICT)
            .entity(Map.of(
                "error", "Cannot delete room with active hardware",
                "message", exception.getMessage(),
                "roomId", exception.getRoomId(),
                "sensorId", exception.getSensorId()
            ))
            .build();
    }
}
