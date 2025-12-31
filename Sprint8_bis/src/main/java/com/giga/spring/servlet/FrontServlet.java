package com.giga.spring.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.giga.spring.annotation.RequestParam;
import com.giga.spring.binding.ObjectBinder;
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

        System.out.println("=== Routes chargées (Sprint 8-bis) ===");
        for (URLRoute route : routeRegistry.getAllRoutes()) {
            System.out.println(route.getUrlPattern() + " [" + route.getHttpMethod() + "] -> " + 
                             route.getMethod().getName());
        }
        System.out.println("=====================================");
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

            // Récupérer tous les paramètres de la requête
            Map<String, String[]> parameterMap = req.getParameterMap();
            
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Class<?> paramType = param.getType();
                String paramName = param.getName();

                // 1. Cas spécial: HttpServletRequest et HttpServletResponse
                if (HttpServletRequest.class.isAssignableFrom(paramType)) {
                    args[i] = req;
                    continue;
                }
                if (HttpServletResponse.class.isAssignableFrom(paramType)) {
                    args[i] = res;
                    continue;
                }

                // 2. Cas spécial: Map<String, Object> (Sprint 8)
                if (Map.class.isAssignableFrom(paramType)) {
                    Type genericType = param.getParameterizedType();
                    if (isValidMapType(genericType)) {
                        Map<String, Object> mapData = new HashMap<>();
                        
                        // Ajouter les paramètres simples
                        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                            String[] values = entry.getValue();
                            if (values != null && values.length == 1) {
                                mapData.put(entry.getKey(), values[0]);
                            } else if (values != null) {
                                mapData.put(entry.getKey(), values);
                            }
                        }
                        
                        // Ajouter les attributs
                        Enumeration<String> attrs = req.getAttributeNames();
                        while (attrs.hasMoreElements()) {
                            String attr = attrs.nextElement();
                            if (!mapData.containsKey(attr)) {
                                mapData.put(attr, req.getAttribute(attr));
                            }
                        }
                        
                        args[i] = mapData;
                        continue;
                    }
                }

                // 3. Cas Sprint 8-bis: Objet complexe
                // Vérifier si c'est un type qui nécessite du binding d'objet
                // (pas un type simple, pas un tableau de primitives)
                if (shouldBindObject(paramType)) {
                    // Utiliser le nom du paramètre comme préfixe (ex: "emp" pour emp.name)
                    String prefix = paramName;
                    
                    // Vérifier si @RequestParam spécifie un autre nom
                    RequestParam rp = param.getAnnotation(RequestParam.class);
                    if (rp != null && !rp.value().isEmpty()) {
                        prefix = rp.value();
                    }
                    
                    // Binding de l'objet
                    Object boundObject = ObjectBinder.bindObject(paramType, parameterMap, prefix);
                    args[i] = boundObject;
                    continue;
                }

                // 4. Paramètres simples avec @RequestParam
                RequestParam rp = param.getAnnotation(RequestParam.class);
                String requestParamName = paramName;
                
                if (rp != null && !rp.value().isEmpty()) {
                    requestParamName = rp.value();
                }
                
                // Chercher la valeur
                String[] values = null;
                
                // D'abord dans les paramètres d'URL
                if (urlParams.containsKey(requestParamName)) {
                    values = new String[]{urlParams.get(requestParamName)};
                }
                // Sinon dans les paramètres de requête
                else {
                    values = parameterMap.get(requestParamName);
                }
                
                // Convertir la valeur
                if (values != null && values.length > 0) {
                    args[i] = ObjectBinder.convertToType(values[0], paramType);
                } else {
                    // Pour les tableaux de String
                    if (paramType.isArray() && paramType.getComponentType() == String.class) {
                        args[i] = values != null ? values : new String[0];
                    } else {
                        args[i] = getDefaultValue(paramType);
                    }
                }
            }

            // Appeler la méthode du contrôleur
            System.out.println("Invoking " + method.getName() + " with " + args.length + " args");
            Object result = method.invoke(controller, args);

            // Traiter le résultat
            processResult(result, req, res);

        } catch (Exception e) {
            handleError(e, res);
        }
    }

    /**
     * Vérifie si le type est une Map valide
     */
    private boolean isValidMapType(Type genericType) {
        if (!(genericType instanceof ParameterizedType)) {
            return true; // Map raw acceptée
        }
        
        ParameterizedType pType = (ParameterizedType) genericType;
        Type[] typeArgs = pType.getActualTypeArguments();
        
        if (typeArgs.length != 2) {
            return false;
        }
        
        // Clé doit être String
        if (!(typeArgs[0] instanceof Class) || !String.class.isAssignableFrom((Class<?>) typeArgs[0])) {
            return false;
        }
        
        // Valeur doit être Object ou String
        if (typeArgs[1] instanceof Class) {
            Class<?> valueType = (Class<?>) typeArgs[1];
            return Object.class.isAssignableFrom(valueType) || 
                   String.class.isAssignableFrom(valueType);
        }
        
        return typeArgs[1] == Object.class;
    }

    /**
     * Détermine si un type nécessite du binding d'objet
     */
    private boolean shouldBindObject(Class<?> type) {
        // Ne pas binder les types simples
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
            type.isEnum() ||
            Number.class.isAssignableFrom(type) ||
            type.getName().startsWith("java.time.") ||
            type.getName().startsWith("java.math.")) {
            return false;
        }
        
        // Ne pas binder les tableaux de types simples
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            return !isSimpleComponentType(componentType);
        }
        
        // Binder les autres types (objets complexes)
        return true;
    }

    /**
     * Vérifie si un composant de tableau est un type simple
     */
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

    /**
     * Traite le résultat de la méthode
     */
    private void processResult(Object result, HttpServletRequest req, HttpServletResponse res) 
            throws Exception {
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
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}