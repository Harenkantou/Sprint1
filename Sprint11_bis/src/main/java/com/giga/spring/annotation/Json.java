package com.giga.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour indiquer qu'une méthode doit retourner du JSON
 * au lieu de rediriger vers une vue.
 * Sprint 9 - Support API REST
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Json {
    // Optionnel: nom personnalisé pour la propriété "data" dans la réponse JSON
    String value() default "data";
}