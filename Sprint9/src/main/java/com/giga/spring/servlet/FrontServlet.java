package com.giga.spring.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.giga.spring.annotation.Json;
import com.giga.spring.annotation.RequestParam;
import com.giga.spring.binding.ObjectBinder;
import com.giga.spring.json.JsonConverter;
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

        System.out.println("=== Routes chargées (Sprint 9) ===");
        for (URLRoute route : routeRegistry.getAllRoutes()) {
            Method method = route.getMethod();
            boolean hasJson = method.isAnnotationPresent(Json.class);
            System.out.println(route.getUrlPattern() + " [" + route.getHttpMethod() + "] -> " + 
                             route.getMethod().getName() + (hasJson ? " [JSON]" : ""));
        }
        System.out.println("==================================");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod();

        System.out.println("Requête API: " + httpMethod + " " + path);

        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            defaultServe(req, res);
            return;
        }

        URLRoute route = routeRegistry.findRoute(path, httpMethod);
        if (route != null) {
            invokeController(route, path, req, res);
        } else {
            // Vérifier si l'URL existe avec une autre méthode HTTP
            boolean urlExists = false;
            StringBuilder availableMethods = new StringBuilder();
            
            for (URLRoute r : routeRegistry.getAllRoutes()) {
                if (r.matches(path)) {
                    urlExists = true;
                    String method = r.getHttpMethod();
                    if (!availableMethods.toString().contains(method)) {
                        if (availableMethods.length() > 0) {
                            availableMethods.append(", ");
                        }
                        availableMethods.append(method);
                    }
                }
            }
            
            if (urlExists) {
                // Retourner 405 en JSON si c'est une API
                if (isLikelyApiRequest(req)) {
                    res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.setHeader("Allow", availableMethods.toString());
                    try (PrintWriter out = res.getWriter()) {
                        String jsonError = "{\"status\":\"error\",\"code\":405,\"message\":\"Method Not Allowed\",\"allowedMethods\":\"" + 
                                          availableMethods + "\"}";
                        out.write(jsonError);
                    }
                } else {
                    res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    res.setHeader("Allow", availableMethods.toString());
                    res.setContentType("text/html;charset=UTF-8");
                    try (PrintWriter out = res.getWriter()) {
                        out.println("<h1>405 - Méthode Non Autorisée</h1>");
                        out.println("<p>Méthodes autorisées: " + availableMethods + "</p>");
                    }
                }
            } else {
                customServe(req, res);
            }
        }
    }

    private void invokeController(URLRoute route, String path, HttpServletRequest req, 
                                 HttpServletResponse res) throws IOException {
        try {
            // Extraire les paramètres d'URL
            Map<String, String> urlParams = route.extractParams(path);
            urlParams.forEach(req::setAttribute);

            Method method = route.getMethod();
            Object controller = route.getController();
            
            // Vérifier si c'est une méthode JSON (Sprint 9)
            boolean isJsonMethod = method.isAnnotationPresent(Json.class);
            
            // Préparer les arguments
            Object[] args = prepareMethodArguments(method, req, res, urlParams);
            
            // Appeler la méthode du contrôleur
            System.out.println("Invoking " + method.getName() + (isJsonMethod ? " [JSON]" : ""));
            Object result = method.invoke(controller, args);
            
            // Traiter le résultat selon le type de méthode
            if (isJsonMethod) {
                processJsonResult(result, req, res, method.getAnnotation(Json.class));
            } else {
                processRegularResult(result, req, res);
            }

        } catch (Exception e) {
            handleError(e, req, res);
        }
    }

    /**
     * Prépare les arguments pour la méthode du contrôleur
     */
    private Object[] prepareMethodArguments(Method method, HttpServletRequest req, 
                                           HttpServletResponse res, Map<String, String> urlParams) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        Map<String, String[]> parameterMap = req.getParameterMap();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            String paramName = param.getName();

            // Types spéciaux
            if (HttpServletRequest.class.isAssignableFrom(paramType)) {
                args[i] = req;
                continue;
            }
            if (HttpServletResponse.class.isAssignableFrom(paramType)) {
                args[i] = res;
                continue;
            }

            // Binding d'objets (Sprint 8-bis)
            if (shouldBindObject(paramType)) {
                String prefix = paramName;
                RequestParam rp = param.getAnnotation(RequestParam.class);
                if (rp != null && !rp.value().isEmpty()) {
                    prefix = rp.value();
                }
                args[i] = ObjectBinder.bindObject(paramType, parameterMap, prefix);
                continue;
            }

            // Paramètres simples
            RequestParam rp = param.getAnnotation(RequestParam.class);
            String requestParamName = (rp != null && !rp.value().isEmpty()) ? rp.value() : paramName;
            
            String[] values = urlParams.containsKey(requestParamName) ? 
                new String[]{urlParams.get(requestParamName)} : 
                parameterMap.get(requestParamName);
            
            if (values != null && values.length > 0) {
                args[i] = ObjectBinder.convertToType(values[0], paramType);
            } else {
                args[i] = getDefaultValue(paramType);
            }
        }
        
        return args;
    }

    /**
     * Traite le résultat d'une méthode JSON
     */
    private void processJsonResult(Object result, HttpServletRequest req, 
                                  HttpServletResponse res, Json jsonAnnotation) throws IOException {
        // Toujours retourner du JSON
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setHeader("Expires", "0");
        
        String jsonResponse;
        
        try {
            if (result instanceof com.giga.spring.json.JsonResponse) {
                // Si c'est déjà un JsonResponse, le convertir directement
                jsonResponse = JsonConverter.toJson(result);
            } else {
                // Sinon, créer une réponse JSON standardisée
                jsonResponse = JsonConverter.toStandardJson(result);
            }
            
            // Écrire la réponse
            try (PrintWriter out = res.getWriter()) {
                out.write(jsonResponse);
            }
            
        } catch (Exception e) {
            // En cas d'erreur, retourner une erreur JSON
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse = JsonConverter.errorToJson(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                out.write(jsonResponse);
            }
        }
    }

    /**
     * Traite le résultat d'une méthode régulière
     */
    private void processRegularResult(Object result, HttpServletRequest req, 
                                     HttpServletResponse res) throws Exception {
        if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;
            mv.getModel().forEach(req::setAttribute);
            
            RequestDispatcher rd = req.getRequestDispatcher(mv.getView());
            rd.forward(req, res);
            return;
        }
        
        if (result instanceof String) {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.write((String) result);
            }
            return;
        }
        
        if (result != null) {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.write(result.toString());
            }
        }
    }

    /**
     * Vérifie si la requête semble être une API (Accept header ou extension .json)
     */
    private boolean isLikelyApiRequest(HttpServletRequest req) {
        String acceptHeader = req.getHeader("Accept");
        String path = req.getRequestURI();
        
        return (acceptHeader != null && acceptHeader.contains("application/json")) ||
               path.endsWith(".json") ||
               req.getParameter("format") != null && req.getParameter("format").equals("json");
    }

    /**
     * Gère les erreurs de manière appropriée (HTML ou JSON)
     */
    private void handleError(Exception e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        e.printStackTrace();
        
        if (isLikelyApiRequest(req)) {
            // Retourner l'erreur en JSON
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("application/json;charset=UTF-8");
            String jsonError = JsonConverter.errorToJson(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                out.write(jsonError);
            }
        } else {
            // Retourner l'erreur en HTML
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<h1>500 - Erreur Interne du Serveur</h1>");
                out.println("<pre>");
                e.printStackTrace(out);
                out.println("</pre>");
            }
        }
    }

    /**
     * Détermine si un type nécessite du binding d'objet
     */
    private boolean shouldBindObject(Class<?> type) {
        if (type.isPrimitive() ||
            type == String.class ||
            type == Integer.class ||
            type == Long.class ||
            type == Double.class ||
            type == Float.class ||
            type == Boolean.class ||
            type == Short.class ||
            type == Byte.class ||
            type == Character.class ||
            type == Date.class ||
            type.isEnum()) {
            return false;
        }
        
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            return !isSimpleComponentType(componentType);
        }
        
        return true;
    }

    private boolean isSimpleComponentType(Class<?> componentType) {
        return componentType.isPrimitive() ||
               componentType == String.class ||
               componentType == Integer.class ||
               componentType == Long.class ||
               componentType == Double.class ||
               componentType == Float.class ||
               componentType == Boolean.class ||
               componentType == Short.class ||
               componentType == Byte.class ||
               componentType == Character.class;
    }

    /**
     * Retourne la valeur par défaut pour un type
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (isLikelyApiRequest(req)) {
            // 404 en JSON
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                String jsonError = "{\"status\":\"error\",\"code\":404,\"message\":\"Resource not found: " + 
                                  req.getRequestURI() + "\"}";
                out.write(jsonError);
            }
        } else {
            // 404 en HTML
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<h1>404 - Ressource Non Trouvée</h1>");
                out.println("<p>URL: " + req.getRequestURI() + "</p>");
            }
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}