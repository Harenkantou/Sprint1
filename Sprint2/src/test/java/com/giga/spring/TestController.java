package com.giga.spring;

import com.giga.spring.annotation.Url;

public class TestController {
    @Url("/t1")
    public String t1() { return "ok t1"; }

    @Url(value="/t2", method="POST")
    public String t2() { return "ok t2"; }
}