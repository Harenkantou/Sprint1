package com.giga.spring.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classe responsable du binding automatique d'objets depuis les paramètres HTTP
 */
public class ObjectBinder {
    
    private static final Pattern INDEXED_PATTERN = Pattern.compile("^(.*?)\\[(\\d+)\\](.*)$");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("^([^.]+)\\.(.+)$");
    
    /**
     * Crée et remplit un objet à partir des paramètres de requête
     */
    public static Object bindObject(Class<?> targetType, Map<String, String[]> parameterMap, String prefix) {
        if (targetType == null) {
            return null;
        }
        
        try {
            // Vérifier si c'est un tableau
            if (targetType.isArray()) {
                return bindArray(targetType.getComponentType(), parameterMap, prefix);
            }
            
            // Vérifier si c'est une Collection (List, Set)
            if (Collection.class.isAssignableFrom(targetType)) {
                return bindCollection(targetType, parameterMap, prefix);
            }
            
            // Vérifier si c'est une Map (déjà traité dans Sprint 8)
            if (Map.class.isAssignableFrom(targetType)) {
                return null; // Géré séparément
            }
            
            // Créer une instance de l'objet
            Object instance = createInstance(targetType);
            if (instance == null) {
                return null;
            }
            
            // Remplir les propriétés
            fillProperties(instance, parameterMap, prefix);
            
            return instance;
            
        } catch (Exception e) {
            System.err.println("Erreur lors du binding de " + targetType.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Crée et remplit un tableau d'objets
     */
    private static Object bindArray(Class<?> componentType, Map<String, String[]> parameterMap, String prefix) {
        // Déterminer la taille du tableau
        int maxIndex = findMaxIndex(parameterMap, prefix);
        if (maxIndex < 0) {
            return Array.newInstance(componentType, 0);
        }
        
        // Créer le tableau
        Object array = Array.newInstance(componentType, maxIndex + 1);
        
        // Remplir chaque élément
        for (int i = 0; i <= maxIndex; i++) {
            String elementPrefix = (prefix.isEmpty() ? "" : prefix + ".") + "[" + i + "]";
            Object element = bindObject(componentType, parameterMap, elementPrefix);
            Array.set(array, i, element);
        }
        
        return array;
    }
    
    /**
     * Crée et remplit une Collection
     */
    private static Collection<?> bindCollection(Class<?> collectionType, Map<String, String[]> parameterMap, String prefix) {
        // Déterminer la taille
        int maxIndex = findMaxIndex(parameterMap, prefix);
        if (maxIndex < 0) {
            return createEmptyCollection(collectionType);
        }
        
        // Créer la collection
        Collection<Object> collection = (Collection<Object>) createCollectionInstance(collectionType);
        
        // Remplir chaque élément
        for (int i = 0; i <= maxIndex; i++) {
            String elementPrefix = (prefix.isEmpty() ? "" : prefix + ".") + "[" + i + "]";
            // Pour les collections, on ne sait pas le type d'élément, on utilise Object
            Object element = bindObject(Object.class, parameterMap, elementPrefix);
            if (element != null) {
                collection.add(element);
            }
        }
        
        return collection;
    }
    
    /**
     * Trouve l'index maximum dans les paramètres
     */
    private static int findMaxIndex(Map<String, String[]> parameterMap, String prefix) {
        int maxIndex = -1;
        String prefixWithDot = prefix.isEmpty() ? "" : prefix + ".";
        
        for (String paramName : parameterMap.keySet()) {
            if (paramName.startsWith(prefixWithDot + "[") || 
                (prefix.isEmpty() && paramName.startsWith("["))) {
                
                // Extraire l'index
                Matcher matcher = INDEXED_PATTERN.matcher(
                    prefix.isEmpty() ? paramName : paramName.substring(prefix.length() + 1)
                );
                
                if (matcher.matches()) {
                    try {
                        int index = Integer.parseInt(matcher.group(2));
                        if (index > maxIndex) {
                            maxIndex = index;
                        }
                    } catch (NumberFormatException e) {
                        // Ignorer
                    }
                }
            }
        }
        
        return maxIndex;
    }
    
    /**
     * Remplit les propriétés d'un objet
     */
    private static void fillProperties(Object instance, Map<String, String[]> parameterMap, String prefix) {
        Class<?> clazz = instance.getClass();
        
        // Récupérer tous les champs (y compris hérités)
        List<Field> fields = getAllFields(clazz);
        
        for (Field field : fields) {
            // Vérifier l'accessibilité
            boolean accessible = field.canAccess(instance);
            if (!accessible) {
                field.setAccessible(true);
            }
            
            // Nom de la propriété dans les paramètres
            String propertyName = field.getName();
            String paramName = prefix.isEmpty() ? propertyName : prefix + "." + propertyName;
            
            // Vérifier si c'est un objet imbriqué
            Class<?> fieldType = field.getType();
            
            if (isSimpleType(fieldType)) {
                // Type simple - récupérer la valeur
                String[] values = findParameterValue(parameterMap, paramName);
                if (values != null && values.length > 0) {
                    try {
                        Object value = convertToType(values[0], fieldType);
                        field.set(instance, value);
                    } catch (Exception e) {
                        System.err.println("Erreur setting " + paramName + ": " + e.getMessage());
                    }
                }
            } else if (!fieldType.isArray() && !Collection.class.isAssignableFrom(fieldType)) {
                // Objet imbriqué - binding récursif
                Object nestedObject = bindObject(fieldType, parameterMap, paramName);
                if (nestedObject != null) {
                    try {
                        field.set(instance, nestedObject);
                    } catch (Exception e) {
                        System.err.println("Erreur setting nested object " + paramName + ": " + e.getMessage());
                    }
                }
            }
            // Les tableaux et collections sont traités différemment
            
            // Restaurer l'accessibilité
            if (!accessible) {
                field.setAccessible(false);
            }
        }
    }
    
    /**
     * Trouve la valeur d'un paramètre (gère les notations avec points)
     */
    private static String[] findParameterValue(Map<String, String[]> parameterMap, String paramName) {
        // Essayer le nom exact d'abord
        String[] values = parameterMap.get(paramName);
        if (values != null) {
            return values;
        }
        
        // Essayer avec différentes notations
        // Ex: "employee.name" pourrait être "employee.name" ou split
        for (String key : parameterMap.keySet()) {
            if (key.replace("_", ".").equals(paramName)) {
                return parameterMap.get(key);
            }
        }
        
        return null;
    }
    
    /**
     * Convertit une String en type cible
     */
    public static Object convertToType(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return getDefaultValue(targetType);
        }
        
        try {
            // String
            if (targetType == String.class) {
                return value;
            }
            
            // Primitives numériques
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(value);
            }
            if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(value);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(value);
            }
            
            // Boolean
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value);
            }
            
