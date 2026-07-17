package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import com.example.insurancesystem.saas.service.AccountService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {
  private final PortalContextService context;
  private final PortalUserMapper users;
  private final EnterpriseMapper enterprises;

  public AccountServiceImpl(
      PortalContextService context, PortalUserMapper users, EnterpriseMapper enterprises) {
    this.context = context;
    this.users = users;
    this.enterprises = enterprises;
  }

  @Override
  public Map<String, Object> currentContext() {
    Long userId = context.userId();
    List<Map<String, Object>> enterpriseList = PortalMaps.camel(users.findEnterprises(userId));
    Map<String, Object> member = PortalMaps.camel(enterprises.findCurrentMember(userId));
    Long enterpriseId = member == null ? null : ((Number) member.get("enterpriseId")).longValue();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("user", PortalMaps.camel(users.findProfile(userId)));
    data.put("enterprises", enterpriseList);
    data.put("currentEnterpriseId", enterpriseId);
    data.put(
        "currentEnterprise",
        enterpriseId == null ? null : PortalMaps.camel(enterprises.findEnterprise(enterpriseId)));
    data.put("currentMember", member);
    return data;
  }
}
