package com.giga.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe pour transporter les données vers la vue
 * Permet de passer des attributs à HttpServletRequest
 */
public class ModelView {
    private String url;  // URL de redirection ou nom de la vue
    private Map<String, Object> data;  // Données à passer à la vue

    public ModelView() {
        this.data = new HashMap<>();
    }

    public ModelView(String url) {
        this();
        this.url = url;
    }

    // Getters et setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    // Méthodes utilitaires pour ajouter des données
    public void addObject(String key, Object value) {
        this.data.put(key, value);
    }

    public Object getObject(String key) {
        return this.data.get(key);
    }

    public void removeObject(String key) {
        this.data.remove(key);
    }

    public boolean hasObject(String key) {
        return this.data.containsKey(key);
    }

    @Override
    public String toString() {
        return "ModelView{url='" + url + "', data=" + data + "}";
    }
}
