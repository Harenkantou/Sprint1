package com.giga.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour mapper une URL à une méthode de contrôleur
 * 
 * Exemple d'utilisation:
 * @URLMapping("/users")
 * public void listUsers(HttpServletRequest req, HttpServletResponse res) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface URLMapping {
    /**
     * Le pattern d'URL à mapper
     * Peut contenir des paramètres dynamiques avec {nom}
     * 
     * Exemples:
     * - "/users"
     * - "/users/{id}"
     * - "/products/{category}/{id}"
     */
    String value();
}
