package com.giga.spring.mapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Représente une route URL avec son contrôleur et sa méthode associée
 */
public class URLRoute {
    private final String urlPattern;
    private final Object controller;
    private final Method method;
    private final Pattern regex;
    private final String[] paramNames;
 
    public URLRoute(String urlPattern, Object controller, Method method) {
        this.urlPattern = urlPattern;
        this.controller = controller;
        this.method = method;
        
        // Convertir le pattern URL en regex
        // Exemple: /users/{id} -> /users/([^/]+)
        String regexPattern = urlPattern;
        java.util.List<String> params = new java.util.ArrayList<>();
        
        Pattern paramPattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = paramPattern.matcher(urlPattern);
        
        while (matcher.find()) {
            params.add(matcher.group(1));
            regexPattern = regexPattern.replace("{" + matcher.group(1) + "}", "([^/]+)");
        }
        
        this.paramNames = params.toArray(new String[0]);
        this.regex = Pattern.compile("^" + regexPattern + "$");
    }

    /**
     * Vérifie si l'URL correspond à ce pattern
     */
    public boolean matches(String url) {
        return regex.matcher(url).matches();
    }

    /**
     * Extrait les paramètres de l'URL
     * Exemple: pattern="/users/{id}", url="/users/123" -> {"id": "123"}
     */
    public Map<String, String> extractParams(String url) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = regex.matcher(url);
        
        if (matcher.matches()) {
            for (int i = 0; i < paramNames.length; i++) {
                params.put(paramNames[i], matcher.group(i + 1));
            }
        }
        
        return params;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public Object getController() {
        return controller;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return "URLRoute{" +
                "pattern='" + urlPattern + '\'' +
                ", controller=" + controller.getClass().getSimpleName() +
                ", method=" + method.getName() +
                '}';
    }
}
