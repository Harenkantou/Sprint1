package com.giga.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Authorized {
    /** Liste des rôles autorisés (au moins un doit correspondre) */
    String[] roles() default {};

    /** Si true: la méthode nécessite simplement un utilisateur authentifié */
    boolean authenticated() default false;

    /** Si true: autorise explicitement les appels anonymes (aucune authentification requise) */
    boolean anonymous() default false;
}
