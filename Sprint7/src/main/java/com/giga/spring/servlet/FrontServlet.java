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
        if (basePackage == null || basePackage.isEmpty()) {
            basePackage = "com.giga.spring.controller";
        }

        List<URLRoute> routes = ControllerScanner.scanPackage(basePackage);
        routeRegistry.registerRoutes(routes);
        
        // Debug: afficher les routes chargées
        System.out.println("=== Routes chargées ===");
        for (URLRoute route : routeRegistry.getAllRoutes()) {
            System.out.println(route.getUrlPattern() + " [" + route.getHttpMethod() + "] -> " + 
                             route.getMethod().getName());
        }
        System.out.println("======================");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod(); // GET, POST, etc.
        
        System.out.println("Requête reçue: " + httpMethod + " " + path);
        
        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            defaultServe(req, res);
        } else {
            URLRoute route = routeRegistry.findRoute(path, httpMethod);
            if (route != null) {
                System.out.println("Route trouvée: " + route.getUrlPattern() + 
                                 " [" + route.getHttpMethod() + "]");
                invokeController(route, path, req, res);
            } else {
                // Vérifier si l'URL existe avec une autre méthode HTTP
                boolean urlExists = false;
                String availableMethods = "";
                for (URLRoute r : routeRegistry.getAllRoutes()) {
                    if (r.matches(path)) {
                        urlExists = true;
                        if (!availableMethods.contains(r.getHttpMethod())) {
                            availableMethods += (availableMethods.isEmpty() ? "" : ", ") + r.getHttpMethod();
                        }
                    }
                }
                
                if (urlExists) {
                    // URL existe mais méthode HTTP incorrecte → 405 Method Not Allowed
                    System.out.println("405 - URL existe mais méthode " + httpMethod + " non supportée");
                    res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    res.setHeader("Allow", availableMethods);
                    try (PrintWriter out = res.getWriter()) {
                        out.println("<h1>405 - Method Not Allowed</h1>");
                        out.println("<p>URL: " + req.getRequestURI() + "</p>");
                        out.println("<p>Méthode utilisée: " + httpMethod + "</p>");
                        out.println("<p>Méthodes autorisées: " + availableMethods + "</p>");
                    }
                } else {
                    customServe(req, res);
                }
            }
        }
    }

    private void invokeController(URLRoute route, String path, HttpServletRequest req, 
                                 HttpServletResponse res) throws IOException {
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

                if (HttpServletRequest.class.isAssignableFrom(pType)) { 
                    args[i] = req; 
                    continue; 
                }
                if (HttpServletResponse.class.isAssignableFrom(pType)) { 
                    args[i] = res; 
                    continue; 
                }

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

                if (stringValue != null) {
                    args[i] = convertStringToType(stringValue, pType);
                } else {
                    args[i] = pType.isPrimitive() ? getDefaultValueForPrimitive(pType) : null;
                }
            }

            Object result = method.invoke(controller, args);

            if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;
                mv.getModel().forEach(req::setAttribute);
                RequestDispatcher rd = req.getRequestDispatcher(mv.getView());
                rd.forward(req, res);
                return;
            }

            // Si le résultat est une String, l'écrire dans la réponse
            if (result instanceof String) {
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().write((String) result);
            } else if (result != null) {
                // Autres types d'objets
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().write(result.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                out.println("<h1>Erreur Interne</h1>");
                out.println("<p>" + e.getMessage() + "</p>");
                out.println("<pre>");
                e.printStackTrace(out);
                out.println("</pre>");
            }
        }
    }

    private Object convertStringToType(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return getDefaultValueForPrimitive(targetType);
        }
        
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return getDefaultValueForPrimitive(targetType);
            }
        }
        if (targetType == long.class || targetType == Long.class) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return getDefaultValueForPrimitive(targetType);
            }
        }
        if (targetType == double.class || targetType == Double.class) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return getDefaultValueForPrimitive(targetType);
            }
        }
        if (targetType == float.class || targetType == Float.class) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException e) {
                return getDefaultValueForPrimitive(targetType);
            }
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (targetType == short.class || targetType == Short.class) {
            try {
                return Short.parseShort(value);
            } catch (NumberFormatException e) {
                return getDefaultValueForPrimitive(targetType);
            }
        }
        if (targetType == byte.class || targetType == Byte.class) {
            try {
                return Byte.parseByte(value);
            } catch (NumberFormatException e) {
                return getDefaultValueForPrimitive(targetType);
            }
        }
        if (targetType == char.class || targetType == Character.class) {
            return value.length() > 0 ? value.charAt(0) : '\0';
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
            try {
                return Enum.valueOf(enumType, value);
            } catch (IllegalArgumentException e) {
                return enumType.getEnumConstants()[0];
            }
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
        System.out.println("404 - URL non trouvée: " + req.getRequestURI());
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<h1>404 - Not Found</h1>");
            out.println("<p>URL: " + req.getRequestURI() + "</p>");
            out.println("<p>Désolé, la page que vous cherchez n'existe pas.</p>");
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}