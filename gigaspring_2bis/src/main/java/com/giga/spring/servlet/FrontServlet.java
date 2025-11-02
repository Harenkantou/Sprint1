package com.giga.spring.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.giga.spring.mapping.ControllerScanner;
import com.giga.spring.mapping.RouteRegistry;
import com.giga.spring.mapping.URLRoute;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This is the servlet that takes all incoming requests targeting the app - If
 * the requested resource exists, it delegates to the default dispatcher - else
 * it checks if a controller handles the URL via @URLMapping annotation
 */
public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;
    RouteRegistry routeRegistry;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        
        // Initialiser le registre de routes
        routeRegistry = new RouteRegistry();
        
        // Scanner les contrôleurs depuis le paramètre d'initialisation
        String basePackage = config.getInitParameter("controller-package");
        if (basePackage == null || basePackage.isEmpty()) {
            // Package par défaut si non spécifié
            basePackage = "com.giga.springlab.controller";
        }
        
        System.out.println("=== Initialisation du FrontServlet ===");
        System.out.println("Scan du package: " + basePackage);
        
        // Scanner et enregistrer les routes
        List<URLRoute> routes = ControllerScanner.scanPackage(basePackage);
        routeRegistry.registerRoutes(routes);
        
        // Afficher les routes enregistrées
        routeRegistry.printRoutes();
        System.out.println("=== FrontServlet initialisé ===");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        /**
         * Example: 
         * If URI is /app/folder/file.html 
         * and context path is /app,
         * then path = /folder/file.html
         */
        String path = req.getRequestURI().substring(req.getContextPath().length());
        
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            // La ressource existe (fichier statique, JSP, etc.)
            defaultServe(req, res);
        } else {
            // La ressource n'existe pas, vérifier si une route correspond
            URLRoute route = routeRegistry.findRoute(path);
            
            if (route != null) {
                // Une route correspond, invoquer le contrôleur
                invokeController(route, path, req, res);
            } else {
                // Aucune route ne correspond, afficher l'erreur par défaut
                customServe(req, res);
            }
        }
    }

    /**
     * Invoque la méthode du contrôleur correspondant à la route
     */
    private void invokeController(URLRoute route, String path, HttpServletRequest req, HttpServletResponse res) 
            throws IOException {
        try {
            // Extraire les paramètres de l'URL
            Map<String, String> urlParams = route.extractParams(path);
            
            // Ajouter les paramètres en tant qu'attributs de la requête
            for (Map.Entry<String, String> entry : urlParams.entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
            
            // Invoquer la méthode du contrôleur
            Method method = route.getMethod();
            Object controller = route.getController();
            
            // La méthode doit accepter (HttpServletRequest, HttpServletResponse)
            method.invoke(controller, req, res);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de l'invocation du contrôleur: " + e.getMessage());
            e.printStackTrace();
            
            // Afficher une erreur 500
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                res.setContentType("text/html;charset=UTF-8");
                out.println("<html><head><title>Erreur Serveur</title></head><body>");
                out.println("<h1>Erreur 500 - Erreur Interne du Serveur</h1>");
                out.println("<p>Une erreur s'est produite lors du traitement de la requête.</p>");
                out.println("<pre>" + e.getMessage() + "</pre>");
                out.println("</body></html>");
            }
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = """
                <html>
                    <head><title>Resource Not Found</title></head>
                    <body>
                        <h1>Unknown resource</h1>
                        <p>The requested URL was not found: <strong>%s</strong></p>
                        <p>Aucune route ne correspond à cette URL.</p>
                    </body>
                </html>
                """.formatted(uri);

            res.setContentType("text/html;charset=UTF-8");
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

}
