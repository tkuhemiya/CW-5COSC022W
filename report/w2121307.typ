#import "@preview/codelst:2.0.2": sourcecode

#align(center + top)[
  #v(3cm)
  
  #grid(
    columns: (1fr, 1fr),
    gutter: 1cm,
    align(center)[#image("media/uow.jpg", width: 6cm)],
    align(center)[#image("media/iit.png", width: 6cm)]
  )
  
  #v(1cm)
  
  #text(size: 20pt, weight: "bold")[University of Westminster]
  
  #v(0.1cm)

  #text(size: 18pt, weight: "bold")[5COSC022W Client-Server Architectures]
  
  #v(0.1cm)
  
  #text(size: 14pt)[Coursework Report — 2025/26]
  
  #v(1cm)
  
  #text(size: 13pt)[
    Student Name: Themiya Deemantha Kulathunga\
    UOW Student ID: w2121307\
    IIT Student ID: 20241123
  ]
  
  #v(0.2cm)
  
  #text(size: 12pt)[
    Module Leader: Hamed Hamzeh \
    Submission Date: 24th April 2026
  ]
  
  #v(1.5cm)
  
  #text(size: 14pt, weight: "bold")[Smart Campus Sensor & Room Management API]
]

#pagebreak()

#set page(
  margin: (top: 0.7in, bottom: 0.7in, left: 0.7in, right: 0.7in),
  numbering: "1",
  number-align: center
)

#set text(font: "Times New Roman", size: 11pt)
//#set heading(numbering: "1.1.1")

#align(center)[
  #text(size: 16pt, weight: "bold")[Report Questions and Answers]
]

#v(1cm)

= Part 1: Service Architecture & Setup

== Question 1.1

*Question:* Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request, or is it treated as a singleton? How does this impact how you manage and synchronise your in-memory data structures (maps/lists) to prevent data loss or race conditions?

*Answer:*

By default, a JAX-RS resource class is request-scoped, meaning a new instance is created for each HTTP request. This is different from singleton scope, where the same instance would be shared across all requests. The request-scoped default is actually safer for thread safety because it prevents accidental state sharing between requests, but it also means we cannot use instance fields to hold shared data.

This lifecycle matters because if we used instance fields for the `DataStore`, each request would see a different (empty) map, causing data to appear to "disappear" between requests. For example, if `SensorRoomResource` had a private field `Map<String, Room> rooms = new HashMap<>()`, Room A created in Request 1 wouldn't exist in Request 2 because each request gets a fresh resource instance.

To solve this, the project uses a static `DataStore` with `ConcurrentHashMap`:

