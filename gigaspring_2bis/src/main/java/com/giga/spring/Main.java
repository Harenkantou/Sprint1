package com.giga.spring;

import java.util.List;

import com.giga.spring.mapping.ControllerScanner;
import com.giga.spring.mapping.URLRoute;

/**
 * Entrypoint pour lister les controllers annotés et leurs URL mappings.
 * Utilise le scanner existant (qui respecte désormais @AnnotationClass).
 */
public class Main {
    public static void main(String[] args) {
    // base package to scan - adjust if your controllers live elsewhere
    // Default to the controllers package to avoid loading servlet or other framework classes.
    String basePackage = args.length > 0 ? args[0] : "com.giga.spring.controller";

        System.out.println("Scanning package: " + basePackage);
        List<URLRoute> routes = ControllerScanner.scanPackage(basePackage);

        if (routes.isEmpty()) {
            System.out.println("Aucun controller annoté avec des routes n'a été trouvé.");
            return;
        }

        System.out.println("Controllers et leurs URL mappings:");
        for (URLRoute route : routes) {
            System.out.printf("Controller: %s, Method: %s, URL: %s\n",
                    route.getController().getClass().getName(),
                    route.getMethod().getName(),
                    route.getUrlPattern());
        }

        System.out.println("Total routes trouvées: " + routes.size());
    }
}
