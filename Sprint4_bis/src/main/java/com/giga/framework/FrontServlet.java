package com.giga.framework;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

public class FrontServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // Nothing auto-scanned here; controllers should register themselves (static block)
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handle(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();

        HandlerRegistry.Handler h = HandlerRegistry.get(uri);
        if (h == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No handler for " + uri);
            return;
        }

        try {
            Object result = h.method.invoke(h.instance, req, resp);

            if (result instanceof String) {
                resp.setContentType("text/plain; charset=UTF-8");
                PrintWriter out = resp.getWriter();
                out.print(result.toString());
                out.flush();
                return;
            }

            if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;
                // expose model attributes as request attributes
                mv.getModel().forEach(req::setAttribute);
                Dispatcher.forward(req, resp, mv.getView());
                return;
            }

            // default: print toString
            resp.setContentType("text/plain; charset=UTF-8");
            PrintWriter out = resp.getWriter();
            out.print(result == null ? "null" : result.toString());
            out.flush();

        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ServletException(e);
        }
    }
}
