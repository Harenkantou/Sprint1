package com.giga.spring.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.giga.spring.annotation.Url;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This is the servlet that takes all incoming requests targeting the app - If
 * the requested resource exists, it delegates to the default dispatcher - else
 * it shows the requested URL
 */
public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;

    private final Map<String, MethodBinding> routeTable = new HashMap<>();

    private static class MethodBinding {
        final Class<?> clazz;
        final Method method;
        final String httpMethod;
        final String path;

        MethodBinding(Class<?> clazz, Method method, String httpMethod, String path) {
            this.clazz = clazz;
            this.method = method;
            this.httpMethod = httpMethod;
            this.path = path;
        }
    }

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        String basePackage = getServletConfig().getInitParameter("basePackage");
        if (basePackage == null || basePackage.isBlank()) {
            basePackage = "com.giga"; // défaut raisonnable pour ce projet
        }
        scanAndRegisterRoutes(basePackage);
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
            defaultServe(req, res);
        } else {
            customServe(req, res);
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod().toUpperCase();
        String key = httpMethod + " " + path;

        MethodBinding binding = routeTable.get(key);
        if (binding != null) {
            try (PrintWriter out = res.getWriter()) {
                Object controller = binding.clazz.getDeclaredConstructor().newInstance();
                Object result = binding.method.invoke(controller);
                if (result != null) {
                    res.setContentType("text/plain;charset=UTF-8");
                    out.print(String.valueOf(result));
                } else {
                    res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            } catch (Exception e) {
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter out = res.getWriter()) {
                    out.printf("Handler error for %s: %s", key, e.getMessage());
                }
            }
            return;
        }

        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = """
                <html>
                    <head><title>Resource Not Found</title></head>
                    <body>
                        <h1>Unknown resource</h1>
                        <p>The requested URL was not found: <strong>%s</strong></p>
                    </body>
                </html>
                """.formatted(uri);

            res.setContentType("text/html;charset=UTF-8");
            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

    private void scanAndRegisterRoutes(String basePackage) {
        try {
            for (Class<?> clazz : getClasses(basePackage)) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(Url.class)) {
                        Url ann = m.getAnnotation(Url.class);
                        String http = (ann.method() == null || ann.method().isBlank()) ? "GET" : ann.method().toUpperCase();
                        String p = normalizePath(ann.value());
                        String key = http + " " + p;
                        routeTable.put(key, new MethodBinding(clazz, m, http, p));
                    }
                }
            }
        } catch (Exception e) {
            // En cas d'erreur de scan, on laisse la table vide et on continuera à répondre 404
        }
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) return "/";
        if (!p.startsWith("/")) return "/" + p;
        return p;
    }

    private static List<Class<?>> getClasses(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                dirs.add(new File(resource.getFile()));
            }
        }
        List<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        if (files == null) return classes;
        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().contains(".")) continue;
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Class.forName(className));
                } catch (Throwable ignored) {
                    // ignorer classes non chargeables en test
                }
            }
        }
        return classes;
    }

}
