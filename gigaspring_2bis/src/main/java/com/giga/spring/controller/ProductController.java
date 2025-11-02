package com.giga.spring.controller;

import com.giga.spring.annotation.AnnotationClass;
import com.giga.spring.annotation.URLMapping;

/**
 * Exemple de controller annoté - Product endpoints
 */
@AnnotationClass("ProductController")
public class ProductController extends Controller {

    public ProductController() {
    }

    @URLMapping("/products")
    public void listProducts() {
        // méthode factice pour test du scanner
    }

    @URLMapping("/products/{category}/{id}")
    public void getProduct() {
        // méthode factice pour test du scanner
    }
}
