package com.giga.spring.servlet;

import com.giga.spring.annotation.RequestParam;
import com.giga.spring.mapping.ControllerScanner;
import com.giga.spring.mapping.RouteRegistry;
import com.giga.spring.mapping.URLRoute;
import com.giga.spring.model.ModelView;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;
    RouteRegistry routeRegistry;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        routeRegistry = new RouteRegistry();

        String basePackage = config.getInitParameter("controller-package");
        if (basePackage == null || basePackage.isEmpty()) basePackage = "com.giga.spring.controller";

        List<URLRoute> routes = ControllerScanner.scanPackage(basePackage);
        routeRegistry.registerRoutes(routes);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            defaultServe(req, res);
        } else {
            URLRoute route = routeRegistry.findRoute(path);
            if (route != null) invokeController(route, path, req, res);
            else customServe(req, res);
        }
    }

    private void invokeController(URLRoute route, String path, HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            Map<String, String> urlParams = route.extractParams(path);
            urlParams.forEach(req::setAttribute);

            Method method = route.getMethod();
            Object controller = route.getController();

            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                Class<?> pType = parameters[i].getType();
                String name = parameters[i].getName();

                if (HttpServletRequest.class.isAssignableFrom(pType)) { args[i] = req; continue; }
                if (HttpServletResponse.class.isAssignableFrom(pType)) { args[i] = res; continue; }

                // RequestParam annotation override
                RequestParam rp = parameters[i].getAnnotation(RequestParam.class);
                String stringValue = null;
                if (rp != null && !rp.value().isEmpty()) {
                    stringValue = req.getParameter(rp.value());
                } else if (urlParams.containsKey(name)) {
                    stringValue = urlParams.get(name);
                } else {
                    stringValue = req.getParameter(name);
                }

                if (stringValue != null) args[i] = convertStringToType(stringValue, pType);
                else args[i] = pType.isPrimitive() ? getDefaultValueForPrimitive(pType) : null;
            }

            Object result = method.invoke(controller, args);

            if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;
                mv.getModel().forEach(req::setAttribute);
                RequestDispatcher rd = req.getRequestDispatcher(mv.getView());
                rd.forward(req, res);
                return;
            }

            // If result is String, write it as response body
            if (result instanceof String) {
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().write((String) result);
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) { out.println("Erreur interne: " + e.getMessage()); }
        }
    }

    private Object convertStringToType(String value, Class<?> targetType) {
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
        if (targetType == short.class || targetType == Short.class) return Short.parseShort(value);
        if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(value);
        if (targetType == char.class || targetType == Character.class) return value.length() > 0 ? value.charAt(0) : '\0';
        if (targetType.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
            return Enum.valueOf(enumType, value);
        }
        return value;
    }

    private Object getDefaultValueForPrimitive(Class<?> primitiveType) {
        if (primitiveType == boolean.class) return false;
        if (primitiveType == char.class) return '\0';
        if (primitiveType == byte.class) return (byte) 0;
        if (primitiveType == short.class) return (short) 0;
        if (primitiveType == int.class) return 0;
        if (primitiveType == long.class) return 0L;
        if (primitiveType == float.class) return 0f;
        if (primitiveType == double.class) return 0d;
        return null;
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        try (PrintWriter out = res.getWriter()) { out.println("404 - Not Found: " + req.getRequestURI()); }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException { defaultDispatcher.forward(req, res); }
}
