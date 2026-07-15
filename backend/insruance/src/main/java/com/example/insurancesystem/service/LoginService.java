package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;

public interface LoginService {
    ResponseResult login(User user);

    ResponseResult logout();

    ResponseResult getEmailCode(String email);

    ResponseResult forgetPassword(String email, String code, String password);
}
