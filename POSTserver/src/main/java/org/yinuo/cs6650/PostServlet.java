package org.yinuo.cs6650;


import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

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

  // MongoDB configuration
  private static final String MONGO_URI = "mongodb+srv://admin:admin@social-media.i5pvqwf.mongodb.net/?retryWrites=true&w=majority&appName=Social-Media";
  private static final String DB_NAME = "social_media";
  private static final String USERS_COLLECTION = "users";
  private static final String POSTS_COLLECTION = "posts";

  private MongoClient mongoClient;
  private MongoDatabase database;

  @Override
  public void init() throws ServletException {
    super.init();
    // Initialize MongoDB client
    mongoClient = MongoClients.create(MONGO_URI);
    database = mongoClient.getDatabase(DB_NAME);
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

  // Create a new post
  private void createPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
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

      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write("{\"message\":\"" + CREATE_POST_SUCCESS + "\",\"postId\":\"" + postId + "\"}");
    } catch (Exception e) {
      // Handle database exceptions
      String errorMessage = "MongoDB error: " + e.getMessage();
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

      // Get users collection
      MongoCollection<Document> usersCollection = database.getCollection(USERS_COLLECTION);

      // Create document for MongoDB
      Document userDoc = new Document()
          .append("_id", userId)
          .append("username", username)
          .append("createdAt", java.time.Instant.now().toString());

      // Insert document into MongoDB
      usersCollection.insertOne(userDoc);

      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write("{\"message\":\"" + CREATE_USER_SUCCESS + "\",\"userId\":\"" + userId + "\"}");
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
        String successMessage = action.equals("like") ? LIKE_SUCCESS : DISLIKE_SUCCESS;
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"message\":\"" + successMessage + "\"}");
      } else {
        handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update post");
      }
    } catch (Exception e) {
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

  @Override
  public void destroy() {
    // Close the mongoDB client
    if (mongoClient != null) {
      mongoClient.close();
    }
    super.destroy();
  }
}