package org.yinuo.cs6650;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

// PostServlet handles post creation
@WebServlet(name = "PostServlet", value = "/*")
public class PostServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
        String url = request.getPathInfo();
        
        if (url == null || url.isEmpty()) {
          handleInvalidRequest(response);
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
            handleInvalidRequest(response);
            break;
        }
  }

  // Create a new post
  private void createPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().write("Post created");
  }

  // Create a new user
  private void createUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().write("User created");
  }

  // Handle invalid request
  private void handleInvalidRequest(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.getWriter().write("Invalid request");
  }
}