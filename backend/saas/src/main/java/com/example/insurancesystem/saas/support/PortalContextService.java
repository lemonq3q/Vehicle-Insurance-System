package com.example.insurancesystem.saas.support;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.utils.SystemCommonUtil;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PortalContextService {
  private final EnterpriseMapper enterpriseMapper;

  public PortalContextService(EnterpriseMapper enterpriseMapper) {
    this.enterpriseMapper = enterpriseMapper;
  }

  public Long userId() {
    return SystemCommonUtil.getNowUserId();
  }

  public Map<String, Object> currentMember() {
    Map<String, Object> member = PortalMaps.camel(enterpriseMapper.findCurrentMember(userId()));
    if (member == null) throw new BusinessException(400, "当前账号尚未加入企业");
    return member;
  }

  public Long enterpriseId() {
    return ((Number) currentMember().get("enterpriseId")).longValue();
  }

  public Map<String, Object> requireRoles(String... roles) {
    Map<String, Object> member = currentMember();
    String role = String.valueOf(member.get("roleCode"));
    if (Arrays.stream(roles).noneMatch(role::equals)) throw new BusinessException(403, "无操作权限");
    if (((Number) member.get("status")).intValue() != 1)
      throw new BusinessException(403, "当前企业成员未启用");
    return member;
  }
}
