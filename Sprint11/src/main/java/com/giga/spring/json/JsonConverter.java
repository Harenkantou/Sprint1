package com.giga.spring.json;

import com.giga.spring.model.ModelView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Classe utilitaire pour convertir des objets en JSON
 * Sprint 9 - Conversion JSON
 */
public class JsonConverter {
    
    private static final Gson gson;
    
    static {
        // Configuration de Gson avec des sérialiseurs personnalisés
        GsonBuilder builder = new GsonBuilder();
        
        // Sérialsateur pour Date
        builder.registerTypeAdapter(Date.class, new JsonSerializer<Date>() {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            
            @Override
            public JsonElement serialize(Date date, Type typeOfSrc, JsonSerializationContext context) {
                return new JsonPrimitive(dateFormat.format(date));
            }
        });
        
        // Pretty printing pour le développement
        builder.setPrettyPrinting();
        
        // Gérer les nulls
        builder.serializeNulls();
        
        gson = builder.create();
    }
    
    /**
     * Convertit un objet en JSON
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        try {
            return gson.toJson(obj);
        } catch (Exception e) {
            System.err.println("Erreur conversion JSON: " + e.getMessage());
            return "{\"error\": \"Failed to convert to JSON\"}";
        }
    }
    
    /**
     * Convertit un objet en réponse JSON standardisée
     */
    public static String toStandardJson(Object data) {
        JsonResponse response;
        
        if (data instanceof ModelView) {
            // Si c'est un ModelView, extraire les données du modèle
            ModelView mv = (ModelView) data;
            Map<String, Object> modelData = mv.getModel();
            
            // Si le modèle contient plusieurs données, on les retourne toutes
            // Sinon, extraire la première (ou unique) valeur
            if (modelData.size() == 1) {
                data = modelData.values().iterator().next();
            } else {
                data = modelData;
            }
        }
        
        // Déterminer le format de la réponse
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            response = JsonResponse.success(list, list.size());
        } else if (data instanceof Object[]) {
            Object[] array = (Object[]) data;
            response = JsonResponse.success(array, array.length);
        } else {
            response = JsonResponse.success(data);
        }
        
        return toJson(response);
    }
    
    /**
     * Convertit une exception en réponse JSON d'erreur
     */
    public static String errorToJson(Exception e, int statusCode) {
        JsonResponse response = new JsonResponse("error", statusCode, null, e.getMessage());
        return toJson(response);
    }
    
    /**
     * Vérifie si un objet est "sérialisable en JSON simple"
     * (pas besoin d'encapsulation dans JsonResponse)
     */
    public static boolean isSimpleJsonType(Object obj) {
        if (obj == null) return true;
        
        Class<?> clazz = obj.getClass();
        return clazz == String.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Double.class ||
               clazz == Float.class ||
               clazz == Boolean.class ||
               clazz == JsonResponse.class ||
               clazz.getName().startsWith("java.") ||
               clazz.isPrimitive();
    }
}