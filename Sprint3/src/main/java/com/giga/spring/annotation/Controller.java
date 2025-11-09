package com.giga.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour marquer une classe comme contrôleur
 * Les classes annotées avec @Controller seront automatiquement scannées
 * au démarrage de l'application pour enregistrer leurs routes.
 * 
 * Exemple d'utilisation:
 * @Controller
 * public class UserController extends com.giga.spring.controller.Controller {
 *     @URLMapping("/users")
 *     public void listUsers(HttpServletRequest req, HttpServletResponse res) { ... }
 * }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Controller {
    /**
     * Nom optionnel du contrôleur
     * Si non spécifié, le nom de la classe sera utilisé
     */
    String value() default "";
}
