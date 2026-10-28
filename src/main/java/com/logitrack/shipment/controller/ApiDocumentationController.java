package com.logitrack.shipment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApiDocumentationController {

    @GetMapping({"/docs", "/docs/", "/swagger-ui/index.html"})
    public String documentation() {
        return "forward:/docs/index.html";
    }
}
