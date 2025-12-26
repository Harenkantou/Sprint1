package com.giga.springlab.controller;

import com.giga.spring.annotation.Controller;
import com.giga.spring.annotation.URLMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class DeptController {

    @URLMapping("/dept/{id}")
    public void get(HttpServletRequest req, HttpServletResponse res, int id) {
        try {
            res.getWriter().println("Dept id received: " + id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
