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
        
        // Check if the request is valid
        if (url == null || url.isEmpty()) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().write("Missing parameters");
          return;
        }

        if (url.equals("/posts")) {
          // Create a new post
          createPost(request, response);
        } else if (url.equals("/users")) {
          // Create a new user
          createUser(request, response);
        } else {
          // Invalid request
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().write("Invalid request");
          return;
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
}