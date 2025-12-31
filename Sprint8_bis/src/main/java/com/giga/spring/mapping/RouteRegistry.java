package com.giga.spring.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteRegistry {
    private final Map<String, List<URLRoute>> routeMap = new HashMap<>();

    public void registerRoute(URLRoute route) {
        String url = route.getUrlPattern();
        routeMap.computeIfAbsent(url, k -> new ArrayList<>()).add(route);
    }

    public void registerRoutes(List<URLRoute> routes) {
        for (URLRoute route : routes) registerRoute(route);
    }

    public URLRoute findRoute(String url, String httpMethod) {
        for (List<URLRoute> routes : routeMap.values()) {
            for (URLRoute route : routes) {
                if (route.matches(url) && route.matchesHttpMethod(httpMethod)) return route;
            }
        }
        return null;
    }

    public List<URLRoute> getAllRoutes() {
        List<URLRoute> all = new ArrayList<>();
        for (List<URLRoute> routes : routeMap.values()) all.addAll(routes);
        return all;
    }
}
