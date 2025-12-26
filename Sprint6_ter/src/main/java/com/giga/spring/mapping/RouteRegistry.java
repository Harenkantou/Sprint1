package com.giga.spring.mapping;

import java.util.ArrayList;
import java.util.List;

public class RouteRegistry {
    private final List<URLRoute> routes = new ArrayList<>();

    public void registerRoute(URLRoute route) { routes.add(route); }
    public void registerRoutes(List<URLRoute> routes) { this.routes.addAll(routes); }
    public URLRoute findRoute(String url) { for (URLRoute r : routes) if (r.matches(url)) return r; return null; }
    public List<URLRoute> getAllRoutes() { return new ArrayList<>(routes); }
}
