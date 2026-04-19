package com.smartcampus.resource;

import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.data.DataStore;
import com.smartcampus.exception.SensorUnavailableException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class SensorReadingResource {

    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReadings() {
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Sensor not found"))
                .build();
        }

        List<SensorReading> history = DataStore.readings.getOrDefault(sensorId, List.of());
        return Response.ok(history).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading reading) {
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Sensor not found"))
                .build();
        }

        if (reading == null || reading.getId() == null || reading.getId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Reading ID is required"))
                .build();
        }

        if ("MAINTENANCE".equals(sensor.getStatus())) {
            throw new SensorUnavailableException(sensorId, sensor.getStatus());
        }

        DataStore.readings.computeIfAbsent(sensorId, k -> new ArrayList<>()).add(reading);
        sensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED)
            .entity(reading)
            .build();
    }
}
