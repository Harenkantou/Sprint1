package com.giga.spring.controller;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.PostUrl;
import com.giga.spring.model.ModelView;

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
}
