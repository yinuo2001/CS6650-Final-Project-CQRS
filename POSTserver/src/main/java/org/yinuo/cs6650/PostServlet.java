package org.yinuo.cs6650;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.UUID;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;

// PostServlet handles post creation using JSON request bodies
@WebServlet(name = "PostServlet", value = "/*")
public class PostServlet extends HttpServlet {

  // Error constants
  private static final String MISSING_REQUIRED_PARAMETERS = "Missing required parameters";
  private static final String EMPTY_URL = "Empty URL";
  private static final String INCOMPLETE_URL = "Incomplete URL";
  private static final String INVALID_ACTION = "Invalid action (must be 'like' or 'dislike')";
  private static final String POST_NOT_FOUND = "Post not found";
  private static final String INVALID_JSON = "Invalid JSON format in request body";

  // Message constants
  private static final String CREATE_POST_SUCCESS = "Post created successfully";
  private static final String CREATE_USER_SUCCESS = "User created successfully";
  private static final String LIKE_SUCCESS = "Post liked successfully";
  private static final String DISLIKE_SUCCESS = "Post disliked successfully";

  // ElastiCache configuration, we will be partially using write-through caching
  private static final String REDIS_HOST = "postcache-fmkdi1.serverless.usw2.cache.amazonaws.com";
  private static final int REDIS_PORT_NUM = 6379;
  private static final int CACHE_EXPIRY = 3600;
  private static final int MAX_RETRIES = 3;
  private JedisCluster jedisCluster;

  // MongoDB configuration
  private static final String MONGO_URI = "mongodb+srv://admin:admin@social-media.i5pvqwf.mongodb.net/?retryWrites=true&w=majority&appName=Social-Media";
  private static final String DB_NAME = "social_media";
  private static final String USERS_COLLECTION = "users";
  private static final String POSTS_COLLECTION = "posts";

  private MongoClient mongoClient;
  private MongoDatabase database;
  private Gson gson;

  @Override
  public void init() throws ServletException {
    super.init();
    // Initialize MongoDB client
    mongoClient = MongoClients.create(MONGO_URI);
    database = mongoClient.getDatabase(DB_NAME);

    // Initialize Gson for JSON processing
    gson = new Gson();

    JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
        .ssl(true)
        .build();
    jedisCluster = new JedisCluster(
        new HostAndPort(REDIS_HOST, REDIS_PORT_NUM),
        clientConfig,
        MAX_RETRIES,
        new ConnectionPoolConfig()
    );
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      String url = request.getPathInfo();

      if (url == null || url.isEmpty()) {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, EMPTY_URL);
        return;
      }

