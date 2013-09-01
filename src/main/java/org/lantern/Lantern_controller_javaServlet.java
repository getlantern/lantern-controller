package org.lantern;
import java.io.IOException;
import javax.servlet.http.*;

@SuppressWarnings("serial")
public class Lantern_controller_javaServlet extends HttpServlet {
    
    @Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/plain");
		resp.getWriter().println("Hello, world");
	}
}
