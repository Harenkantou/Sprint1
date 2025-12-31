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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

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

        System.out.println("=== Routes chargées (Sprint 8) ===");
        for (URLRoute route : routeRegistry.getAllRoutes()) {
            System.out.println(route.getUrlPattern() + " [" + route.getHttpMethod() + "] -> " + 
                             route.getMethod().getName());
        }
        System.out.println("================================");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod();

        System.out.println("Requête: " + httpMethod + " " + path);

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
                // 405 Method Not Allowed
                res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                res.setHeader("Allow", availableMethods.toString());
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println("<h1>405 - Méthode Non Autorisée</h1>");
                    out.println("<p>URL: " + req.getRequestURI() + "</p>");
                    out.println("<p>Méthode utilisée: " + httpMethod + "</p>");
                    out.println("<p>Méthodes autorisées: " + availableMethods + "</p>");
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
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];

            // Préparer les données de la requête pour le Map
            Map<String, Object> allRequestData = prepareRequestData(req);

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Class<?> paramType = param.getType();
                Type genericType = param.getParameterizedType();

                // 1. Cas spécial: Map<String, Object> ou Map<String, String>
                if (Map.class.isAssignableFrom(paramType)) {
                    if (isValidMapParameter(genericType)) {
                        // Créer une copie des données de la requête
                        Map<String, Object> paramMap = new HashMap<>(allRequestData);
                        args[i] = paramMap;
                    } else {
                        // Type de Map non supporté
                        System.err.println("Warning: Unsupported Map type in method " + 
                                         method.getName() + ". Expected Map<String, Object> or Map<String, String>");
                        args[i] = null;
                    }
                    continue;
                }

                // 2. HttpServletRequest et HttpServletResponse
                if (HttpServletRequest.class.isAssignableFrom(paramType)) {
                    args[i] = req;
                    continue;
                }
                if (HttpServletResponse.class.isAssignableFrom(paramType)) {
                    args[i] = res;
                    continue;
                }

                // 3. Paramètres avec annotation @RequestParam
                RequestParam rp = param.getAnnotation(RequestParam.class);
                String paramName = param.getName();
                Object paramValue = null;

                if (rp != null && !rp.value().isEmpty()) {
                    paramName = rp.value();
                }

                // Vérifier d'abord les paramètres d'URL
                if (urlParams.containsKey(paramName)) {
                    paramValue = urlParams.get(paramName);
                } 
                // Sinon chercher dans les paramètres de requête
                else {
                    paramValue = extractParameterValue(req, paramName, paramType);
                }

                // Si valeur trouvée, convertir au type approprié
                if (paramValue != null) {
                    args[i] = convertToType(paramValue, paramType);
                } else {
                    args[i] = getDefaultValue(paramType);
                }
            }

            // Appeler la méthode du contrôleur
            Object result = method.invoke(controller, args);

            // Traiter le résultat
            processResult(result, req, res);

        } catch (Exception e) {
            handleError(e, res);
        }
    }

    /**
     * Vérifie si le type générique est Map<String, Object> ou Map<String, String>
     */
    private boolean isValidMapParameter(Type genericType) {
        if (!(genericType instanceof ParameterizedType)) {
            // Map raw - accepté avec warning
            System.out.println("Warning: Raw Map parameter detected. Consider specifying generic types.");
            return true;
        }
        
        ParameterizedType pType = (ParameterizedType) genericType;
        Type[] typeArgs = pType.getActualTypeArguments();
        
        if (typeArgs.length != 2) {
            return false;
        }
        
        // Vérifier que la clé est String
        if (!(typeArgs[0] instanceof Class) || !String.class.isAssignableFrom((Class<?>) typeArgs[0])) {
            return false;
        }
        
        // Vérifier que la valeur est Object ou String
        if (typeArgs[1] instanceof Class) {
            Class<?> valueType = (Class<?>) typeArgs[1];
            return Object.class.isAssignableFrom(valueType) || 
                   String.class.isAssignableFrom(valueType);
        }
        
        // Accepte aussi ? (wildcard) et Object.class
        return typeArgs[1] == Object.class || 
               typeArgs[1].toString().equals("?");
    }

    /**
     * Prépare toutes les données de la requête dans une Map
     */
    private Map<String, Object> prepareRequestData(HttpServletRequest req) {
        Map<String, Object> data = new HashMap<>();
        
        // 1. Paramètres de requête (GET/POST)
        Enumeration<String> paramNames = req.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String[] values = req.getParameterValues(name);
            
            if (values != null) {
                if (values.length == 1) {
                    data.put(name, values[0]);
                } else {
                    // Garder le tableau pour les valeurs multiples
                    data.put(name, values);
                }
            }
        }
        
        // 2. Attributs de requête
        Enumeration<String> attrNames = req.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            String name = attrNames.nextElement();
            if (!data.containsKey(name)) { // Ne pas écraser les paramètres
                data.put(name, req.getAttribute(name));
            }
        }
        
        // 3. Ajouter quelques infos de la requête
        data.put("_request_method", req.getMethod());
        data.put("_request_uri", req.getRequestURI());
        data.put("_context_path", req.getContextPath());
        
        return data;
    }

    /**
     * Extrait la valeur d'un paramètre en fonction de son type
     */
    private Object extractParameterValue(HttpServletRequest req, String paramName, Class<?> paramType) {
        // Cas spécial: tableaux
        if (paramType.isArray()) {
            String[] values = req.getParameterValues(paramName);
            if (values != null && values.length > 0) {
                return values;
            }
            return null;
        }
        
        // Cas standard: valeur unique
        String value = req.getParameter(paramName);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        
        return null;
    }

    /**
     * Convertit une valeur au type approprié
     */
    private Object convertToType(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }
        
        // Si déjà du bon type
        if (targetType.isInstance(value)) {
            return value;
        }
        
        // Conversion depuis String
        if (value instanceof String) {
            String strValue = (String) value;
            return convertStringToType(strValue, targetType);
        }
        
        // Conversion depuis String[] (pour les tableaux)
        if (value instanceof String[] && targetType.isArray()) {
            String[] strArray = (String[]) value;
            return convertStringArrayToType(strArray, targetType.getComponentType());
        }
        
        // Autres cas
        return value;
    }

    private Object convertStringToType(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return getDefaultValue(targetType);
        }
        
        try {
            if (targetType == String.class) return value;
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
            if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
            if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
            if (targetType == short.class || targetType == Short.class) return Short.parseShort(value);
            if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(value);
            if (targetType == char.class || targetType == Character.class) {
                return value.length() > 0 ? value.charAt(0) : '\0';
            }
            if (targetType.isEnum()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
                return Enum.valueOf(enumType, value);
            }
        } catch (Exception e) {
            System.err.println("Conversion error for value '" + value + "' to type " + targetType.getName());
            return getDefaultValue(targetType);
        }
        
        return value;
    }

    private Object convertStringArrayToType(String[] values, Class<?> componentType) {
        if (componentType == String.class) {
            return values;
        }
        
        if (componentType == int.class) {
            int[] result = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                try {
                    result[i] = Integer.parseInt(values[i]);
                } catch (NumberFormatException e) {
                    result[i] = 0;
                }
            }
            return result;
        }
        
        if (componentType == Integer.class) {
            Integer[] result = new Integer[values.length];
            for (int i = 0; i < values.length; i++) {
                try {
                    result[i] = Integer.parseInt(values[i]);
                } catch (NumberFormatException e) {
                    result[i] = 0;
                }
            }
            return result;
        }
        
        // Pour d'autres types, retourner un tableau d'Object
        Object[] result = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = convertStringToType(values[i], componentType);
        }
        return result;
    }

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

    /**
     * Traite le résultat de la méthode du contrôleur
     */
    private void processResult(Object result, HttpServletRequest req, HttpServletResponse res) 
            throws Exception {
        if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;
            // Ajouter les données du modèle aux attributs de requête
            mv.getModel().forEach(req::setAttribute);
            
            // Forward vers la vue
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

    private void handleError(Exception e, HttpServletResponse res) throws IOException {
        e.printStackTrace();
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<h1>500 - Erreur Interne du Serveur</h1>");
            out.println("<p>" + e.getMessage() + "</p>");
            out.println("<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<h1>404 - Ressource Non Trouvée</h1>");
            out.println("<p>URL: " + req.getRequestURI() + "</p>");
            out.println("<p>Cette ressource n'existe pas sur le serveur.</p>");
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}