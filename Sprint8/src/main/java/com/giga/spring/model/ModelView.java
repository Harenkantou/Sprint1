package com.giga.spring.model;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private String view;
    private Map<String, Object> model = new HashMap<>();

    public ModelView() {}
    public ModelView(String view) { this.view = view; }

    public String getView() { return view; }
    public void setView(String view) { this.view = view; }

    public Map<String, Object> getModel() { return model; }
    public void addObject(String key, Object value) { model.put(key, value); }
}
