package com.smartcampus.exception;

public class RoomNotEmptyException extends RuntimeException {
    private final String roomId;
    private final String sensorId;

    public RoomNotEmptyException(String roomId, String sensorId) {
        super("Room " + roomId + " has active sensor " + sensorId);
        this.roomId = roomId;
        this.sensorId = sensorId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSensorId() {
        return sensorId;
    }
}
