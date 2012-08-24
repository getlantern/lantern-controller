package org.lantern;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.lantern.data.Dao;

@SuppressWarnings("serial")
public class StatsController extends HttpServlet {
    
    @Override
    public void doGet(final HttpServletRequest request, 
        final HttpServletResponse response) throws IOException {
        
        final Dao dao = new Dao();
        //final JSONObject json = dao.getStats();
        final String finalData = dao.getStats();
        
        final String responseString;
        final String functionName = request.getParameter("callback");
        if (StringUtils.isBlank(functionName)) {
            responseString = finalData;
            response.setContentType("application/json");
        } else {
            responseString = functionName + "(" + finalData + ");";
            response.setContentType("text/javascript");
        }
        
        response.setStatus(HttpServletResponse.SC_OK);
        
        final byte[] content = responseString.getBytes("UTF-8");
        response.setContentLength(content.length);

        final OutputStream os = response.getOutputStream();

        System.out.println("Writing javascript callback.");
        os.write(content);
        os.flush();
    }
}