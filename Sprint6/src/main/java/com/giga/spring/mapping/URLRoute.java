package com.giga.spring.mapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        StringBuilder regexPattern = new StringBuilder();
        java.util.List<String> params = new java.util.ArrayList<>();

        String[] parts = urlPattern.split("/");
        for (String part : parts) {
            if (!part.isEmpty()) {
                regexPattern.append("/");
                if (part.startsWith("{") && part.endsWith("}")) {
                    String paramName = part.substring(1, part.length() - 1);
                    params.add(paramName);
                    regexPattern.append("([^/]+)");
                } else {
                    regexPattern.append(Pattern.quote(part));
                }
            }
        }

        this.paramNames = params.toArray(new String[0]);
        this.regex = Pattern.compile("^" + regexPattern.toString() + "$");
    }

    public boolean matches(String url) {
        return regex.matcher(url).matches();
    }

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

    public String getUrlPattern() { return urlPattern; }
    public Object getController() { return controller; }
    public Method getMethod() { return method; }
}
