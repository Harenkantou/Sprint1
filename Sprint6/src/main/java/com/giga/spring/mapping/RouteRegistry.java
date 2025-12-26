package com.giga.spring.mapping;

import java.util.ArrayList;
import java.util.List;

public class RouteRegistry {
    private final List<URLRoute> routes = new ArrayList<>();

    public void registerRoute(URLRoute route) {
        routes.add(route);
    }

    public void registerRoutes(List<URLRoute> routes) {
        this.routes.addAll(routes);
    }

    public URLRoute findRoute(String url) {
        for (URLRoute route : routes) {
            if (route.matches(url)) return route;
        }
        return null;
    }

    public List<URLRoute> getAllRoutes() { return new ArrayList<>(routes); }
}
