package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.service.LoginService;
import com.example.insurancesystem.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseResult login(@RequestBody User user){
        return loginService.login(user);
    }

    @PostMapping("/register")
    public ResponseResult register(@RequestBody User user){
        return userService.registerPersonal(user);
    }

    @GetMapping("/logout")
    public ResponseResult logout(){
        return loginService.logout();
    }

    @GetMapping("/code")
    public ResponseResult getCode(String email){
        return loginService.getEmailCode(email);
    }

    @GetMapping("/forget")
    public ResponseResult forgetPassword(String email, String code, String password){
        return loginService.forgetPassword(email, code, password);
    }

}
