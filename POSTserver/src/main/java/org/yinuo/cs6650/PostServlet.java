package org.yinuo.cs6650;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.regions.Region;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

// PostServlet handles post creation
@WebServlet(name = "PostServlet", value = "/*")
public class PostServlet extends HttpServlet {

  // Error constants
  private static final String MISSING_REQUIRED_PARAMETERS = "Missing required parameters";
  private static final String EMPTY_URL = "Empty URL";
  private static final String INCOMPLETE_URL = "Incomplete URL";
  private static final String INVALID_ACTION = "Invalid action (must be 'like' or 'dislike')";
  private static final String POST_NOT_FOUND = "Post not found";

  // Message constants
  private static final String CREATE_POST_SUCCESS = "Post created successfully";
  private static final String CREATE_USER_SUCCESS = "User created successfully";
  private static final String LIKE_SUCCESS = "Post liked successfully";
  private static final String DISLIKE_SUCCESS = "Post disliked successfully";

  // DynamoDB table names
  private static final String USERS_TABLE = "Users";
  private static final String POSTS_TABLE = "Posts";

  // DynamoDB region
  private static final Region REGION = Region.US_WEST_2;

  private DynamoDbClient dynamoDbClient;

  @Override
  public void init() throws ServletException {
    super.init();
    // Initialize JDBC MySQL database connection
    dynamoDbClient = DynamoDbClient.builder().region(REGION).build();
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
    } catch (SQLException e) {
      // Handle SQL exceptions
      String errorMessage = "Database error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    } catch (Exception e) {
      // Handle other exceptions
      String errorMessage = "Server error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    }
  }

  // Create a new post
  private void createPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, SQLException {
    String title = request.getParameter("title");
    String content = request.getParameter("content");
    String userId = request.getParameter("user_id");

    // Validate required parameters
    if (title == null || title.isEmpty() || content == null || content.isEmpty() || userId == null || userId.isEmpty()) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, MISSING_REQUIRED_PARAMETERS);
      return;
    }

    try {
      // Generate a unique UUID for the post ID
      String postId = UUID.randomUUID().toString();

      // Create item for DynamoDB
      Map<String, AttributeValue> item = new HashMap<>();
      item.put("postId", AttributeValue.builder().s(postId).build());
      item.put("userId", AttributeValue.builder().s(userId).build());
      item.put("title", AttributeValue.builder().s(title).build());
      item.put("content", AttributeValue.builder().s(content).build());
      item.put("likeCount", AttributeValue.builder().n("0").build());
      item.put("dislikeCount", AttributeValue.builder().n("0").build());
      item.put("createdAt", AttributeValue.builder().s(java.time.Instant.now().toString()).build());

      PutItemRequest putItemRequest = PutItemRequest.builder()
          .tableName(POSTS_TABLE)
          .item(item)
          .build();

      // Put item to DynamoDB
      dynamoDbClient.putItem(putItemRequest);

      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write("{\"message\":\"" + CREATE_POST_SUCCESS + "\",\"postId\":\"" + postId + "\"}");
    } catch (DynamoDbException e) {
      // Handle DynamoDB exceptions
      String errorMessage = "DynamoDB error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    }
  }

  // Create a new user
  private void createUser(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String username = request.getParameter("username");

    if (username == null || username.isEmpty()) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, MISSING_REQUIRED_PARAMETERS);
      return;
    }

    try {
      String userId = UUID.randomUUID().toString();
      // Create item for DynamoDB
      Map<String, AttributeValue> item = new HashMap<>();
      item.put("userId", AttributeValue.builder().s(userId).build());
      item.put("username", AttributeValue.builder().s(username).build());
      item.put("createdAt", AttributeValue.builder().s(java.time.Instant.now().toString()).build());

      // Create request
      PutItemRequest putItemRequest = PutItemRequest.builder()
          .tableName(USERS_TABLE)
          .item(item)
          .build();

      // Put item to DynamoDB
      dynamoDbClient.putItem(putItemRequest);

      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write("{\"message\":\"" + CREATE_USER_SUCCESS + "\",\"userId\":\"" + userId + "\"}");
    } catch (DynamoDbException e) {
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
      Map<String, AttributeValue> key = new HashMap<>();
      key.put("postId", AttributeValue.builder().s(postId).build());
      GetItemRequest getItemRequest = GetItemRequest.builder()
          .tableName(POSTS_TABLE)
          .key(key)
          .attributesToGet("postId")
          .build();

      GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);

      if (!getItemResponse.hasItem()) {
        handleError(response, HttpServletResponse.SC_NOT_FOUND, POST_NOT_FOUND);
        return;
      }
      // Update the like or dislike count using atomic counter
      Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
      expressionAttributeValues.put(":inc", AttributeValue.builder().n("1").build());

      Map<String, String> expressionAttributeNames = new HashMap<>();
      String updateAttribute = action.equals("like") ? "likeCount" : "dislikeCount";
      expressionAttributeNames.put("#count", updateAttribute);

      UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
          .tableName(POSTS_TABLE)
          .key(key)
          .updateExpression("ADD #count :inc")
          .expressionAttributeNames(expressionAttributeNames)
          .expressionAttributeValues(expressionAttributeValues)
          .build();

      dynamoDbClient.updateItem(updateItemRequest);

      String successMessage = action.equals("like") ? LIKE_SUCCESS : DISLIKE_SUCCESS;
      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().write("{\"message\":\"" + successMessage + "\"}");
    } catch (DynamoDbException e) {
      getServletContext().log("Error fetching post with ID: " + postId, e);
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch post: " + e.getMessage());
    }
  }

  // Handle different error cases
  private void handleError(HttpServletResponse response, int statusCode, String errorMessage) throws IOException {
    response.setStatus(statusCode);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"" + errorMessage + "\"}");
  }
}