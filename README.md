# Scalable Read-Optimized Backend System Design (Redis + Kafka + NoSQL)

A simplified but production-style CQRS backend system demonstrating:
- Read-through caching with **Redis**
- User identity validation via **AWS Lambda**
- Eventual consistency with **cache updates**
- Separation of read and write paths (lightweight **CQRS**)

## Architecture Overview

![image](Architecture.png)

**Reads**: AWS Lambda validates client identity -> Go to Redis first -> fallback to DB if cache misses

**Writes**: Server updates database & Redis cache

## Technologies Used

- **Redis** - in-memory, write-through cache (updated immediately when the primary database is updated)
- **MongoDB** - persistent storage
- **AWS Lambda** - user identity validation
- **Java Servlet** - multi-threaded backend

## API Endpoint Structure

### Write Endpoints(POST)
- [IP_ADDR]:8080/post_server/posts - Create a new post
- [IP_ADDR]:8080/post_server/users - Create a new user

### Read Endpoints(GET)
- [IP_ADDR]:8080/get_server/posts - Retrieve all posts
- [IP_ADDR]:8080/get_server/posts/{id} - Retrieve a specific post
- [IP_ADDR]:8080/get_server/users/{id} - Retrieve a specific user

## Database

### Database Schema

**Users Table**
|   Column   |     Type      |         Constraints            |
|------------|---------------|--------------------------------|
|   user_id  |     INT       |   PRIMARY KEY, AUTO_INCREMENT  |
|   username |  VARCHAR(50)  |  UNIQUE, NOT NULL              |
| created_at |  TIMESTAMP    |   DEFAULT CURRENT_TIMESTAMP    |

**Posts Table**
|     Column   |     Type      |         Constraints                    |
|--------------|---------------|----------------------------------------|
|   post_id    |     INT       |   PRIMARY KEY, AUTO_INCREMENT          |
|   user_id    |     INT       | NOT NULL, FOREIGN KEY (users.user_id)  |
|   title      |  VARCHAR(255) |   NOT NULL                             |
|   content    |  TEXT         |       NOT NULL                         |
| like_count   |     INT       |   DEFAULT 0                            |
| dislike_count|    INT        |   DEFAULT 0                            |
| created_at   | TIMESTAMP     |      DEFAULT CURRENT_TIMESTAMP         |

### Database Connection Design

**Dual Connection Strategy**

The application implements a dual database connection strategy optimized for different types of HTTP operations:

**GET Requests:**

- **Connection Pooling**: For read operations, a connection pool is used to manage multiple concurrent connections efficiently. This allows the application to handle a high volume of read requests without overwhelming the database.
- **Rationale**: Read operations don't modify data and therefore don't risk race conditions between concurrent requests.

**POST Requests:**
- **Per-request Connection**: Each POST request creates a fresh database connection that is closed when the request completes.
- **Implementation**: Connections are established at the beginning of doPost() and closed in a finally block.
- **Rationale**: Write operations can modify shared data, so isolating connections provides better concurrency control

## How It Works

### Write Request

1. App receives a POST request
2. Load balancer forwards it to write server
3. Write server writes the data to MongoDB
4. Write server writes the message to Redis
5. Redis updates its cache

### Read Request

1. App receives a GET request
2. AWS Lambda validates client identity
3. Load balancer forwards it to read server
4. Read server fetches data from Redis (if exists)
5. If cache misses, read server fetches data from MongoDB
6. Read server writes the result to Redis (read-through caching)

## Why This Design

### Pros
- Fast reads via Redis
- Read-write separation

### Trade-offs
Eventual Consistency:
- Redis may temporarily serve stale data (delay in message queue publishing)
- Potential race condition: cache updated by read-through (GET) and consumer (POST)

## Conclusion

This system follows the AP (Availability + Partition Tolerance) model under the CAP Theorem. It prioritizes availability by continuing to serve read requests from Redis and accept write requests to the database. Redis is updated asynchronously through a message queue and consumer pattern, which means it may temporarily serve stale data, trading off strict consistency in favor of system responsiveness and resilience. 
This design is well-suited for read-heavy applications where eventual consistency is acceptable and high availability is critical.
