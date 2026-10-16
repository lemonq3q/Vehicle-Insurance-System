package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import com.example.insurancesystem.saas.service.ProfileService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * 为门户个人中心读取和编辑当前账号的普通资料，与认证上下文和用户存储协作。
 * 邮箱身份及密码由共享邮件验证流程维护，此服务不得从普通编辑请求修改认证字段。
 */
@Service
public class ProfileServiceImpl implements ProfileService {
  private final PortalUserMapper mapper;
  private final PortalContextService context;

  /**
   * 注入既有用户存储和门户会话上下文，所有资料操作固定为当前登录用户。
   */
  public ProfileServiceImpl(PortalUserMapper mapper, PortalContextService context) {
    this.mapper = mapper;
    this.context = context;
  }

  /**
   * 返回本人可展示资料并转换为前端字段命名，不查询密码或认证凭据。
   */
  public Map<String, Object> get() {
    return PortalMaps.camel(mapper.findProfile(context.userId()));
  }

  /**
   * 普通资料只修改姓名、可选联系电话和证件号，邮箱身份必须通过验证码绑定。
   */
  public Map<String, Object> update(Map<String, Object> body) {
    Long id = context.userId();
    String phone = body.get("phone") == null ? null : body.get("phone").toString().trim();
    String realName = required(body, "realName");
    if (realName.length() > 100) throw new BusinessException(400, "姓名最多 100 字符");
    if (phone != null && phone.isBlank()) phone = null;
    if (phone != null && !phone.matches("^1\\d{10}$")) throw new BusinessException(400, "手机号格式错误");
    if (phone != null && mapper.countPhoneExcept(phone, id) > 0) throw new BusinessException(400, "手机号已被使用");
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", id);
    values.put("phone", phone);
    values.put("realName", realName);
    Object idNum = body.get("idNum");
    if (idNum != null && idNum.toString().length() > 100) throw new BusinessException(400, "证件号最多 100 字符");
    values.put("idNum", idNum);
    mapper.updateProfile(values);
    return get();
  }

  /**
   * 读取普通资料中的必填文本，空输入拒绝更新，非空内容清理首尾空白。
   */
  private String required(Map<String, Object> b, String k) {
    Object v = b.get(k);
    if (v == null || v.toString().isBlank()) throw new BusinessException(400, k + "不能为空");
    return v.toString().trim();
  }
}
