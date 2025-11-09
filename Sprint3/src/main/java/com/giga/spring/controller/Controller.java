package com.giga.spring.controller;

/**
 * Classe de base pour tous les contrôleurs
 * Les contrôleurs doivent hériter de cette classe pour être détectés par le framework
 */
public abstract class Controller {
    
    /**
     * Méthode appelée lors de l'initialisation du contrôleur
     * Peut être surchargée pour initialiser des ressources
     */
    public void init() {
        // Par défaut, ne fait rien
    }
    
    /**
     * Méthode appelée lors de la destruction du contrôleur
     * Peut être surchargée pour libérer des ressources
     */
    public void destroy() {
        // Par défaut, ne fait rien
    }
}
