package com.giga.spring.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
public @interface Url {
    String value();          // ex: "/users"
    String method() default "GET"; // optionnel: "GET", "POST", etc.
}