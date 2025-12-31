package com.giga.spring.model;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private final String view;
    private final Map<String, Object> model = new HashMap<>();

    public ModelView(String view) { this.view = view; }
    public String getView() { return view; }
    public Map<String, Object> getModel() { return model; }
    public void addAttribute(String name, Object value) { model.put(name, value); }
}
