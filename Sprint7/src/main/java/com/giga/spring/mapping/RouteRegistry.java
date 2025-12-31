// RouteRegistry.java (modifié)
package com.giga.spring.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteRegistry {
    // Map: URL → Liste de routes (pour GET/POST/ANY sur la même URL)
    private final Map<String, List<URLRoute>> routeMap = new HashMap<>();

    public void registerRoute(URLRoute route) {
        String url = route.getUrlPattern();
        routeMap.computeIfAbsent(url, k -> new ArrayList<>()).add(route);
    }

    public void registerRoutes(List<URLRoute> routes) {
        for (URLRoute route : routes) {
            registerRoute(route);
        }
    }

    public URLRoute findRoute(String url, String httpMethod) {
        for (Map.Entry<String, List<URLRoute>> entry : routeMap.entrySet()) {
            // Vérifier chaque route pour cette URL
            for (URLRoute route : entry.getValue()) {
                if (route.matches(url) && route.matchesHttpMethod(httpMethod)) {
                    return route;
                }
            }
        }
        return null;
    }

    public List<URLRoute> getAllRoutes() {
        List<URLRoute> allRoutes = new ArrayList<>();
        for (List<URLRoute> routes : routeMap.values()) {
            allRoutes.addAll(routes);
        }
        return allRoutes;
    }
    
    // Méthode utilitaire pour debugging
    public Map<String, List<URLRoute>> getRouteMap() {
        return new HashMap<>(routeMap);
    }
}