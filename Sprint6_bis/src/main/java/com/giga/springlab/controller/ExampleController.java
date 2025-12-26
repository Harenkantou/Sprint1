package com.giga.springlab.controller;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.URLMapping;
import com.giga.spring.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class ExampleController {

    @URLMapping("/user/save")
    public String saveUser(HttpServletRequest req, HttpServletResponse res,
                           @RequestParam("name") String name,
                           @RequestParam("age") int age) {
        return "Saved user " + name + " (" + age + ")";
    }
}
