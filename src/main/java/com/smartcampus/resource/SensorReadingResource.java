package com.smartcampus.resource;

import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.data.DataStore;
import com.smartcampus.exception.SensorUnavailableException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        return Response.ok(List.copyOf(history)).build();
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

        List<SensorReading> readingList = DataStore.readings.computeIfAbsent(sensorId, 
            k -> Collections.synchronizedList(new ArrayList<>()));
        
        synchronized (readingList) {
            readingList.add(reading);
        }
        
        synchronized (sensor) {
            sensor.setCurrentValue(reading.getValue());
        }

        return Response.status(Response.Status.CREATED)
            .entity(reading)
            .build();
    }
}