            // Character
            if (targetType == char.class || targetType == Character.class) {
                return value.length() > 0 ? value.charAt(0) : '\0';
            }
            
            // Date (format simple)
            if (targetType == Date.class) {
                try {
                    // Essayer différents formats
                    String[] formats = {"yyyy-MM-dd", "dd/MM/yyyy", "yyyy/MM/dd", "MM/dd/yyyy"};
                    for (String format : formats) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat(format);
                            sdf.setLenient(false);
                            return sdf.parse(value);
                        } catch (ParseException e) {
                            // Continuer avec le format suivant
                        }
                    }
                    // Si aucun format ne fonctionne, retourner null
                    return null;
                } catch (Exception e) {
                    return null;
                }
            }
            
            // java.time.LocalDate
            if (targetType.getName().equals("java.time.LocalDate")) {
                try {
                    return Class.forName("java.time.LocalDate")
                            .getMethod("parse", CharSequence.class)
                            .invoke(null, value);
                } catch (Exception e) {
                    return null;
                }
            }
            
            // java.time.LocalDateTime
            if (targetType.getName().equals("java.time.LocalDateTime")) {
                try {
                    return Class.forName("java.time.LocalDateTime")
                            .getMethod("parse", CharSequence.class)
                            .invoke(null, value);
                } catch (Exception e) {
                    return null;
                }
            }
            
            // Enum
            if (targetType.isEnum()) {
                try {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
                    return Enum.valueOf(enumType, value);
                } catch (IllegalArgumentException e) {
                    // Essayer avec ignoreCase
                    for (Object constant : targetType.getEnumConstants()) {
                        if (constant.toString().equalsIgnoreCase(value)) {
                            return constant;
                        }
                    }
                    return null;
                }
            }
            
            // BigDecimal
            if (targetType.getName().equals("java.math.BigDecimal")) {
                try {
                    return Class.forName("java.math.BigDecimal")
                            .getConstructor(String.class)
                            .newInstance(value);
                } catch (Exception e) {
                    return null;
                }
            }
            
            // BigInteger
            if (targetType.getName().equals("java.math.BigInteger")) {
                try {
                    return Class.forName("java.math.BigInteger")
                            .getConstructor(String.class)
                            .newInstance(value);
                } catch (Exception e) {
                    return null;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Erreur conversion " + value + " to " + targetType.getName() + ": " + e.getMessage());
            return getDefaultValue(targetType);
        }
        
        return value;
    }
    
    /**
     * Vérifie si un type est "simple" (pas besoin de binding récursif)
     */
    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Boolean.class ||
               type == Short.class ||
               type == Byte.class ||
               type == Character.class ||
               type == Date.class ||
               type.isEnum() ||
               type.getName().startsWith("java.time.") ||
               type.getName().startsWith("java.math.");
    }
    
    /**
     * Crée une instance d'une classe
     */
    private static Object createInstance(Class<?> clazz) {
        try {
            // Vérifier si c'est une interface ou classe abstraite
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                System.err.println("Cannot instantiate interface or abstract class: " + clazz.getName());
                return null;
            }
            
            // Chercher un constructeur par défaut
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
            
        } catch (NoSuchMethodException e) {
            System.err.println("No default constructor for " + clazz.getName());
            return null;
        } catch (Exception e) {
            System.err.println("Error creating instance of " + clazz.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Crée une instance de Collection
     */
    private static Collection<?> createCollectionInstance(Class<?> collectionType) {
        try {
            if (collectionType == List.class || collectionType == ArrayList.class) {
                return new ArrayList<>();
            }
            if (collectionType == Set.class || collectionType == HashSet.class) {
                return new HashSet<>();
            }
            if (collectionType == LinkedList.class) {
                return new LinkedList<>();
            }
            if (collectionType == Vector.class) {
                return new Vector<>();
            }
            
            // Par défaut, créer avec le constructeur par défaut
            return (Collection<?>) collectionType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Crée une Collection vide
     */
    private static Collection<?> createEmptyCollection(Class<?> collectionType) {
        Collection<?> collection = createCollectionInstance(collectionType);
        collection.clear();
        return collection;
    }
    
    /**
     * Récupère tous les champs d'une classe (y compris hérités)
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }
    
    /**
     * Retourne la valeur par défaut pour un type
     */
    private static Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}