package com.giga.spring.controller;

import com.giga.spring.annotation.AnnotationClass;
import com.giga.spring.annotation.URLMapping;

/**
 * Exemple de controller annoté - User endpoints
 */
@AnnotationClass("UserController")
public class UserController extends Controller {

    public UserController() {
    }

    @URLMapping("/users")
    public void listUsers() {
        // méthode factice pour test du scanner
    }

    @URLMapping("/users/{id}")
    public void getUser() {
        // méthode factice pour test du scanner
    }
}
