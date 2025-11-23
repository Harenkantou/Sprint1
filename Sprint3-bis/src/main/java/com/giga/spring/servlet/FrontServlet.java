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
                <!DOCTYPE html>
                <html>
                    <head>
                        <title>404 - Url tsy fantatra</title>
                        <style>
                            body {
                                font-family: Arial, sans-serif;
                                margin: 40px;
                                background: #f5f5f5;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                min-height: 80vh;
                            }
                            .container {
                                background: white;
                                padding: 40px;
                                border-radius: 8px;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                                text-align: center;
                                max-width: 600px;
                            }
                            h1 {
                                color: #e74c3c;
                                font-size: 48px;
                                margin: 0;
                            }
                            .error-code {
                                color: #95a5a6;
                                font-size: 24px;
                                margin: 10px 0;
                            }
                            .message {
                                color: #34495e;
                                font-size: 18px;
                                margin: 20px 0;
                            }
                            .url {
                                background: #ecf0f1;
                                padding: 10px;
                                border-radius: 5px;
                                font-family: monospace;
                                word-break: break-all;
                            }
                            a {
                                color: #3498db;
                                text-decoration: none;
                                font-weight: bold;
                            }
                            a:hover {
                                text-decoration: underline;
                            }
                        </style>
                    </head>
                    <body>
                        <div class='container'>
                            <h1>404</h1>
                            <div class='error-code'>Url tsy fantatra</div>
                            <div class='message'>L'URL demandée n'a pas été trouvée</div>
                            <div class='url'>%s</div>
                            <p style='margin-top: 30px;'>
                                <a href='%s/home'>← Retour à l'accueil</a>
                            </p>
                        </div>
                    </body>
                </html>
                """.formatted(uri, req.getContextPath());

            res.setContentType("text/html;charset=UTF-8");
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

}
