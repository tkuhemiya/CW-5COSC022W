# Smart Campus REST API

## API Overview

### Resources

| Name | API Path | Implementation Class |
|------|----------|----------------------|
| Rooms | `/api/v1/rooms` | `RoomResource` |
| Sensors | `/api/v1/sensors` | `SensorResource` |
| Readings | `/api/v1/sensors/{id}/readings` | `SensorReadingResource` |

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

| Component | Technology |
|-----------|------------|
| JAX-RS Implementation | Jersey 2.32 |
| Servlet Container | Tomcat 9 |
| JSON Processing | Jackson |
| Build Tool | Maven |
| Java Version | 8 |
| Architecture | RESTful API with sub-resource locators |

## Building and Running

### Prerequisites
- JDK 8 or later
- Maven 3.6+
- A Servlet 3.1+ container

### Build

```bash
mvn clean package
# produces: target/smartcampus-api.war
```

### Deploy

You can run the application in two ways:

#### Option 1: Run with NetBeans
- Open the project in NetBeans and run it. The API will be available at `http://localhost:8080/smartcampus-api/api/v1`, This is because the default context root matches the WAR file name (`smartcampus-api`).
- Deploying to the root context (`/`) may fail because a default `ROOT` application already exists in the server. You must remove or replace the existing `ROOT` application to use `/` and the api be at `http://localhost:8080/api/v1`.

#### Option 2: Deploy to a Servlet Container (Tomcat)
- Build and copy the WAR file to the container’s `webapps/` directory:

```bash
mvn clean package
cp target/smartcampus-api.war /path/to/tomcat/webapps/ROOT.war
```

- The server will start on `http://localhost:8080/`, the API is available at `http://localhost:8080/api/v1/`.

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

By default, a JAX-RS resource class is **request-scoped**, meaning a new instance is created for each HTTP request. This is different from **singleton scope**, where the same instance would be shared across all requests. The request-scoped default is actually safer for thread safety because it prevents accidental state sharing between requests, but it also means we cannot use instance fields to hold shared data.

This lifecycle matters because if we used instance fields for the `DataStore`, each request would see a different empty map, causing data to appear to "disappear" between requests. For example, if `SensorRoomResource` had a private field `Map<String, Room> rooms = new HashMap<>()`, Room A created in Request 1 wouldn't exist in Request 2 because each request gets a fresh resource instance.

To solve this, the project uses a **static `DataStore`** with `ConcurrentHashMap`:
```java
public static final Map<String, Room> rooms = new ConcurrentHashMap<>();
public static final Map<String, Sensor> sensors = new ConcurrentHashMap<>();
public static final Map<String, List<SensorReading>> readings = new ConcurrentHashMap<>();
```
`ConcurrentHashMap` is thread-safe by design multiple threads can read and write without corrupting the data structure. We also use `synchronized` blocks around compound operations like adding a sensor to a room's sensor list to ensure consistency when multiple requests modify related data at the same time.

### Question 1.2
> **Question:** Why is "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

Hypermedia is important because it allows the API to describe available actions through links in the response. This makes the API more self-descriptive and reduces reliance on hard-coded URLs or external documentation. The key benefit is that **clients follow server-provided links rather than constructing URLs manually**, which makes them more adaptable to API changes.

Compared to static documentation, hypermedia is self-documenting and doesn't go stale. If the API structure changes (e.g., moving from `/api/v1/rooms` to `/api/v2/rooms`), clients that follow links will automatically adapt, while clients with hard-coded URLs would break. Static documentation requires manual updates and developers might still be using outdated API references.

A real example in this project is the discovery endpoint, which returns the API version, base path, contact information, and resource links:
```json
{
  "version": "1.0",
  "api": "/api/v1",
  "contact": "smartcampus@westminster.ac.uk",
  "resources": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```
Instead of constructing URLs manually, the client can follow the links returned by the server. For example, a client can parse the response, extract `"resources.rooms"`, and use that value for subsequent API calls.

---

## Part 2: Room Management

### Question 2.1
> **Question:** When returning a list of rooms, what are the implications of returning only IDs versus full room objects? Consider network bandwidth and client-side processing.

Returning only IDs uses less bandwidth, but it forces the client to make extra requests before it can display useful room details. This creates the **N+1 requests problem**: 1 request to get the list of IDs, plus N additional requests to fetch each room's details. If there are 100 rooms, that's 101 total requests.

Returning full room objects increases response size, but it usually simplifies client processing and reduces the number of round trips required. For this reason, full objects are generally more efficient for typical application use, while IDs are only preferable when minimal payload size is the priority.

