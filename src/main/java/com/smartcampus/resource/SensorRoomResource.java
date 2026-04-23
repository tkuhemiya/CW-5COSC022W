package com.smartcampus.resource;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.data.DataStore;
import com.smartcampus.exception.RoomNotEmptyException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.util.HashMap;
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
            Map<String, String> body = new HashMap<>();
            body.put("error", "Room ID is required");
            return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
        }
        
        if (DataStore.rooms.containsKey(room.getId())) {
            Map<String, String> body = new HashMap<>();
            body.put("error", "Room already exists");
            return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
        }
        
        DataStore.rooms.put(room.getId(), room);
        return Response.status(Response.Status.CREATED)
            .location(UriBuilder.fromPath("/api/v1/rooms/{id}").build(room.getId()))
            .entity(room)
            .build();
    }

    @GET
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);
        if (room == null) {
            Map<String, String> body = new HashMap<>();
            body.put("error", "Room not found");
            return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
        }
        return Response.ok(room).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = DataStore.rooms.get(roomId);
        
        if (room == null) {
            return Response.status(Response.Status.NO_CONTENT).build();
        }

        synchronized (room) {
            // re fetch inside to get latest state
            room = DataStore.rooms.get(roomId);
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
}
