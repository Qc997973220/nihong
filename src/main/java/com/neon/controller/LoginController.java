package com.neon.controller;

import com.neon.pojo.Users;
import com.neon.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/login")
public class LoginController {
    @Autowired
    LoginService loginService;
    @GetMapping("/first")
    @ResponseBody
    public int first(@RequestParam String userName, @RequestParam String password){
        return loginService.login(userName,password);
    }

    @PostMapping("/registered")
    @ResponseBody
    public int registered(Users users){
        return loginService.registered(users);
    }
}
