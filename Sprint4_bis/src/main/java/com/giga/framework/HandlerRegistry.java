package com.giga.framework;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerRegistry {
    public static class Handler {
        public final Object instance;
        public final Method method;

        public Handler(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
    }

    private static final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public static void register(String url, Object instance, Method method) {
        handlers.put(url, new Handler(instance, method));
    }

    public static Handler get(String url) {
        return handlers.get(url);
    }
}