For example:
```json
// IDs only - creates N+1 problem
["LIB-001", "LAB-001", "OFF-001"]
// Client must then call GET /rooms/LIB-301, GET /rooms/LAB-101, etc.

// Full room objects - single request, all data
[
  {"id": "LIB-001", "name": "Library Quiet Study", "capacity": 50, "sensorIds": ["TEMP-001"]},
  {"id": "LAB-001", "name": "Computer Lab A", "capacity": 30, "sensorIds": []}
  {"id": "OFF-001", "name": "Teachers Office", "capacity": 10, "sensorIds": []}
]
```
If the client needs the room name and capacity anyway, the second option is more practical.

### Question 2.2
> **Question:** Is the DELETE operation idempotent in your implementation? Justify this by describing what happens if a client sends the same DELETE request multiple times.

Yes, the DELETE operation is idempotent in this implementation. An operation is idempotent when repeating it multiple times produces the same result as doing it once. In this case, whether the room existed initially or not, after any number of DELETE requests, the final state is the same: the room is absent.

what happens in the application:
- **First DELETE**: Room exists → gets deleted → returns `204 No Content`. Server state: room is absent.
- **Second DELETE**: Room already absent → still returns `204 No Content`. Server state: room is absent.
- **N-th DELETE**: Same result, same state.

This is shown in the resource method below:
```java
@DELETE
@Path("/{roomId}")
public Response deleteRoom(@PathParam("roomId") String roomId) {
    Room room = DataStore.rooms.get(roomId);

    if (room == null) {
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    DataStore.rooms.remove(roomId);
    return Response.status(Response.Status.NO_CONTENT).build();
}
```
Importantly the final server state is identical after one DELETE or repeated DELETE requests. The room is gone. This is different from non-idempotent operations like POST, for example creating a room twice creates two rooms.

---

## Part 3: Sensor Operations & Filtering

### Question 3.1:
> **Question:** We use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. What happens if a client sends data in a different format such as `text/plain` or `application/xml`? How does JAX-RS handle this mismatch?

If the request body uses a media type other than JSON, JAX-RS returns `415 Unsupported Media Type`. The resource method is not called because the framework checks the `Content-Type` header before method dispatch. Even if the body contains valid JSON syntax, sending the wrong `Content-Type` causes JAX-RS to return 415 before the resource method is ever invoked, because the framework validates the header first.

For example, this request will fail because the body is labelled as `text/plain` instead of JSON:
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: text/plain" \
  -d '{"id": "LIB-301", "name": "Test", "capacity": 50}'
```
Response: `HTTP 415 Unsupported Media Type`

In contrast, the same payload with `Content-Type: application/json` would be accepted and passed to the resource method:
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id": "LIB-301", "name": "Test", "capacity": 50}'
```
Response: `HTTP 201 Created`

### Question 3.2:
> **Question:** You implemented filtering using `@QueryParam`. Contrast this with an alternative where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

Query parameters are better suited to filtering because they express a condition on a collection rather than a separate resource. For example, `GET /api/v1/sensors?type=CO2` clearly means “return only sensors of type CO2”. Query parameters are also more flexible because several filters can be combined in one request, such as `?type=CO2&status=ACTIVE`, without changing the route structure.

In contrast, using `/api/v1/sensors/type/CO2` incorrectly implies that 'CO2' is a sub-resource (like a collection), when it's actually a filter criterion on the sensors collection. This design is less flexible: adding another filter like status would require a completely different URL structure (e.g., `/api/v1/sensors/type/CO2/status/ACTIVE`), making the API harder to maintain.

This matches the implementation in the resource class:
```java
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
```
This is more suitable than path-based filtering because the path should identify the resource collection, while the query string should refine the results.

---

## Part 4: Deep Nesting with Sub-Resources

### Question 4.1:
> **Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

The sub-resource locator pattern improves maintainability by moving nested functionality into dedicated classes. In this project, the sensor resource delegates `/sensors/{sensorId}/readings` to `SensorReadingResource`, which keeps sensor handling and reading handling separate. This approach reduces class size, improves clarity, and makes the API easier to extend than placing all nested routes in one large controller. Separate classes also improve testability - `SensorReadingResource` can be unit-tested independently without loading the entire `SensorResource`.

The implementation uses a sub-resource locator method:
```java
@Path("/{sensorId}/readings")
public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```
This means that the `SensorResource` class only needs to handle routing, while the `SensorReadingResource` class handles operations such as:
```java
@GET
public Response getReadings() {
    Sensor sensor = DataStore.sensors.get(sensorId);
    if (sensor == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    List<SensorReading> history = DataStore.readings.getOrDefault(sensorId, List.of());
    return Response.ok(List.copyOf(history)).build();
}
```
As a result, each class has a narrower responsibility, which improves readability and testing.

