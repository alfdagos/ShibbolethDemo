package com.example.demo.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserController {

    @GetMapping("/user")
    public Map<String, String> getUser(
            @RequestHeader(value = "uid", required = false) String uid,
            @RequestHeader(value = "mail", required = false) String mail,
            @RequestHeader(value = "cn", required = false) String cn) {
        
        Map<String, String> user = new HashMap<>();
        user.put("uid", uid != null ? uid : "N/A");
        user.put("mail", mail != null ? mail : "N/A");
        user.put("cn", cn != null ? cn : "N/A");
        user.put("message", "Hello from Spring Boot Backend!");
        
        return user;
    }
}
