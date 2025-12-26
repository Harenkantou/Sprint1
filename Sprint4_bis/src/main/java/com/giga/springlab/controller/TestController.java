package com.giga.springlab.controller;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.URLMapping;
import com.giga.spring.model.ModelView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class TestController {

    @URLMapping("/hello")
    public String hello(HttpServletRequest req, HttpServletResponse res) {
        return "Bonjour depuis TestController (String)";
    }

    @URLMapping("/mvtest")
    public ModelView mvtest(HttpServletRequest req, HttpServletResponse res) {
        ModelView mv = new ModelView("/test.jsp");
        mv.addAttribute("message", "Bonjour depuis TestController (ModelView)");
        return mv;
    }
}
