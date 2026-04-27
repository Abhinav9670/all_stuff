package org.styli.services.customer.jwt.security.jwtsecurity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("rest")
public class TokenValidateController {

    @GetMapping("/auth/token/validate")
    public String hello() {

        return "SUCCESS";
    }
}
