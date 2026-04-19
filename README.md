# Smart Campus REST API

## Project Configuration

This project uses:
- **JAX-RS Implementation**: Jersey 4.0.0
- **Embedded Server**: Grizzly HTTP Server
- **JSON Processing**: Jackson
- **Build Tool**: Maven

## Application Entry Point

The `SmartCampusApplication` class extends `javax.ws.rs.core.Application` and is annotated with `@ApplicationPath("/api/v1")`, establishing the versioned API entry point at `/api/v1/`.

```java
@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
}
```

## Running the Server

```bash
mvn clean package
java -jar target/CW-5COSC022W-1.0-SNAPSHOT.jar
```

The server will start on `http://localhost:8080/`, the API is available at `http://localhost:8080/api/v1/`.

---

# Report Question: 
## Part 1
### Question 1
> Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request, or is it treated as a singleton? How does this impact how you manage and synchronise your in-memory data structures (maps/lists) to prevent data loss or race conditions?

#### Answer
- By default, JAX-RS Resource classes are request scoped, so a new instance is created for every request. The JAX-RS runtime instantiates the resource class when a request arrives and invokes the appropriate method.
- We can change the lifecycle by adding `@Singleton` annotation to the resource class.
- since a new instance is created per request you cannot store request specific state in resources, you must save them externally. 
- The shared data structures Must be thread Safe to prevent data loss or race conditions.
   - **Use thread-safe collections:** `ConcurrentHashMap` instead of `HashMap`, `CopyOnWriteArrayList` instead of `ArrayList`
   - **Synchronize access:** Use `synchronized` blocks or `ReentrantReadWriteLock` for complex operations
   - **Atomic operations:** Use atomic classes like `AtomicInteger` for counters

   
---



*Coursework for 5COSC022W - Client-Server Architectures*
