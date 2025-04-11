package org.yinuo.cs6650;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

// GetServlet fetches posts from Redis cache/MongoDB
@WebServlet(name = "GetServlet", value = "/*")
public class GetServlet extends HttpServlet {
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html");
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().println("<h1>gets</h1>");
  }
}