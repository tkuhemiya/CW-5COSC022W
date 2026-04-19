package com.smartcampus.exception;

public class SensorUnavailableException extends RuntimeException {
    private final String sensorId;
    private final String status;

    public SensorUnavailableException(String sensorId, String status) {
        super("Sensor " + sensorId + " is currently " + status);
        this.sensorId = sensorId;
        this.status = status;
    }

    public String getSensorId() {
        return sensorId;
    }

    public String getStatus() {
        return status;
    }
}
