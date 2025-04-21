package org.yinuo.cs6650;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;

// GetServlet fetches posts from Redis cache/MongoDB
@WebServlet(name = "GetServlet", value = "/*")
public class GetServlet extends HttpServlet {
  private JedisCluster jedisCluster;
  private MongoClient mongoClient;
  private MongoDatabase database;
  private Gson gson;

  // Error messages
  private static final String EMPTY_URL = "Empty URL";
  private static final String WRONG_URL = "URL is not correct";
  private static final String SERVER_ERROR = "Internal server error";
  private static final String POST_NOT_FOUND = "Post not found";

  // MongoDB configuration
  private static final String MONGO_URI = "mongodb+srv://admin:admin@social-media.i5pvqwf.mongodb.net/?retryWrites=true&w=majority&appName=Social-Media";
  private static final String DB_NAME = "social_media";
  private static final String POSTS_COLLECTION = "posts";

  // Redis configuration
  private static final String REDIS_HOST = "postcache-fmkdi1.serverless.usw2.cache.amazonaws.com";
  private static final int REDIS_PORT_NUM = 6379;
  private static final int CACHE_EXPIRY = 3600;
  private static final int MAX_RETRIES = 5;

  @Override
  public void init() throws ServletException {
    super.init();
    // Initialize Elasticache Cluster connection
    JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
        .ssl(true)
        .build();
    jedisCluster = new JedisCluster(
        new HostAndPort(REDIS_HOST, REDIS_PORT_NUM),
        clientConfig,
        MAX_RETRIES,
        new ConnectionPoolConfig()
    );

    // Initialize MongoDB client
    mongoClient = MongoClients.create(MONGO_URI);
    database = mongoClient.getDatabase(DB_NAME);

    // Initialize Gson
    gson = new Gson();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
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
      } else if (pathParts.length == 4 && "likes".equals(pathParts[3])) {
        // URL format: [IP_ADDR]:[PORT]/[WAR_NAME]/posts/{post_id}/likes
        String postId = pathParts[2];
        getLikeDislikeCount(postId, response);
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
    if (jedisCluster != null) {
      jedisCluster.close();
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
        // Cache hit
        response.getWriter().write(cachedPost);
        return;
      }

      // Cache miss, fetch from MongoDB
      MongoCollection<Document> postsCollection = database.getCollection(POSTS_COLLECTION);
      Document post = postsCollection.find(Filters.eq("_id", postId)).first();

      if (post != null) {
        // Create a new document with only the fields we want
        Document filteredPost = new Document();
        filteredPost.put("postId", post.getString("_id"));
        filteredPost.put("title", post.getString("title"));
        filteredPost.put("content", post.getString("content"));

        String postJson = gson.toJson(filteredPost);

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

  // Retrieve like/dislike count for a post
  private void getLikeDislikeCount(String postId, HttpServletResponse response) throws IOException {
    try {
      response.setContentType("application/json");

      String cacheKey = "post:likes:" + postId;
      String cachedData = getFromCache(cacheKey);
      if (cachedData != null) {
        response.getWriter().write(cachedData);
        return;
      }

      // Cache miss, fetch from MongoDB
      MongoCollection<Document> postsCollection = database.getCollection(POSTS_COLLECTION);
      Document post = postsCollection.find(Filters.eq("_id", postId)).first();

      if (post != null) {
        Document likesInfo = new Document();
        likesInfo.put("postId", post.getString("_id"));

        // Get like count, default to 0 if not present
        Integer likeCount = post.getInteger("likeCount", 0);
        likesInfo.put("likeCount", likeCount);

        // Get dislike count, default to 0 if not present
        Integer dislikeCount = post.getInteger("dislikeCount", 0);
        likesInfo.put("dislikeCount", dislikeCount);

        String likesJson = gson.toJson(likesInfo);

        // Cache the result
        cacheData(cacheKey, likesJson);
        // Return the likes info
        response.getWriter().write(likesJson);
      } else {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, POST_NOT_FOUND);
      }
    } catch (Exception e) {
      getServletContext().log("Error fetching likes count for post: " + postId, e);
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SERVER_ERROR);
    }
  }

  // Get data from Redis cache
  private String getFromCache(String cacheKey) {
    try {
      return jedisCluster.get(cacheKey);
    } catch (Exception e) {
      getServletContext().log("Redis error when fetching key: " + cacheKey, e);
      return null;
    }
  }

  // Cache data in Redis cache
  private void cacheData(String cacheKey, String jsonData) {
    try {
      jedisCluster.setex(cacheKey, CACHE_EXPIRY, jsonData);
    } catch (Exception e) {
      getServletContext().log("Redis error when caching key: " + cacheKey, e);
    }
  }

  private void handleError(HttpServletResponse response, int statusCode, String message)
      throws IOException {
    response.setContentType("application/json");
    response.setStatus(statusCode);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}