#block(breakable: false)[
#sourcecode[```java
public static final Map<String, Room> rooms = new ConcurrentHashMap<>();
public static final Map<String, Sensor> sensors = new ConcurrentHashMap<>();
public static final Map<String, List<SensorReading>> readings = new ConcurrentHashMap<>();
```]
]

`ConcurrentHashMap` is thread-safe by design — multiple threads can read and write without corrupting the data structure. We also use `synchronized` blocks around compound operations (like adding a sensor to a room's sensor list) to ensure consistency when multiple requests modify related data at the same time.

== Question 1.2

*Question:* Why is "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

*Answer:*

Hypermedia is important because it allows the API to describe available actions through links in the response. This makes the API more self-descriptive and reduces reliance on hard-coded URLs or external documentation. The key benefit is that clients follow server-provided links rather than constructing URLs manually, which makes them more adaptable to API changes.

Compared to static documentation, hypermedia is self-documenting and doesn't go stale. If the API structure changes (e.g., moving from `/api/v1/rooms` to `/api/v2/rooms`), clients that follow links will automatically adapt, while clients with hard-coded URLs would break. Static documentation requires manual updates and developers might still be using outdated API references.

A real example in this project is the discovery endpoint, which returns the API version, base path, contact information, and resource links:

#block(breakable: false)[
#sourcecode[```json
{
  "version": "1.0",
  "api": "/api/v1",
  "contact": "smartcampus@westminster.ac.uk",
  "resources": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```]
]

Instead of constructing URLs manually, the client can follow the links returned by the server. For example, a client can parse the response, extract `"resources.rooms"`, and use that value for subsequent API calls.


= Part 2: Room Management

== Question 2.1

*Question:* When returning a list of rooms, what are the implications of returning only IDs versus full room objects? Consider network bandwidth and client-side processing.

*Answer:*

Returning only IDs uses less bandwidth, but it forces the client to make extra requests before it can display useful room details. This creates the N+1 requests problem: 1 request to get the list of IDs, plus N additional requests to fetch each room's details. If there are 100 rooms, that's 101 total requests.

Returning full room objects increases response size, but it usually simplifies client processing and reduces the number of round trips required. For this reason, full objects are generally more efficient for typical application use, while IDs are only preferable when minimal payload size is the priority (e.g., mobile apps with limited data plans).

For example:

#block(breakable: false)[
#sourcecode[
```json
// IDs only - creates N+1 problem
["LIB-301", "LAB-101", "OFF-205"]
// Client must then call GET /rooms/LIB-301, GET /rooms/LAB-101, etc.

// Full room objects - single request, all data
[
  {"id": "LIB-301", "name": "Library Quiet Study", "capacity": 50, "sensorIds": ["TEMP-001"]},
  {"id": "LAB-101", "name": "Computer Lab A", "capacity": 30, "sensorIds": []}
]
```]
]

If the client needs the room name and capacity anyway, the second option is more practical.

== Question 2.2

*Question:* Is the DELETE operation idempotent in your implementation? Justify this by describing what happens if a client sends the same DELETE request multiple times.

*Answer:*

Yes, the DELETE operation is idempotent in this implementation. An operation is idempotent when repeating it multiple times produces the same result as doing it once. In this case, whether the room existed initially or not, after any number of DELETE requests, the final state is the same: the room is absent. This is the mathematical definition of idempotency.

Here's what happens:

- First DELETE: Room exists → gets deleted → returns `204 No Content`. Server state: room is absent.
- Second DELETE: Room already absent → still returns `204 No Content`. Server state: room is absent.
- N-th DELETE: Same result, same state.

This is shown in the resource method below:

#block(breakable: false)[
#sourcecode[```java
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
```]
]

The important point is that the final server state is identical after one DELETE or repeated DELETE requests — the room is gone. This is different from non-idempotent operations like POST (creating a room twice creates two rooms).


= Part 3: Sensor Operations & Filtering

== Question 3.1

*Question:* We use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. What happens if a client sends data in a different format such as `text/plain` or `application/xml`? How does JAX-RS handle this mismatch?

*Answer:*

If the request body uses a media type other than JSON, JAX-RS returns `415 Unsupported Media Type`. The resource method is not called because the framework checks the `Content-Type` header before method dispatch. Even if the body contains valid JSON syntax, sending the wrong `Content-Type` causes JAX-RS to return 415 before the resource method is ever invoked, because the framework validates the header first.

For example, this request will fail because the body is labelled as `text/plain` instead of JSON:

#block(breakable: false)[
#sourcecode[```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: text/plain" \
  -d '{"id": "LIB-301", "name": "Test", "capacity": 50}'
```]
]

Response: `HTTP 415 Unsupported Media Type`

In contrast, the same payload with `Content-Type: application/json` would be accepted and passed to the resource method:

#block(breakable: false)[
#sourcecode[```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id": "LIB-301", "name": "Test", "capacity": 50}'
```]
]

Response: `HTTP 201 Created`

== Question 3.2

*Question:* You implemented filtering using `@QueryParam`. Contrast this with an alternative where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

*Answer:*

Query parameters are better suited to filtering because they express a condition on a collection rather than a separate resource. For example, `GET /api/v1/sensors?type=CO2` clearly means "return only sensors of type CO2". Query parameters are also more flexible because several filters can be combined in one request, such as `?type=CO2&status=ACTIVE`, without changing the route structure.

In contrast, using `/api/v1/sensors/type/CO2` incorrectly implies that 'CO2' is a sub-resource (like a collection), when it's actually a filter criterion on the sensors collection. This design is less flexible — adding another filter like status would require a completely different URL structure (e.g., `/api/v1/sensors/type/CO2/status/ACTIVE`), making the API harder to maintain.

This matches the implementation in the resource class:

#block(breakable: false)[
#sourcecode[```java
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
```]
]

This is more suitable than path-based filtering because the path should identify the resource collection, while the query string should refine the results.


= Part 4: Deep Nesting with Sub-Resources

== Question 4.1

*Question:* Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

*Answer:*

The sub-resource locator pattern improves maintainability by moving nested functionality into dedicated classes. In this project, the sensor resource delegates `/sensors/{sensorId}/readings` to `SensorReadingResource`, which keeps sensor handling and reading handling separate. This approach reduces class size, improves clarity, and makes the API easier to extend than placing all nested routes in one large controller. Separate classes also improve testability — `SensorReadingResource` can be unit-tested independently without loading the entire `SensorResource`.

The implementation uses a sub-resource locator method:

#block(breakable: false)[
#sourcecode[
```java
@Path("/{sensorId}/readings")
public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```]
]

This means that the `SensorResource` class only needs to handle routing, while the `SensorReadingResource` class handles operations such as:

#block(breakable: false)[
#sourcecode[```java
@GET
public Response getReadings() {
    Sensor sensor = DataStore.sensors.get(sensorId);
    if (sensor == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    List<SensorReading> history = DataStore.readings.getOrDefault(sensorId, List.of());
    return Response.ok(List.copyOf(history)).build();
}
```]
]

As a result, each class has a narrower responsibility, which improves readability and testing.


= Part 5: Error Handling, Exception Mapping & Logging

== Question 5.2

*Question:* Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

*Answer:*

HTTP 422 is more appropriate because the endpoint itself exists, but the submitted data cannot be processed successfully. In this project, a request to create a sensor may be syntactically valid JSON, but if the `roomId` does not refer to an existing room, the problem is with the content of the payload rather than the URL.

A `404 Not Found` would incorrectly suggest that the `/api/v1/sensors` endpoint doesn't exist, when in fact the endpoint is perfectly valid and only the `roomId` value in the JSON payload is invalid. This distinction matters for API consumers: a 404 tells them to check their URL, while a 422 tells them to check their request body. For example, if a client posts `{"id": "TEMP-001", "roomId": "NON-EXISTENT"}` to `/api/v1/sensors`, returning 404 would be confusing because the URL is correct — the issue is specifically that room "NON-EXISTENT" doesn't exist.

The project maps this error with an exception mapper:

#block(breakable: false)[
#sourcecode[```java
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
```]
]

For example, posting a sensor with a non-existent room returns `422` rather than `404`, because the API route is valid and only the linked resource is invalid.


== Question 5.4

*Question:* From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

*Answer:*

Exposing stack traces is unsafe because they reveal internal implementation details that attackers can exploit. A raw stack trace can expose:

- Class names and package structure: revealing the application architecture (e.g., `com.smartcampus.resource.SensorRoomResource`)
- Method names: showing what operations are available (e.g., `createRoom`, `deleteRoom`)
- File paths and line numbers: pinpointing exactly where vulnerabilities might exist
- Framework versions: from package names like `jersey` or `jackson`, revealing known CVEs

For example, if a `NullPointerException` occurred in the `SensorRoomResource.createRoom` method, a raw stack trace might look like this:

#sourcecode[```
java.lang.NullPointerException: Cannot invoke "com.smartcampus.model.Room.getId()" because "room" is null
    at com.smartcampus.resource.SensorRoomResource.createRoom(SensorRoomResource.java:26)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
    at org.glassfish.jersey.server.model.internal.ResourceMethodInvocationHandlerFactory$1.invoke(ResourceMethodInvocationHandlerFactory.java:81)
    at org.glassfish.jersey.server.model.internal.AbstractJavaResourceMethodDispatcher$1.run(AbstractJavaResourceMethodDispatcher.java:144)
```]

From this single error, an attacker learns:

+ The exact package structure (`com.smartcampus.resource`)
+ The class name (`SensorRoomResource`) and method (`createRoom`)
+ The internal model class (`Room`) and its methods (`getId`)
+ The file location and line number (`SensorRoomResource.java:26`)
+ That the application uses Jersey framework (from `org.glassfish.jersey`)

With this information, an attacker could search for known vulnerabilities in Jersey, craft targeted attacks against the `Room` model, or focus on line 26 of `SensorRoomResource.java` for further exploitation.

The safer approach is to return a generic error message to the client and keep the full trace in server-side logs.

In this project, the global exception mapper returns a generic response instead of exposing the stack trace directly:

#block(breakable: false)[
#sourcecode[```java
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
```]
]

This safe response reveals nothing about the internal code structure, framework, or error location, while the detailed stack trace is logged server-side for debugging.


== Question 5.5

*Question:* Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every resource method?

*Answer:*

Filters are preferable because logging is a cross-cutting concern rather than business logic. A filter can handle request and response logging in one place, which avoids repetition across resource methods. This keeps the codebase cleaner, improves consistency, and makes future logging changes easier to apply.

The project uses a filter for both request and response logging:

#block(breakable: false)[
#sourcecode[```java
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
```]
]

If logging were inserted manually into every resource method, the same code would need to be repeated in multiple places, which is less maintainable.
