package com.smartcampus.resource;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.data.DataStore;
import com.smartcampus.exception.LinkedResourceNotFoundException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/sensors")
public class SensorResource {

    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Sensor> getAllSensors(@QueryParam("type") String type) {
        List<Sensor> sensors = List.copyOf(DataStore.sensors.values());
        
        if (type != null && !type.isEmpty()) {
            return sensors.stream()
                .filter(s -> type.equalsIgnoreCase(s.getType()))
                .collect(Collectors.toList());
        }
        
        return sensors;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Sensor ID is required"))
                .build();
        }
        
        if (DataStore.sensors.containsKey(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "Sensor already exists"))
                .build();
        }
        
        if (sensor.getRoomId() != null && !sensor.getRoomId().isEmpty()) {
            if (!DataStore.rooms.containsKey(sensor.getRoomId())) {
                throw new LinkedResourceNotFoundException("Room", sensor.getRoomId());
            }

            Room room = DataStore.rooms.get(sensor.getRoomId());
            synchronized (room) {
                room.getSensorIds().add(sensor.getId());
            }
        }
        
        DataStore.sensors.put(sensor.getId(), sensor);
        return Response.status(Response.Status.CREATED)
            .entity(sensor)
            .build();
    }
}
