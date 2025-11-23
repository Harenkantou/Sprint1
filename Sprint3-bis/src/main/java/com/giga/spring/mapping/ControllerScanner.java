package com.giga.spring.mapping;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.URLMapping;

/**
 * Scanner pour détecter les contrôleurs et leurs annotations URLMapping
 */
public class ControllerScanner {

    /**
     * Scanne un package pour trouver tous les contrôleurs et leurs routes
     */
    public static List<URLRoute> scanPackage(String packageName) {
        List<URLRoute> routes = new ArrayList<>();
        
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            
            List<File> dirs = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                dirs.add(new File(resource.getFile()));
            }
            
            for (File directory : dirs) {
                routes.addAll(findControllers(directory, packageName));
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du scan du package " + packageName + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return routes;
    }

    /**
     * Trouve récursivement tous les contrôleurs dans un répertoire
     */
    private static List<URLRoute> findControllers(File directory, String packageName) {
        List<URLRoute> routes = new ArrayList<>();
        
        if (!directory.exists()) {
            return routes;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return routes;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                routes.addAll(findControllers(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                routes.addAll(scanClass(className));
            }
        }
        
        return routes;
    }

    /**
     * Scanne une classe pour trouver ses méthodes annotées avec @URLMapping
     */
    private static List<URLRoute> scanClass(String className) {
        List<URLRoute> routes = new ArrayList<>();
        
        try {
            Class<?> clazz = Class.forName(className);
            
            // Vérifier si la classe est annotée avec @Controller
            if (!clazz.isAnnotationPresent(Controller.class)) {
                return routes;
            }
            
            System.out.println("🎯 Contrôleur trouvé: " + clazz.getSimpleName());
            
            // Créer une instance du contrôleur
            Object controller = clazz.getDeclaredConstructor().newInstance();
            
            // Appeler la méthode init() si c'est une sous-classe de Controller
            if (controller instanceof com.giga.spring.controller.Controller) {
                ((com.giga.spring.controller.Controller) controller).init();
            }
            
            // Scanner les méthodes
            for (Method method : clazz.getDeclaredMethods()) {
                URLMapping annotation = method.getAnnotation(URLMapping.class);
                if (annotation != null) {
                    String urlPattern = annotation.value();
                    URLRoute route = new URLRoute(urlPattern, controller, method);
                    routes.add(route);
                    System.out.println("   📍 " + urlPattern + " → " + method.getName() + "()");
                    
                    // Afficher les paramètres détectés pour le debugging
                    if (urlPattern.contains("{")) {
                        System.out.println("      🔍 Paramètres URL: " + urlPattern);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du scan de la classe " + className + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return routes;
    }
}
