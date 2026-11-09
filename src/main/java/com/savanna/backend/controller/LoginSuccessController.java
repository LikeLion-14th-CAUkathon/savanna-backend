package com.savanna.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginSuccessController {

    @GetMapping("/login-success")
    public String loginSuccess() {
        return "Google OAuth2 로그인 성공! Refresh Token 발급 완료";
    }
}
