package com.giga.spring.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.giga.spring.annotation.Json;
import com.giga.spring.annotation.Authorized;
import com.giga.spring.annotation.RequestParam;
import com.giga.spring.binding.ObjectBinder;
import com.giga.spring.json.JsonConverter;
import com.giga.spring.mapping.ControllerScanner;
import com.giga.spring.mapping.RouteRegistry;
import com.giga.spring.mapping.URLRoute;
import com.giga.spring.model.ModelView;
import com.giga.spring.upload.FileUploadUtils;
import com.giga.spring.upload.UploadedFile;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Configuration pour l'upload de fichiers
@MultipartConfig(
    maxFileSize = 1024 * 1024 * 10,      // 10MB max par fichier
    maxRequestSize = 1024 * 1024 * 50,   // 50MB max par requête
    fileSizeThreshold = 1024 * 1024      // 1MB avant écriture sur disque
)
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

        System.out.println("=== Routes chargées (Sprint 10 - File Upload) ===");
        for (URLRoute route : routeRegistry.getAllRoutes()) {
            Method method = route.getMethod();
            boolean hasJson = method.isAnnotationPresent(Json.class);
            System.out.println(route.getUrlPattern() + " [" + route.getHttpMethod() + "] -> " + 
                             route.getMethod().getName() + (hasJson ? " [JSON]" : ""));
        }
        System.out.println("=================================================");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod();

        System.out.println("Requête: " + httpMethod + " " + path + 
                         (FileUploadUtils.isMultipartRequest(req) ? " [MULTIPART]" : ""));

        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            defaultServe(req, res);
            return;
        }

        URLRoute route = routeRegistry.findRoute(path, httpMethod);
        if (route != null) {
            invokeController(route, path, req, res);
        } else {
            handleNotFound(req, res);
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
            
            // Vérifier si c'est une méthode JSON
            boolean isJsonMethod = method.isAnnotationPresent(Json.class);
            
            // Vérifier l'annotation d'autorisation avant de préparer les arguments
            if (!checkAuthorization(method, req, res)) {
                return; // réponse déjà écrite par checkAuthorization
            }

            // Préparer les arguments (avec support upload de fichiers)
            Object[] args = prepareMethodArguments(method, req, res, urlParams);
            
            // Appeler la méthode du contrôleur
            System.out.println("Invoking " + method.getName() + 
                             (isJsonMethod ? " [JSON]" : "") + 
                             (FileUploadUtils.isMultipartRequest(req) ? " [WITH FILES]" : ""));
            
            Object result = method.invoke(controller, args);
            
            // Traiter le résultat
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
     * Vérifie l'annotation @Authorized sur la méthode et valide la session/utilisateur.
     * Si l'accès est refusé, écrit la réponse (401/403) et retourne false.
     */
    private boolean checkAuthorization(Method method, HttpServletRequest req, HttpServletResponse res) throws IOException {
        Authorized auth = method.getAnnotation(Authorized.class);
        if (auth == null) return true; // pas de restriction

        // anonymous explicit allow
        if (auth.anonymous()) return true;

        javax.servlet.http.HttpSession session = req.getSession(false);

        // authenticated required
        if (auth.authenticated()) {
            if (session != null && session.getAttribute("currentUser") != null) return true;
            sendAuthError(res, req, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return false;
        }

        // specific roles
        String[] allowed = auth.roles();
        if (allowed != null && allowed.length > 0) {
            if (session != null) {
                Object rolesObj = session.getAttribute("roles");
                java.util.Set<String> userRoles = new java.util.HashSet<>();
                if (rolesObj instanceof java.util.Collection) {
                    for (Object r : (java.util.Collection<?>) rolesObj) if (r != null) userRoles.add(r.toString());
                } else if (rolesObj instanceof String[]) {
                    for (String r : (String[]) rolesObj) if (r != null) userRoles.add(r);
                } else if (rolesObj instanceof String) {
                    String s = (String) rolesObj;
                    for (String r : s.split(",")) if (!r.isBlank()) userRoles.add(r.trim());
                }

                for (String a : allowed) {
                    if (userRoles.contains(a)) return true;
                }
            }

            sendAuthError(res, req, HttpServletResponse.SC_FORBIDDEN, "Insufficient role");
            return false;
        }

        // Default deny
        sendAuthError(res, req, HttpServletResponse.SC_UNAUTHORIZED, "Access denied");
        return false;
    }

    private void sendAuthError(HttpServletResponse res, HttpServletRequest req, int status, String message) throws IOException {
        boolean api = isLikelyApiRequest(req);
        res.setStatus(status);
        if (api) {
            res.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.write("{\"status\":\"error\",\"code\":" + status + ",\"message\":\"" + message + "\"}");
            }
        } else {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<h1>" + status + " - " + message + "</h1>");
            }
        }
    }

    /**
     * Prépare les arguments pour la méthode du contrôleur avec support upload
     */
    private Object[] prepareMethodArguments(Method method, HttpServletRequest req, 
                                           HttpServletResponse res, Map<String, String> urlParams) 
            throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        // Récupérer les paramètres normaux
        Map<String, String[]> parameterMap = req.getParameterMap();
        
        // Récupérer les fichiers uploadés (si c'est une requête multipart)
        Map<String, UploadedFile> uploadedFiles = null;
        if (FileUploadUtils.isMultipartRequest(req)) {
            uploadedFiles = FileUploadUtils.getUploadedFiles(req);
            System.out.println("Found " + uploadedFiles.size() + " uploaded file(s)");
        }
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            String paramName = param.getName();

            // 1. Types spéciaux: HttpServletRequest, HttpServletResponse
            if (HttpServletRequest.class.isAssignableFrom(paramType)) {
                args[i] = req;
                continue;
            }
            if (HttpServletResponse.class.isAssignableFrom(paramType)) {
                args[i] = res;
                continue;
            }
            
            // 2. UploadedFile (Sprint 10)
            if (UploadedFile.class.isAssignableFrom(paramType)) {
                // Vérifier s'il y a un fichier avec ce nom
                UploadedFile file = null;
                if (uploadedFiles != null) {
                    // Chercher par nom du paramètre
                    file = uploadedFiles.get(paramName);
                    
                    // Sinon chercher par @RequestParam
                    if (file == null) {
                        RequestParam rp = param.getAnnotation(RequestParam.class);
                        if (rp != null && !rp.value().isEmpty()) {
                            file = uploadedFiles.get(rp.value());
                        }
                    }
                }
                args[i] = file;
                continue;
            }
            
            // 3. Map<String, UploadedFile> pour tous les fichiers
            if (Map.class.isAssignableFrom(paramType)) {
                Type genericType = param.getParameterizedType();
                if (isUploadedFileMap(genericType)) {
                    args[i] = uploadedFiles != null ? 
                             new HashMap<>(uploadedFiles) : 
                             new HashMap<String, UploadedFile>();
                    continue;
                }
            }
            
            // 4. List<UploadedFile> pour plusieurs fichiers
            if (List.class.isAssignableFrom(paramType) && 
                isUploadedFileList(param.getParameterizedType())) {
                if (uploadedFiles != null) {
                    args[i] = new ArrayList<>(uploadedFiles.values());
                } else {
                    args[i] = new ArrayList<UploadedFile>();
                }
                continue;
            }
            
            // 5. UploadedFile[] pour tableau de fichiers
            if (paramType.isArray() && paramType.getComponentType() == UploadedFile.class) {
                if (uploadedFiles != null) {
                    args[i] = uploadedFiles.values().toArray(new UploadedFile[0]);
                } else {
                    args[i] = new UploadedFile[0];
                }
                continue;
            }
            
            // 6. Binding d'objets réguliers (Sprint 8-bis)
            if (shouldBindObject(paramType)) {
                String prefix = paramName;
                RequestParam rp = param.getAnnotation(RequestParam.class);
                if (rp != null && !rp.value().isEmpty()) {
                    prefix = rp.value();
                }
                args[i] = ObjectBinder.bindObject(paramType, parameterMap, prefix);
                continue;
            }
            
            // 7. Paramètres simples
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
     * Vérifie si le type est Map<String, UploadedFile>
     */
    private boolean isUploadedFileMap(Type genericType) {
        if (!(genericType instanceof ParameterizedType)) {
            return false;
        }
        
        ParameterizedType pType = (ParameterizedType) genericType;
        Type[] typeArgs = pType.getActualTypeArguments();
        
        if (typeArgs.length != 2) {
            return false;
        }
        
        // Vérifier Map<String, UploadedFile>
        if (typeArgs[0] == String.class || 
            (typeArgs[0] instanceof Class && String.class.isAssignableFrom((Class<?>) typeArgs[0]))) {
            
            if (typeArgs[1] == UploadedFile.class ||
                (typeArgs[1] instanceof Class && UploadedFile.class.isAssignableFrom((Class<?>) typeArgs[1]))) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Vérifie si le type est List<UploadedFile>
     */
    private boolean isUploadedFileList(Type genericType) {
        if (!(genericType instanceof ParameterizedType)) {
            return false;
        }
        
        ParameterizedType pType = (ParameterizedType) genericType;
        Type[] typeArgs = pType.getActualTypeArguments();
        
        if (typeArgs.length != 1) {
            return false;
        }
        
        return typeArgs[0] == UploadedFile.class ||
               (typeArgs[0] instanceof Class && UploadedFile.class.isAssignableFrom((Class<?>) typeArgs[0]));
    }

    /**
     * Vérifie si un type nécessite du binding d'objet
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
            type.isEnum() ||
            UploadedFile.class.isAssignableFrom(type)) {
            return false;
        }
        
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            return !isSimpleComponentType(componentType) && componentType != UploadedFile.class;
        }
        
        if (Map.class.isAssignableFrom(type) || List.class.isAssignableFrom(type)) {
            return false; // Traités séparément
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

    /**
     * Traite le résultat d'une méthode JSON
     */
    private void processJsonResult(Object result, HttpServletRequest req, 
                                  HttpServletResponse res, Json jsonAnnotation) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        
        String jsonResponse;
        
        try {
            if (result instanceof com.giga.spring.json.JsonResponse) {
                jsonResponse = JsonConverter.toJson(result);
            } else {
                jsonResponse = JsonConverter.toStandardJson(result);
            }
            
            try (PrintWriter out = res.getWriter()) {
                out.write(jsonResponse);
            }
            
        } catch (Exception e) {
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
     * Vérifie si la requête semble être une API
     */
    private boolean isLikelyApiRequest(HttpServletRequest req) {
        String acceptHeader = req.getHeader("Accept");
        String path = req.getRequestURI();
        
        return (acceptHeader != null && acceptHeader.contains("application/json")) ||
               path.endsWith(".json") ||
               req.getParameter("format") != null && req.getParameter("format").equals("json");
    }

    /**
     * Gère les erreurs
     */
    private void handleError(Exception e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        e.printStackTrace();
        
        if (isLikelyApiRequest(req)) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("application/json;charset=UTF-8");
            String jsonError = JsonConverter.errorToJson(e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                out.write(jsonError);
            }
        } else {
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
     * Gère les requêtes non trouvées
     */
    private void handleNotFound(HttpServletRequest req, HttpServletResponse res) throws IOException {
        boolean urlExists = false;
        StringBuilder availableMethods = new StringBuilder();
        
        for (URLRoute r : routeRegistry.getAllRoutes()) {
            if (r.matches(req.getRequestURI().substring(req.getContextPath().length()))) {
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

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (isLikelyApiRequest(req)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                String jsonError = "{\"status\":\"error\",\"code\":404,\"message\":\"Resource not found: " + 
                                  req.getRequestURI() + "\"}";
                out.write(jsonError);
            }
        } else {
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