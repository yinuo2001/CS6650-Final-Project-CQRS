package org.yinuo.cs6650;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.regions.Region;

import com.google.gson.Gson;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

// GetServlet fetches posts from Redis cache/DynamoDB
@WebServlet(name = "GetServlet", value = "/*")
public class GetServlet extends HttpServlet {
  private JedisPool jedisPool;
  private DynamoDbClient dynamoDbClient;
  private Gson gson;

  // Error messages
  private static final String EMPTY_URL = "Empty URL";
  private static final String WRONG_URL = "URL is not correct";
  private static final String SERVER_ERROR = "Internal server error";
  private static final String POST_NOT_FOUND = "Post not found";

  // Table name
  private static final String POSTS_TABLE = "Posts";
  //TODO: Replace with actual region of Read Replica
  private static final Region REGION = Region.US_WEST_2;
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

    // Initialize DynamoDB client
    dynamoDbClient = DynamoDbClient.builder().region(REGION).build();

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
    if (jedisPool != null) {
      jedisPool.close();
    }
    // Close MongoDB client
    if (dynamoDbClient != null) {
      dynamoDbClient.close();
    }
  }

  // Retrieve a post by its ID, first checking Redis cache and then DynamoDB
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

      // Cache miss, fetch from DynamoDB
      Map<String, AttributeValue> key = new HashMap<>();
      key.put("postId", AttributeValue.builder().s(postId).build());

      GetItemRequest getItemRequest = GetItemRequest.builder()
          .tableName(POSTS_TABLE)
          .key(key)
          .attributesToGet("postId", "title", "content") // Only get title and content (plus ID)
          .consistentRead(false)
          .build();
      GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
      if (getItemResponse.hasItem()) {
        // Convert DynamoDB item to JSON, but only include specific fields
        Map<String, Object> postMap = new HashMap<>();
        Map<String, AttributeValue> item = getItemResponse.item();

        // Include only postId, title, and content
        postMap.put("postId", item.get("postId").s());
        postMap.put("title", item.get("title").s());
        postMap.put("content", item.get("content").s());

        String postJson = gson.toJson(postMap);

        // Cache the result
        cacheData(cacheKey, postJson);
        // Return the post
        response.getWriter().write(postJson);
      } else {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, POST_NOT_FOUND);
      }
    } catch (DynamoDbException e) {
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

      // Cache miss, fetch from DynamoDB
      Map<String, AttributeValue> key = new HashMap<>();
      key.put("postId", AttributeValue.builder().s(postId).build());
      GetItemRequest getItemRequest = GetItemRequest.builder()
          .tableName(POSTS_TABLE)
          .key(key)
          .consistentRead(false)
          .attributesToGet("postId", "likeCount", "dislikeCount")
          .build();
      GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);

      if (getItemResponse.hasItem()) {
        Map<String, AttributeValue> item = getItemResponse.item();
        Map<String, Object> likeDislikeMap = new HashMap<>();
        likeDislikeMap.put("postId", item.get("postId").s());

        int likeCount = 0;
        if (item.containsKey("likeCount") && item.get("likeCount").n() != null) {
          likeCount = Integer.parseInt(item.get("likeCount").n());
        }
        likeDislikeMap.put("likeCount", likeCount);

        int dislikeCount = 0;
        if (item.containsKey("dislikeCount") && item.get("dislikeCount").n() != null) {
          dislikeCount = Integer.parseInt(item.get("dislikeCount").n());
        }
        likeDislikeMap.put("dislikeCount", dislikeCount);

        // Convert to JSON
        String countsJson = gson.toJson(likeDislikeMap);
        // Cache the result
        cacheData(cacheKey, countsJson);

        response.getWriter().write(countsJson);
      } else {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, POST_NOT_FOUND);
      }
    } catch (DynamoDbException e) {
      getServletContext().log("Error fetching likes count for post: " + postId, e);
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SERVER_ERROR);
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

  private void handleError(HttpServletResponse response, int statusCode, String message)
      throws IOException {
    response.setContentType("application/json");
    response.setStatus(statusCode);
    response.getWriter().write("{\"error\": \"" + message + "\"}");
  }
}