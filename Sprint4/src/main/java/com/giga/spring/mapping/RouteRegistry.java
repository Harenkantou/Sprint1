package com.giga.spring.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registre central de toutes les routes de l'application
 */
public class RouteRegistry {
    private final List<URLRoute> routes = new ArrayList<>();

    /**
     * Enregistre une nouvelle route
     */
    public void registerRoute(URLRoute route) {
        routes.add(route);
    }

    /**
     * Enregistre plusieurs routes
     */
    public void registerRoutes(List<URLRoute> routes) {
        this.routes.addAll(routes);
    }

    /**
     * Trouve la route correspondant à une URL
     * @return La route trouvée, ou null si aucune correspondance
     */
    public URLRoute findRoute(String url) {
        for (URLRoute route : routes) {
            if (route.matches(url)) {
                return route;
            }
        }
        return null;
    }

    /**
     * Extrait les paramètres d'une URL pour une route donnée
     */
    public Map<String, String> extractParams(URLRoute route, String url) {
        return route.extractParams(url);
    }

    /**
     * Retourne toutes les routes enregistrées
     */
    public List<URLRoute> getAllRoutes() {
        return new ArrayList<>(routes);
    }

    /**
     * Retourne le nombre de routes enregistrées
     */
    public int size() {
        return routes.size();
    }

    /**
     * Affiche toutes les routes enregistrées
     */
    public void printRoutes() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         ROUTES ENREGISTRÉES                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        
        if (routes.isEmpty()) {
            System.out.println("  ⚠ Aucune route enregistrée");
        } else {
            for (URLRoute route : routes) {
                String className = route.getController().getClass().getSimpleName();
                String methodName = route.getMethod().getName();
                String urlPattern = route.getUrlPattern();
                
                System.out.println("\n  📍 URL: " + urlPattern);
                System.out.println("     ├─ Classe: " + className);
                System.out.println("     └─ Méthode: " + methodName + "()");
            }
            
            System.out.println("\n╔════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║  Total: " + routes.size() + " route(s) chargée(s) avec succès                           ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════════════╝\n");
        }
    }
}
