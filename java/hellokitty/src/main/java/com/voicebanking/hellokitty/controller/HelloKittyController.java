package com.voicebanking.hellokitty.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HelloKittyController {

    @GetMapping("/hello")
    public Map<String, String> hello(@RequestParam(value = "name", required = false) String name) {
        if (name == null || name.trim().isEmpty()) {
            return Map.of("message", "");
        } else if ("hi".equalsIgnoreCase(name)) {
            return Map.of("message", "hello kitty");
        } else {
            return Map.of("message", "hello " + name);
        }
    }
}
