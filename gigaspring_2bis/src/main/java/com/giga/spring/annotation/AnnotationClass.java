package com.giga.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a Controller for this lightweight framework.
 *
 * Usage example:
 * @AnnotationClass
 * public class UserController extends Controller { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AnnotationClass {
    /**
     * Optional name for the controller (helpful for listing)
     */
    String value() default "";
}
