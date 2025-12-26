package com.giga.spring.mapping;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.URLMapping;

public class ControllerScanner {
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
            for (File directory : dirs) routes.addAll(findControllers(directory, packageName));
        } catch (Exception e) {
            System.err.println("Erreur scan package: " + e.getMessage());
        }
        return routes;
    }

    private static List<URLRoute> findControllers(File directory, String packageName) {
        List<URLRoute> routes = new ArrayList<>();
        if (!directory.exists()) return routes;
        File[] files = directory.listFiles();
        if (files == null) return routes;
        for (File file : files) {
            if (file.isDirectory()) routes.addAll(findControllers(file, packageName + "." + file.getName()));
            else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                routes.addAll(scanClass(className));
            }
        }
        return routes;
    }

    private static List<URLRoute> scanClass(String className) {
        List<URLRoute> routes = new ArrayList<>();
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isAnnotationPresent(Controller.class)) return routes;
            Object controller = clazz.getDeclaredConstructor().newInstance();
            if (controller instanceof com.giga.spring.controller.Controller) ((com.giga.spring.controller.Controller) controller).init();
            for (Method method : clazz.getDeclaredMethods()) {
                URLMapping annotation = method.getAnnotation(URLMapping.class);
                if (annotation != null) routes.add(new URLRoute(annotation.value(), controller, method));
            }
        } catch (Exception e) {
            System.err.println("Erreur scan classe " + className + ": " + e.getMessage());
        }
        return routes;
    }
}
