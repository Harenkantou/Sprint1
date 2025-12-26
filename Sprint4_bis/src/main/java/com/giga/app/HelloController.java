package com.giga.app;

import com.giga.framework.HandlerRegistry;
import com.giga.framework.ModelView;
import com.giga.framework.Url;

import java.lang.reflect.Method;

public class HelloController {

    static {
        try {
            Method m1 = HelloController.class.getMethod("sayHello", javax.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletResponse.class);
            HandlerRegistry.register("/hello", new HelloController(), m1);

            Method m2 = HelloController.class.getMethod("viewTest", javax.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletResponse.class);
            HandlerRegistry.register("/viewTest", new HelloController(), m2);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Url("/hello")
    public String sayHello(javax.servlet.http.HttpServletRequest req, javax.servlet.http.HttpServletResponse resp) {
        return "Bonjour depuis HelloController!";
    }

    @Url("/viewTest")
    public ModelView viewTest(javax.servlet.http.HttpServletRequest req, javax.servlet.http.HttpServletResponse resp) {
        ModelView mv = new ModelView("/test.jsp");
        mv.addAttribute("message", "Message depuis ModelView");
        return mv;
    }
}
