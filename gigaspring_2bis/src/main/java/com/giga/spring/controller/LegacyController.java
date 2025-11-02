package com.giga.spring.controller;

import com.giga.spring.annotation.URLMapping;

/**
 * Exemple de controller NON annoté. Le scanner doit l'ignorer car il
 * n'a pas l'annotation de classe @AnnotationClass.
 */
public class LegacyController extends Controller {

    public LegacyController() {
    }

    @URLMapping("/legacy")
    public void legacyEndpoint() {
        // Même si les méthodes sont annotées, la classe n'est pas marquée
        // avec @AnnotationClass et doit donc être ignorée par le scanner.
    }
}
