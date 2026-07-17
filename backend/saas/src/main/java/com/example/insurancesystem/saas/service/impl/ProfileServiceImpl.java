package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import com.example.insurancesystem.saas.service.ProfileService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {
  private final PortalUserMapper mapper;
  private final PortalContextService context;

  public ProfileServiceImpl(PortalUserMapper mapper, PortalContextService context) {
    this.mapper = mapper;
    this.context = context;
  }

  public Map<String, Object> get() {
    return PortalMaps.camel(mapper.findProfile(context.userId()));
  }

  public Map<String, Object> update(Map<String, Object> body) {
    Long id = context.userId();
    String username = required(body, "username"),
        phone = required(body, "phone"),
        realName = required(body, "realName");
    if (!phone.matches("^1\\d{10}$")) throw new BusinessException(400, "手机号格式错误");
    if (mapper.countPhoneExcept(phone, id) > 0) throw new BusinessException(400, "手机号已被使用");
    if (mapper.countUsernameExcept(username, id) > 0) throw new BusinessException(400, "登录账号已被使用");
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", id);
    values.put("username", username);
    values.put("phone", phone);
    values.put("realName", realName);
    values.put("idNum", body.get("idNum"));
    mapper.updateProfile(values);
    return get();
  }

  private String required(Map<String, Object> b, String k) {
    Object v = b.get(k);
    if (v == null || v.toString().isBlank()) throw new BusinessException(400, k + "不能为空");
    return v.toString().trim();
  }
}
