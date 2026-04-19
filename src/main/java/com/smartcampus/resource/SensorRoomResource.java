package com.smartcampus.resource;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.data.DataStore;
import com.smartcampus.exception.RoomNotEmptyException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/rooms")
public class SensorRoomResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Room> getAllRooms() {
        return List.copyOf(DataStore.rooms.values());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createRoom(Room room) {
        if (room == null || room.getId() == null || room.getId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Room ID is required"))
                .build();
        }
        
        if (DataStore.rooms.containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "Room already exists"))
                .build();
        }
        
        DataStore.rooms.put(room.getId(), room);
        return Response.status(Response.Status.CREATED)
            .entity(room)
            .build();
    }

    @GET
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Room not found"))
                .build();
        }
        return Response.ok(room).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);
        
        // Idempotent: if room doesn't exist, return 204 (resource is gone)
        if (room == null) {
            return Response.status(Response.Status.NO_CONTENT).build();
        }
        
        List<String> sensorIds = room.getSensorIds();
        if (sensorIds != null && !sensorIds.isEmpty()) {
            for (String sensorId : sensorIds) {
                Sensor sensor = DataStore.sensors.get(sensorId);
                if (sensor != null && "ACTIVE".equals(sensor.getStatus())) {
                    throw new RoomNotEmptyException(roomId, sensorId);
                }
            }
        }
        
        DataStore.rooms.remove(roomId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
