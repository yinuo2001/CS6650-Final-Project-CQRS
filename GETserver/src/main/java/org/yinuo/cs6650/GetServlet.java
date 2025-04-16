package org.yinuo.cs6650;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
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

  //TODO: Replace with mongoDB name
  private static final String DB_NAME = "your_database_name";

  //TODO: Replace with AWS Read Replica Endpoint
  private static final String REPLICA_ENDPOINT = "mongodb://localhost:27017";
  //TODO: Replace with AWS ElastiCache Endpoint / EC2 instance address holding Redis service
  private static final String REDIS_ENDPOINT = "redis://localhost:6379";
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
    jedisPool = new JedisPool(jedisPoolConfig, REDIS_ENDPOINT, Integer.parseInt(REDIS_PORT_NUM));

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
        // URL format: /posts/{postId}
        getPostById(postId, response);
      } else if (pathParts.length == 2) {
        // URL format: /posts
        getAllPosts(response);
      } else if (pathParts.length == 4 && "user".equals(pathParts[2])) {
        // URL format: /posts/user/{userId}
        String userId = pathParts[3];
        getPostsByUserId(userId, response);
      } else {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, WRONG_URL);
      }
    } else if (url.startsWith("/users")) {
      String [] pathParts = url.split("/");
      if (pathParts.length == 3) {
        String userId = pathParts[2];
        // URL format: /users/{userId}
        getUserById(userId, response);
      } else if (pathParts.length == 2) {
        // URL format: /users
        getAllUsers(response);
      } else {
        handleError(response, HttpServletResponse.SC_BAD_REQUEST, WRONG_URL);
      }
    } else {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, WRONG_URL);
    }
  }

  private void getPostById(String postId, HttpServletResponse response) {

  }

  private void getAllPosts(HttpServletResponse response) {

  }

  private void getPostsByUserId(String userId, HttpServletResponse response) {

  }

  private void getUserById(String userId, HttpServletResponse response) {

  }

  private void getAllUsers(HttpServletResponse response) {

  }

  private void handleError(HttpServletResponse response, int statusCode, String message)
      throws IOException {
    response.setStatus(statusCode);
    response.getWriter().println("<h1>" + message + "</h1>");
  }
}