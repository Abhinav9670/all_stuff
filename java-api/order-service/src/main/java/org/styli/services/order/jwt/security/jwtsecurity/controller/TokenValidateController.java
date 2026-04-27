package org.styli.services.order.jwt.security.jwtsecurity.controller;

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

  // @GetMapping("/customer/hello")
  // public String hello2() {
  // return "Customer Hello World WITHOUT TOKEN";
  // }
  //
  // @GetMapping("/auth/customer/hello")
  // public String hello3() {
  // return "Hello World WITH AUTH TOKEN";
  // }
}
