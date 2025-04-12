package org.yinuo.cs6650;

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

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
        String url = request.getPathInfo();
        
        if (url == null || url.isEmpty()) {
          handleInvalidRequest(response, EMPTY_URL);
          return;
        }

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
            handleInvalidRequest(response, INCOMPLETE_URL);
            break;
        }
  }

  // Create a new post
  private void createPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String title = request.getParameter("title");
    String content = request.getParameter("content");
    String userId = request.getParameter("user_id");

    // Validate required parameters
    if (title == null || title.isEmpty() || content == null || content.isEmpty() || userId == null || userId.isEmpty()) {
      handleInvalidRequest(response, MISSING_REQUIRED_PARAMETERS);
      return;
    }

    
  }

  // Create a new user
  private void createUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().write("User created");
  }

  // Handle invalid request
  private void handleInvalidRequest(HttpServletResponse response, String errorMessage) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.getWriter().write(errorMessage);
  }
}