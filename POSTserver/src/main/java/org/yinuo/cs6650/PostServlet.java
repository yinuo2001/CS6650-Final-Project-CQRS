package org.yinuo.cs6650;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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

  // Message constants
  private static final String CREATE_POST_SUCCESS = "Post created successfully";
  private static final String CREATE_USER_SUCCESS = "User created successfully";

  // Database connection constants
  private static final String DB_DRIVER = "com.mysql.cj.jdbc.Driver";
  //TODO: Change the database name to RDS db name
  private static final String DB_NAME = "your_database_name";
  //TODO: Change the database host to RDS db endpoint
  private static final String DB_HOST = "localhost";
  private static final String DB_PORT = "3306";
  private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
  private static final String DB_USER = "admin";
  private static final String DB_PASSWORD = "adminadmin";

  private java.sql.Connection connection;

  @Override
  public void init() throws ServletException {
    super.init();
    // Initialize JDBC MySQL database connection
    try {
      Class.forName(DB_DRIVER);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
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

      // Initialize the database connection
      connection = java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

      // Check where the request is routed to
      switch (url) {
        // URL: [IP_ADDR]:[PORT]/[WAR_NAME]/posts
        case "/posts":
          createPost(request, response);
          break;
        case "/users":
          // URL: [IP_ADDR]:[PORT]/[WAR_NAME]/users
          createUser(request, response);
          break;
        default:
          // Invalid request
          handleError(response, HttpServletResponse.SC_BAD_REQUEST, INCOMPLETE_URL);
          break;
      }
    } catch (SQLException e) {
      // Handle SQL exceptions
      String errorMessage = "Database error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    } catch (Exception e) {
      // Handle other exceptions
      String errorMessage = "Server error: " + e.getMessage();
      handleError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage);
    } finally {
      // Close the database connection
      try {
        if (connection != null && !connection.isClosed()) {
          connection.close();
        }
      } catch (SQLException e) {
        System.err.println("Failed to close connection: " + e.getMessage());
      }
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

    PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO posts (title, content, user_id) VALUES (?, ?, ?)");

    // Handle SQL exceptions e.g. user_id not found
    try (statement) {
      statement.setString(1, title);
      statement.setString(2, content);
      statement.setString(3, userId);
      statement.executeUpdate();
    }
    response.setStatus(HttpServletResponse.SC_CREATED);
    response.getWriter().write(CREATE_POST_SUCCESS);
  }

  // Create a new user
  private void createUser(HttpServletRequest request, HttpServletResponse response)
      throws IOException, SQLException {
    String username = request.getParameter("username");

    if (username == null || username.isEmpty()) {
      handleError(response, HttpServletResponse.SC_BAD_REQUEST, MISSING_REQUIRED_PARAMETERS);
      return;
    }

    PreparedStatement statement = connection.prepareStatement("INSERT INTO users (username) VALUES (?)");
    // Close the statement automatically at the end of the block
    try (statement) {
      statement.setString(1, username);
      statement.executeUpdate();
    }
    response.setStatus(HttpServletResponse.SC_CREATED);
    response.getWriter().write(CREATE_USER_SUCCESS);
  }

  // Handle different error cases
  private void handleError(HttpServletResponse response, int statusCode, String errorMessage) throws IOException {
    response.setStatus(statusCode);
    response.getWriter().write(errorMessage);
  }
}