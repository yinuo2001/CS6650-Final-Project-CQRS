package org.yinuo.cs6650;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import org.bson.Document;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

// GetServlet fetches posts from Redis cache/MongoDB
@WebServlet(name = "GetServlet", value = "/*")
public class GetServlet extends HttpServlet {
  private JedisPool jedisPool;
  private MongoClient mongoClient;
  private Gson gson;

  // Error messages
  private static final String EMPTY_URL = "Empty URL";
  private static final String WRONG_URL = "URL is not correct";
  private static final String SERVER_ERROR = "Internal server error";
  private static final String POST_NOT_FOUND = "Post not found";
  private static final String USER_NOT_FOUND = "User not found";

  //TODO: Replace with mongoDB name
  private static final String DB_NAME = "your_database_name";

  //TODO: Replace with AWS Read Replica Endpoint
  private static final String REPLICA_ENDPOINT = "mongodb://localhost:27017";
  //TODO: Replace with AWS ElastiCache Endpoint / EC2 instance address holding Redis service
  private static final String REDIS_ADDRESS = "localhost";
  private static final String REDIS_PORT_NUM = "6379";

  private static final int CACHE_EXPIRY = 3600;

  @Override
  public void init() throws ServletException {
    super.init();
    // Initialize Redis connection pool
    JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
    jedisPoolConfig.setMaxTotal(100);
    jedisPoolConfig.setMaxIdle(20);
    jedisPoolConfig.setMinIdle(5);
    jedisPool = new JedisPool(jedisPoolConfig, REDIS_ADDRESS, Integer.parseInt(REDIS_PORT_NUM));

    // Initialize MongoDB client
    mongoClient = MongoClients.create(REPLICA_ENDPOINT);

    // Initialize Gson
    gson = new Gson();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String url = request.getPathInfo();
    if (url == null || url.isEmpty()) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, EMPTY_URL);
      return;
    }

    // Route requests based on URL path
    if (url.startsWith("/posts")) {
      String[] pathParts = url.split("/");
      if (pathParts.length == 3) {
        String postId = pathParts[2];
        // URL format: [IP_ADDR]:[PORT]/[WAR_NAME]/posts/{postId}
        getPostById(postId, response);
      } else if (pathParts.length == 4 && "user".equals(pathParts[2])) {
        // URL format: [IP_ADDR]:[PORT]/[WAR_NAME]/posts/user/{user_id}
        String userId = pathParts[3];
        getPostsByUserId(userId, response);
      } else {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, WRONG_URL);
      }
    } else if (url.startsWith("/users")) {
      String [] pathParts = url.split("/");
      if (pathParts.length == 3) {
        String userId = pathParts[2];
        // URL format: [IP_ADDR]:[PORT]/[WAR_NAME]/users/{user_id}
        getUserById(userId, response);
      } else {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, WRONG_URL);
      }
    } else {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, WRONG_URL);
    }
  }

  @Override
  public void destroy() {
    super.destroy();
    // Close Redis connection pool
    if (jedisPool != null) {
      jedisPool.close();
    }
    // Close MongoDB client
    if (mongoClient != null) {
      mongoClient.close();
    }
  }

  // Retrieve a post by its ID, first checking Redis cache and then MongoDB
  private void getPostById(String postId, HttpServletResponse response) throws IOException {
    try {
      response.setContentType("application/json");

      // Check Redis cache first
      String cacheKey = "post:" + postId;
      String cachedPost = getFromCache(cacheKey);
      if (cachedPost != null) {
        // Cache hit, yay!
        response.getWriter().write(cachedPost);
        return;
      }

      // Cache miss, fetch from MongoDB
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection("posts");
      Document query = new Document("_id", postId);
      Document post = findSingleDocument("posts", query);
      if (post != null) {
        String postJson = post.toJson();
        // Cache the result
        cacheData(cacheKey, postJson);
        // Return the post
        response.getWriter().write(postJson);
      } else {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, POST_NOT_FOUND);
      }
    } catch (Exception e) {
      getServletContext().log("Error fetching post with ID: " + postId, e);
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SERVER_ERROR);
    }
  }

  // Retrieve all posts by a user ID, first checking Redis cache and then MongoDB
  private void getPostsByUserId(String userId, HttpServletResponse response) throws IOException {
    try {
      response.setContentType("application/json");

      // Check cache first
      String cacheKey = "posts:user:" + userId;
      String cacheData = getFromCache(cacheKey);
      if (cacheData != null) {
        // Cache hit
        response.getWriter().write(cacheData);
        return;
      }

      // Cache miss, fetch from MongoDB and then cache the result
      Document query = new Document("user_id", userId);
      List<Document> posts = findMultipleDocuments("posts", query);
      // Convert to JSON
      String postsJson = convertDocumentsToJson(posts);
      // Cache the result
      cacheData(cacheKey, postsJson);
      // Return the posts
      response.getWriter().write(postsJson);
    } catch (Exception e) {
      getServletContext().log("Error fetching posts for user ID: " + userId, e);
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SERVER_ERROR);
    }
  }

  // Retrieve a user by its ID, first checking Redis cache and then MongoDB
  private void getUserById(String userId, HttpServletResponse response) throws IOException {
    try {
      response.setContentType("application/json");
      // Check Redis cache first
      String cacheKey = "user:" + userId;
      String cachedData = getFromCache(cacheKey);
      if (cachedData != null) {
        // Cache hit
        response.getWriter().write(cachedData);
        return;
      }

      // Cache miss, fetch from MongoDB
      Document query = new Document("_id", userId);
      Document user = findSingleDocument("users", query);
      if (user != null) {
        String userJson = user.toJson();
        // Cache the result
        cacheData(cacheKey, userJson);
        // Return the user
        response.getWriter().write(userJson);
      } else {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, USER_NOT_FOUND);
      }
    } catch (Exception e) {
      getServletContext().log("Error retrieving user: " + userId, e);
      try {
        handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SERVER_ERROR);
      } catch (IOException ioe) {
        getServletContext().log("Failed to send error response", ioe);
      }
    }
  }

  // Get data from Redis cache
  private String getFromCache (String cacheKey) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.get(cacheKey);
    } catch (Exception e) {
      getServletContext().log("Redis error when fetching key: " + cacheKey, e);
      return null;
    }
  }

  // Cache data in Redis cache
  private void cacheData (String cacheKey, String jsonData) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.setex(cacheKey, CACHE_EXPIRY, jsonData);
    } catch (Exception e) {
      getServletContext().log("Redis error when caching key: " + cacheKey, e);
    }
  }

  // Fetch a single document from MongoDB
  private Document findSingleDocument(String collectionName, Document query) {
    try {
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection(collectionName);
      return collection.find(query).first();
    } catch (Exception e) {
      getServletContext().log("Error fetching document from collection: " + collectionName, e);
      return null;
    }
  }

  // Fetch multiple documents from MongoDB
  private List<Document> findMultipleDocuments(String collectionName, Document query) {
    List<Document> documents = new ArrayList<>();
    try {
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection(collectionName);
      for (Document document : collection.find(query)) {
        documents.add(document);
      }
    } catch (Exception e) {
      getServletContext().log("Error fetching documents from collection: " + collectionName, e);
    }
    return documents;
  }

  // Convert a list of documents to JSON
  private String convertDocumentsToJson(List<Document> documents) {
    StringBuilder jsonBuilder = new StringBuilder("[");
    boolean first = true;
    for (Document document : documents) {
      if (!first) {
        jsonBuilder.append(",");
      }
      jsonBuilder.append(document.toJson());
      first = false;
    }
    jsonBuilder.append("]");
    return jsonBuilder.toString();
  }

  private void handleError(HttpServletResponse response, int statusCode, String message)
      throws IOException {
    response.setContentType("application/json");
    response.getWriter().write("{\"error\": \"" + message + "\"}");
  }
}