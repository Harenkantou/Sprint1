package com.giga.spring.controller;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.GetUrl;
import com.giga.spring.annotation.Json;
import com.giga.spring.annotation.PostUrl;
import com.giga.spring.annotation.RequestParam;
import com.giga.spring.model.ModelView;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SampleController extends Controller {

    @PostUrl("/save")
    public ModelView save(Map<String, Object> formData) {
        ModelView mv = new ModelView("/result.jsp");
        mv.addObject("received", formData);
        return mv;
    }

    @PostUrl("/saveRaw")
    public String saveRaw(String name, String[] tags) {
        return "Saved name=" + name + " tagsCount=" + (tags==null?0:tags.length);
    }

    // --- Session CRUD operations pour développeur (accessible depuis les méthodes d'action)

    @GetUrl("/session/get")
    @Json
    public Object sessionGet(@RequestParam("key") String key, HttpServletRequest req) {
        Object value = req.getSession().getAttribute(key);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", key);
        resp.put("value", value);
        return resp;
    }

    @PostUrl("/session/add")
    @Json
    public Object sessionAdd(@RequestParam("key") String key, @RequestParam("value") String value,
                             HttpServletRequest req) {
        req.getSession().setAttribute(key, value);
        return Map.of("status", "ok", "action", "add", "key", key);
    }

    @PostUrl("/session/update")
    @Json
    public Object sessionUpdate(@RequestParam("key") String key, @RequestParam("value") String value,
                                HttpServletRequest req) {
        req.getSession().setAttribute(key, value);
        return Map.of("status", "ok", "action", "update", "key", key);
    }

    @PostUrl("/session/remove")
    @Json
    public Object sessionRemove(@RequestParam("key") String key, HttpServletRequest req) {
        req.getSession().removeAttribute(key);
        return Map.of("status", "ok", "action", "remove", "key", key);
    }
}
