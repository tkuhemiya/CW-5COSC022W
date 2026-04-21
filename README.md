# Smart Campus REST API

## API Overview

This API manages campus rooms and sensors for the Smart Campus initiative, providing a RESTful interface for campus facilities managers and automated building systems.

### Resources
- **Rooms**: `/api/v1/rooms` - Manage campus rooms (create, list, delete)
- **Sensors**: `/api/v1/sensors` - Manage sensors (create, list, filter by type)
- **Readings**: `/api/v1/sensors/{id}/readings` - Historical sensor data

### Resource Hierarchy
```
/api/v1/
├── rooms/
│   └── {roomId}
├── sensors/
│   └── {sensorId}/
│       └── readings/
```

### Base URL
```
http://localhost:8080/api/v1/
```

---

## Data Models

### Room
```java
public class Room {
    private String id;           // Unique identifier, e.g., "LIB-301"
    private String name;        // Human-readable name, e.g., "Library Quiet Study"
    private int capacity;       // Maximum occupancy for safety regulations
    private List<String> sensorIds = new ArrayList<>(); // IDs of sensors in this room
}
```

### Sensor
```java
public class Sensor {
    private String id;           // Unique identifier, e.g., "TEMP-001"
    private String type;         // Category, e.g., "Temperature", "Occupancy", "CO2"
    private String status;       // Current state: "ACTIVE", "MAINTENANCE", or "OFFLINE"
    private double currentValue; // Most recent measurement recorded
    private String roomId;       // Foreign key linking to the Room
}
```

### SensorReading
```java
public class SensorReading {
    private String id;           // Unique reading event ID
    private long timestamp;      // Epoch time (ms) when the reading was captured
    private double value;        // The actual metric value recorded by hardware
}
```

---

## Project Configuration

This project uses:
- **JAX-RS Implementation**: Jersey 4.0.0
- **Serverlet Container**: Tomcat 10.1
- **JSON Processing**: Jackson
- **Build Tool**: Maven

## Building and Running

### Option 1: Tomcat

copy `target/smartcampus-api.war` to tomcat's `webapps/ROOT.war`
 ```bash
   mvn clean package
   cp target/smartcampus-api.war /path/to/tomcat/webapps/ROOT.war
```

### Option 2: Docker
```bash
docker-compose up --build
```

The server will start on `http://localhost:8080/`, the API is available at `http://localhost:8080/api/v1/`.

---

## Sample curl Commands

### 1. Discovery Endpoint
```bash
curl -X GET http://localhost:8080/api/v1/
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id": "LIB-301", "name": "Library Quiet Study", "capacity": 50}'
```

### 3. Create a Sensor with link to Room
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id": "TEMP-001", "type": "Temperature", "status": "ACTIVE", "currentValue": 22.5, "roomId": "LIB-301"}'
```

### 4. Filter Sensors by Type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=CO2"
```

