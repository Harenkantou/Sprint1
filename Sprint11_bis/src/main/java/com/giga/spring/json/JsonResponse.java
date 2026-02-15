package com.giga.spring.json;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe pour représenter une réponse JSON standardisée
 * Sprint 9 - Format standard des réponses API
 */
public class JsonResponse {
    private String status;
    private int code;
    private Object data;
    private String message;
    private Map<String, Object> meta;
    
    // Constructeurs
    public JsonResponse() {
        this.meta = new HashMap<>();
    }
    
    public JsonResponse(String status, int code, Object data) {
        this();
        this.status = status;
        this.code = code;
        this.data = data;
    }
    
    public JsonResponse(String status, int code, Object data, String message) {
        this(status, code, data);
        this.message = message;
    }
    
    // Getters et Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    
    public void addMeta(String key, Object value) {
        this.meta.put(key, value);
    }
    
    // Méthodes factory statiques pour des réponses courantes
    public static JsonResponse success(Object data) {
        return new JsonResponse("success", 200, data);
    }
    
    public static JsonResponse success(Object data, String message) {
        return new JsonResponse("success", 200, data, message);
    }
    
    public static JsonResponse success(Object data, int count) {
        JsonResponse response = new JsonResponse("success", 200, data);
        response.addMeta("count", count);
        return response;
    }
    
    public static JsonResponse error(String message) {
        return new JsonResponse("error", 500, null, message);
    }
    
    public static JsonResponse error(String message, int code) {
        return new JsonResponse("error", code, null, message);
    }
    
    public static JsonResponse notFound() {
        return new JsonResponse("error", 404, null, "Resource not found");
    }
    
    public static JsonResponse badRequest(String message) {
        return new JsonResponse("error", 400, null, message);
    }
}