      // Check where the request is routed to
      if (url.equals("/posts")) {
        // URL: [IP_ADDR]:[PORT]/[WAR_NAME]/posts
        createPost(request, response);
      } else if (url.equals("/users")) {
        // URL: [IP_ADDR]:[PORT]/[WAR_NAME]/users
        createUser(request, response);
      } else if (url.startsWith("/posts/")) {
        String[] pathParts = url.split("/");
        if (pathParts.length == 4) {
          String postId = pathParts[2];
          String action = pathParts[3];

          if (action.equals("like") || action.equals("dislike")) {
            // URL: [IP_ADDR]:[PORT]/[WAR_NAME]/posts/{postId}/like
            // Or: [IP_ADDR]:[PORT]/[WAR_NAME]/posts/{postId}/dislike
            reactToPost(postId, action, response);
          } else {
            handleError(response, HttpServletResponse.SC_BAD_REQUEST, INCOMPLETE_URL);
          }
        } else {
          handleError(response, HttpServletResponse.SC_BAD_REQUEST, INCOMPLETE_URL);
        }
      } else {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, INCOMPLETE_URL);
      }
    } catch (Exception e) {
      // Handle other exceptions
      String errorMessage = "Server error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    }
  }

  // Read the JSON body from the request
  private JsonObject readJsonBody(HttpServletRequest request) throws IOException {
    StringBuilder buffer = new StringBuilder();
    BufferedReader reader = request.getReader();
    String line;
    while ((line = reader.readLine()) != null) {
      buffer.append(line);
    }

    String requestBody = buffer.toString();
    if (requestBody == null || requestBody.isEmpty()) {
      return null;
    }

    try {
      return gson.fromJson(requestBody, JsonObject.class);
    } catch (JsonSyntaxException e) {
      throw new IOException("Invalid JSON format: " + e.getMessage());
    }
  }

  // Create a new post using JSON body
  private void createPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      JsonObject json = readJsonBody(request);

      if (json == null) {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_JSON);
        return;
      }

      // Extract fields from JSON
      String title = json.has("title") ? json.get("title").getAsString() : null;
      String content = json.has("content") ? json.get("content").getAsString() : null;
      String userId = json.has("userId") ? json.get("userId").getAsString() : null;

      // Validate required parameters
      if (title == null || title.isEmpty() || content == null || content.isEmpty()
          || userId == null || userId.isEmpty()) {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, MISSING_REQUIRED_PARAMETERS);
        return;
      }

      // Generate a unique UUID for the post ID
      String postId = UUID.randomUUID().toString();

      // Get posts collection
      MongoCollection<Document> postsCollection = database.getCollection(POSTS_COLLECTION);

      // Create document for MongoDB
      Document postDoc = new Document()
          .append("_id", postId)
          .append("userId", userId)
          .append("title", title)
          .append("content", content)
          .append("likeCount", 0)
          .append("dislikeCount", 0)
          .append("createdAt", java.time.Instant.now().toString());

      // Insert document into MongoDB
      postsCollection.insertOne(postDoc);

      // Create response JSON
      JsonObject responseJson = new JsonObject();
      responseJson.addProperty("message", CREATE_POST_SUCCESS);
      responseJson.addProperty("postId", postId);

      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write(gson.toJson(responseJson));
    } catch (JsonSyntaxException e) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_JSON);
    } catch (Exception e) {
      // Handle database exceptions
      String errorMessage = "MongoDB error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    }
  }

  // Create a new user using JSON body
  private void createUser(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      JsonObject json = readJsonBody(request);

      if (json == null) {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_JSON);
        return;
      }

      // Extract username from JSON
      String username = json.has("username") ? json.get("username").getAsString() : null;

      if (username == null || username.isEmpty()) {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, MISSING_REQUIRED_PARAMETERS);
        return;
      }

      String userId = UUID.randomUUID().toString();

      // Get users collection
      MongoCollection<Document> usersCollection = database.getCollection(USERS_COLLECTION);

      // Create document for MongoDB
      Document userDoc = new Document()
          .append("_id", userId)
          .append("username", username)
          .append("createdAt", java.time.Instant.now().toString());

      // Insert document into MongoDB
      usersCollection.insertOne(userDoc);

      // Create response JSON
      JsonObject responseJson = new JsonObject();
      responseJson.addProperty("message", CREATE_USER_SUCCESS);
      responseJson.addProperty("userId", userId);

      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write(gson.toJson(responseJson));
    } catch (JsonSyntaxException e) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_JSON);
    } catch (Exception e) {
      getServletContext().log("Error creating user: " + e.getMessage());
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create user: " + e.getMessage());
    }
  }

  private void reactToPost(String postId, String action, HttpServletResponse response) throws IOException {
    if (!action.equals("like") && !action.equals("dislike")) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_ACTION);
      return;
    }

    try {
      MongoCollection<Document> postsCollection = database.getCollection(POSTS_COLLECTION);
      Document existingPost = postsCollection.find(Filters.eq("_id", postId)).first();

      if (existingPost == null) {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, POST_NOT_FOUND);
        return;
      }

      // Update like or dislike count
      String updateField = action.equals("like") ? "likeCount" : "dislikeCount";
      Bson filter = Filters.eq("_id", postId);
      Bson update = Updates.inc(updateField, 1);

      UpdateResult result = postsCollection.updateOne(filter, update);

      if (result.getModifiedCount() > 0) {

        Document updatedPost = postsCollection.find(Filters.eq("_id", postId)).first();

        if (updatedPost != null) {
          // Update the likes cache
          updateLikesCache(postId, updatedPost);
        }

        String successMessage = action.equals("like") ? LIKE_SUCCESS : DISLIKE_SUCCESS;

        // Create response JSON
        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("message", successMessage);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(gson.toJson(responseJson));
      } else {
        handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update post");
      }
    } catch (Exception e) {
      getServletContext().log("Error processing reaction to post with ID: " + postId, e);
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to process reaction: " + e.getMessage());
    }
  }

  // Handle different error cases
  private void handleError(HttpServletResponse response, int statusCode, String errorMessage) throws IOException {
    JsonObject errorJson = new JsonObject();
    errorJson.addProperty("error", errorMessage);

    response.setStatus(statusCode);
    response.setContentType("application/json");
    response.getWriter().write(gson.toJson(errorJson));
  }

  // Update the likes cache in Redis
  // Write-through caching: when a post is liked or disliked, update the cache
  private void updateLikesCache(String postId, Document post) {
    try {
      Document likesInfo = new Document();
      likesInfo.put("postId", post.getString("_id"));
      likesInfo.put("likeCount", post.getInteger("likeCount", 0));
      likesInfo.put("dislikeCount", post.getInteger("dislikeCount", 0));

      String cacheKey = "post:likes:" + postId;
      String likesJson = gson.toJson(likesInfo);

      // Update the cache with new values
      jedisCluster.setex(cacheKey, CACHE_EXPIRY, likesJson);
    } catch (Exception e) {
      getServletContext().log("Error updating likes cache for post: " + postId, e);
    }
  }

  @Override
  public void destroy() {
    // Close the MongoDB client
    if (mongoClient != null) {
      mongoClient.close();
    }

    // Close the Redis connection pool
    if (jedisCluster != null) {
      jedisCluster.close();
    }
    super.destroy();
  }
}