### 5. Add a Sensor Reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"id": "reading-001", "timestamp": 1703001600000, "value": 23.8}'
```

### 6. Get Sensor Readings
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

### 7. Delete a Room
```bash
# fails if active sensors present
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 8. Try to Create Sensor with Invalid Room (triggers 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id": "TEMP-002", "type": "CO2", "status": "ACTIVE", "roomId": "NON-EXISTENT"}'
```

---

# Report Questions and Answers

## Part 1: Service Architecture & Setup

### Question 1.1
> **Question:** Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request, or is it treated as a singleton? How does this impact how you manage and synchronise your in-memory data structures (maps/lists) to prevent data loss or race conditions?

Answer:

- By default, JAX-RS Resource classes are request scoped, therefor a new instance is created for every HTTP request, we can change the lifecycle by adding the `@Singleton` annotation to the resource class, but we are not doing that in this project.
- Since a new instance is created per request, we cannot store request specific state in instance variables of resource classes. Instead, we must save data in external, shared storage. 

 The shared data structures must be thread safe to prevent data loss or race conditions when multiple concurrent requests access them.
 
1. **ConcurrentHashMap** Used for all primary data stores (`rooms`, `sensors`, `readings`). This provides thread-safe atomic operations for the main collections.

2. **Synchronized blocks on Room objects** When adding a sensor to a room's `sensorIds` list, we synchronize on the room instance:
   ```java
   Room room = DataStore.rooms.get(sensor.getRoomId());
   synchronized (room) {
       room.getSensorIds().add(sensor.getId());
   }
   ```

3. **Synchronized blocks on Sensor objects** When updating `currentValue` after a new reading, we synchronize on the sensor to ensure visibility across threads:
   ```java
   synchronized (sensor) {
       sensor.setCurrentValue(reading.getValue());
   }
   ```

4. **Synchronized blocks on readings lists** The readings lists are wrapped with `Collections.synchronizedList()`, and modifications are synchronized on the list instance.

5. **Compound operation synchronization** For room deletion we check active sensors and remove if there is none, we synchronize on the room to prevent race conditions.

6. **Immutable snapshots** Using `List.copyOf()` when returning data to prevent external modification of internal state.

### Question 1.2
> **Question:** Why is "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

Answer:
HATEOAS (Hypermedia As The Engine Of Application State) means that API responses include links describing what the client can do next, rather than raw data. This benefits client side developers since they dont have to rely on static documentation. With HATEOAS, the API is self-describing and navigable.

A client can discover available actions dynamically from the response itself, without needing to hard code URLs or  external documentation. It also means that if the server changes a URL structure, clients following hypermedia links adapt automatically rather than the client breaking.

**Example Discovery Response:**
```json
{
  "version": "1.0",
  "contact": {
    "name": "Smart Campus Admin",
    "email": "admin@smartcampus.edu"
  },
  "links": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
``` 

---

## Part 2: Room Management

### Question 2.1
> **Question:** When returning a list of rooms, what are the implications of returning only IDs versus full room objects? Consider network bandwidth and client-side processing.

Answer:
Returning only IDs is bandwidth efficient but will forces the client to make additional requests (one per room) to get useful data, It increases latency and places more processing burden on the client.

Returning full room objects in one response is heavier on bandwidth but allows the client to render or process everything from a **single round trip**, which is usually preferable.

**Example Comparison:**
```json
// Only IDs (bandwidth efficient)
["LIB-301", "LAB-101", "OFF-205"]

// Full objects (single round trip)
[
  {"id": "LIB-301", "name": "Library Quiet Study", "capacity": 50, "sensorIds": ["TEMP-001"]},
  {"id": "LAB-101", "name": "Computer Lab A", "capacity": 30, "sensorIds": []}
]
```

### Question 2.2
> **Question:** Is the DELETE operation idempotent in your implementation? Justify this by describing what happens if a client sends the same DELETE request multiple times.

Answer:
Yes, DELETE is idempotent, when calling it multiple times it produces the same server state as calling it once. In this implementation, if you delete a room successfully the first time, subsequent DELETE requests for the same `roomId` should return `HTTP 404 Not Found`, since the resource no longer exists. The server state remains the same (the room is still gone), so idempotency is preserved.

```java
if (room == null) {
    return Response.status(Response.Status.NO_CONTENT).build();
}
```

This makes the operation idempotent because the client can safely retry a DELETE request without worrying about creating unintended side effects or errors.

**Example:**
```bash
# First DELETE - succeeds
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
# Returns: 204 No Content

# Second DELETE - idempotent, same result
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
# Returns: 404 Not Found (room already deleted)
```

---

## Part 3: Sensor Operations & Filtering

### Question 3.1: 
> **Question:** We use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. What happens if a client sends data in a different format such as `text/plain` or `application/xml`? How does JAX-RS handle this mismatch?

Answer:
When a client sends a request with a `Content-Type` that doesn't match what the method declares with `@Consumes(MediaType.APPLICATION_JSON)` for instance, sending `text/plain` or `application/xml`. JAX-RS automatically returns HTTP 415 Unsupported Media Type. The framework inspects the `Content-Type` header before even invoking the method, and if no method matches that content type, the request is rejected and the resource method is never called. This protects deserialization logic from receiving data it cannot parse.

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: text/plain" \
  -d '{"id": "LIB-301", "name": "Test", "capacity": 50}'

# Returns: 415 Unsupported Media Type
{
  "type": "https://tools.ietf.org/html/rfc7231#section-6.5.13",
  "title": "Unsupported Media Type",
  "status": 415,
  "detail": "Media type 'text/plain' not supported"
}
```

### Question 3.2:
> **Question:** You implemented filtering using `@QueryParam`. Contrast this with an alternative where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

Answer:
Using a query parameter (`GET /api/v1/sensors?type=CO2`) is superior compared to using a URL path for filtering for several reasons. A URL path represents a specific resource identity, `/api/v1/sensors/type/CO2` implies "CO2" is a seperate resource, which is misleading. Filtering should be a modification of a collection view, not a new resource, so query parameters are the appropriate mechanism.

Query parameters are also more composable, you can combine multiple filters naturally (`?type=CO2&status=ACTIVE`) without redesigning the URL structure. Path-based filtering becomes unwieldy with multiple criteria. Additionally, query parameters are optional by nature, so omitting them gracefully returns the full list, whereas a path segment requires a separate route definition for the unfiltered case.

**URL Comparison:**
```bash
# Query parameter (recommended)
GET /api/v1/sensors?type=CO2&status=ACTIVE

# Path-based (not recommended)
GET /api/v1/sensors/type/CO2/status/ACTIVE
```

---

## Part 4: Deep Nesting with Sub-Resources

### Question 4.1: 
> **Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

Answer:
In our Implementation we use the sub resource locator pattern in `SensorResource`:

```java
@Path("/{sensorId}/readings")
public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

Here a method returns an object instance rather than a response, and JAX-RS delegates further path matching to that object. This solves a complexity and separation of concerns problem in large APIs.

**SensorReadingResource Example:**
```java
@Path("/")
public class SensorReadingResource {
    private final String sensorId;
    
    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }
    
    @GET
    public List<SensorReading> getReadings() {
        return DataStore.readings.get(sensorId);
    }
    
    @POST
    public Response addReading(SensorReading reading) {
        // Add reading logic
    }
}
```

Without it, every nested endpoint (`/sensors/{id}/readings`, `/sensors/{id}/readings/{readingId}`, etc.) would have to be crammed into a single resource class, making it difficult to maintain. By delegating to a dedicated `SensorReadingResource` class, each class has a single responsibility. One manages sensors, another manages readings. This makes the code easier to test in isolation, easier to read, and easier to extend.

---

## Part 5: Error Handling, Exception Mapping & Logging

### Question 5.2:
> **Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

Answer:
When a client POSTs a new sensor with a `roomId` that doesn't exist, returning `404 Not Found` would be misleading, 404 conventionally means the endpoint itself was not found, but the endpoint (`POST /api/v1/sensors`) is valid and reachable.

`422 Unprocessable Entity` is more semantically accurate because it signals that the request was syntactically well formed but logically invalid, the server understood the payload but could not act on it because a referenced entity is missing. This distinction helps client developers understand exactly what went wrong and where to fix it.

**Example Error Response:**
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id": "TEMP-002", "type": "CO2", "status": "ACTIVE", "roomId": "NON-EXISTENT"}'

# Returns: 422 Unprocessable Entity
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Room with ID 'NON-EXISTENT' not found"
}
```

### Question 5.4:
> **Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

Exposing raw Java stack traces to external API consumers is a security vulnerability for several reasons. Stack traces reveal class names, package structures, and method names, which disclose the internal architecture of the application and make it easier for attackers to identify which frameworks, libraries, and versions are in use. Known vulnerabilities in those libraries can then be targeted directly.

They also expose file paths on the server and line numbers, giving attackers a map of the codebase. A `NullPointerException` at a specific line in a database access class, for example, tells an attacker exactly where your data layer is. Additionally, error messages embedded in exceptions may leak configuration details or sensitive field names. Replacing all of this with a generic `HTTP 500` response and a safe message while logging the full trace server-side can eliminates this information leakage.

**Bad Response (Never expose):**
```json
{
  "error": "Internal Server Error",
  "message": "java.lang.NullPointerException\\n\\tat com.smartcampus.dao.RoomDAO.getRoom(RoomDAO.java:45)\\n\\tat com.smartcampus.service.RoomService.findById(RoomService.java:23)"
}
```

**Good Response (Safe):**
```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected error occurred. Please contact support."
}
```

### Question 5.5:
> **Question:** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every resource method?

Logging is not business logic, it is infrastructure that applies uniformly to every request and response. Inserting `Logger.info()` calls into every resource method has several problems: it is repetitive and error-prone fro example a developer might forget to add it to a new method, it clutters business logic with infrastructure code, and it makes the logging behaviour inconsistent if different developers implement it differently.

A filter is defined once and runs automatically for every request and response without any modification to resource methods. It is also easier to maintain changing the log format or adding a correlation ID only requires editing one class. This approach lets us not repeat our selfs and keeps resource classes focused purely on their domain responsibilities.

---

*Coursework for 5COSC022W - Client-Server Architectures*