---

## Part 5: Error Handling, Exception Mapping & Logging

### Question 5.2:
> **Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

HTTP 422 is more appropriate because the endpoint itself exists, but the submitted data cannot be processed successfully. In this project, a request to create a sensor may be syntactically valid JSON, but if the `roomId` does not refer to an existing room, the problem is with the content of the payload rather than the URL.

A `404 Not Found` would incorrectly suggest that the `/api/v1/sensors` endpoint doesn't exist, when in fact the endpoint is perfectly valid and only the `roomId` value in the JSON payload is invalid. This distinction matters for API consumers: a 404 tells them to check their URL, while a 422 tells them to check their request body. For example, if a client posts `{"id": "TEMP-001", "roomId": "NON-EXISTENT"}` to `/api/v1/sensors`, returning 404 would be confusing because the URL is correct. The issue is specifically that room "NON-EXISTENT" doesn't exist.

The project maps this error with an exception mapper:
```java
@Provider
public class LinkedResourceNotFoundExceptionMapper implements ExceptionMapper<LinkedResourceNotFoundException> {
    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        return Response.status(422)
            .entity(Map.of(
                "error", "Linked resource not found",
                "message", exception.getMessage(),
                "resourceType", exception.getResourceType(),
                "resourceId", exception.getResourceId()
            ))
            .build();
    }
}
```
For example, posting a sensor with a non-existent room returns `422` rather than `404`, because the API route is valid and only the linked resource is invalid.

### Question 5.4:
> **Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

Exposing stack traces is unsafe because they reveal internal implementation details that attackers can exploit. A raw stack trace can expose:
- **Class names and package structure**: revealing the application architecture (e.g., `com.smartcampus.resource.SensorRoomResource`)
- **Method names**: showing what operations are available (e.g., `createRoom`, `deleteRoom`)
- **File paths and line numbers**: pinpointing exactly where vulnerabilities might exist
- **Framework versions**: from package names like `jersey` or `jackson`, revealing known CVEs

For example, if a `NullPointerException` occurred in the `SensorRoomResource.createRoom` method, a raw stack trace might look like this:

```
java.lang.NullPointerException: Cannot invoke "com.smartcampus.model.Room.getId()" because "room" is null
    at com.smartcampus.resource.SensorRoomResource.createRoom(SensorRoomResource.java:26)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
    at org.glassfish.jersey.server.model.internal.ResourceMethodInvocationHandlerFactory$1.invoke(ResourceMethodInvocationHandlerFactory.java:81)
    at org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher$1.run(AbstractJavaResourceMethodDispatcher.java:144)
```

From this error, an attacker learns:
1. The exact package structure (`com.smartcampus.resource`)
2. The class name (`SensorRoomResource`) and method (`createRoom`)
3. The internal model class (`Room`) and its methods (`getId`)
4. The file location and line number (`SensorRoomResource.java:26`)
5. That the application uses Jersey framework (from `org.glassfish.jersey`)

With this information, an attacker could search for known vulnerabilities in Jersey, craft targeted attacks against the `Room` model, or focus on line 26 of `SensorRoomResource.java` for further exploitation.

The safer approach is to return a generic error message to the client and keep the full trace in server-side logs.

In this project, the global exception mapper returns a generic response instead of exposing the stack trace directly:
```java
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of(
                "error", "Internal server error",
                "message", "An unexpected error occurred"
            ))
            .build();
    }
}
```
This safe response reveals nothing about the internal code structure, framework, or error location, while the detailed stack trace is logged server-side for debugging.

### Question 5.5:
> **Question:** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every resource method?

Filters are preferable because logging is a cross-cutting concern rather than business logic. A filter can handle request and response logging in one place, which avoids repetition across resource methods. This keeps the codebase cleaner, improves consistency, and makes future logging changes easier to apply.

The project uses a filter for both request and response logging:
```java
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String method = requestContext.getMethod();
        String uri = requestContext.getUriInfo().getRequestUri().toString();
        LOGGER.info(() -> "Request: " + method + " " + uri);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        int status = responseContext.getStatus();
        LOGGER.info(() -> "Response: HTTP " + status);
    }
}
```
If logging were inserted manually into every resource method, the same code would need to be repeated in multiple places, which is less maintainable and easier to forget.

---

*Coursework for 5COSC022W - Client-Server Architectures*
