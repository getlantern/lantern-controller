package org.lantern;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.lantern.loggly.LoggerFactory;

public class ExceptionFilter implements Filter {

    private static final transient Logger log = LoggerFactory
            .getLogger(ExceptionFilter.class);

    @Override
    public void doFilter(ServletRequest rq, ServletResponse rs,
            FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(rq, rs); // this calls the servlet which is where
                                    // your exceptions will bubble up from
        } catch (Throwable t) {
            // deal with exception, then do redirect to custom jsp page
            log.log(Level.SEVERE,
                    "Uncaught exception: " + t.getMessage(),
                    t);
            throw new ServletException(t);
        }
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
    }

    @Override
    public void destroy() {
        // do nothing
    }

}