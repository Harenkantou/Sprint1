package com.giga.spring.model;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private String view;  // Au lieu de url
    private Map<String, Object> model;  // Pour stocker les attributs

    public ModelView() {
        this.model = new HashMap<>();
    }

    public ModelView(String view) {
        this();
        this.view = view;
    }

    // Getters et setters
    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Map<String, Object> getModel() {
        return model;
    }

    // Méthode utilitaire pour ajouter des données
    public void addAttribute(String key, Object value) {
        this.model.put(key, value);
    }
}