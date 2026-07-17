package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.service.EnterpriseService;
import com.example.insurancesystem.saas.support.BusinessCodeGenerator;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.saas.support.SaasCodeConstraints;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnterpriseServiceImpl implements EnterpriseService {
  private final EnterpriseMapper mapper;
  private final FinanceMapper financeMapper;
  private final PortalContextService context;
  private final BusinessCodeGenerator codes;

  public EnterpriseServiceImpl(
      EnterpriseMapper mapper,
      FinanceMapper financeMapper,
      PortalContextService context,
      BusinessCodeGenerator codes) {
    this.mapper = mapper;
    this.financeMapper = financeMapper;
    this.context = context;
    this.codes = codes;
  }

  public Map<String, Object> current() {
    Map<String, Object> member = PortalMaps.camel(mapper.findCurrentMember(context.userId()));
    if (member == null) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("enterprise", null);
      empty.put("member", null);
      empty.put("wallet", null);
      empty.put("subscription", null);
      return empty;
    }
    Long enterpriseId = ((Number) member.get("enterpriseId")).longValue();
    Map<String, Object> subscription =
        PortalMaps.camel(financeMapper.findSubscription(enterpriseId));
    if (subscription != null)
      subscription.put(
          "plan",
          PortalMaps.camel(
              financeMapper.findPlan(((Number) subscription.get("planId")).longValue())));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("enterprise", PortalMaps.camel(mapper.findEnterprise(enterpriseId)));
    data.put("member", member);
    data.put("wallet", PortalMaps.camel(financeMapper.findWallet(enterpriseId)));
    data.put("subscription", subscription);
    return data;
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    if (mapper.findCurrentMember(context.userId()) != null)
      throw new BusinessException(400, "当前账号已加入企业");
    String name = required(body, "name"),
        contactName = required(body, "contactName"),
        contactPhone = required(body, "contactPhone");
    Map<String, Object> enterprise = new LinkedHashMap<>();
    enterprise.put("name", name);
    enterprise.put("contactName", contactName);
    enterprise.put("contactPhone", contactPhone);
    enterprise.put("ownerUserId", context.userId());
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.ENTERPRISE_CODE,
        codes::enterpriseCode,
        code -> enterprise.put("code", code),
        () -> mapper.insertEnterprise(enterprise));
    Long enterpriseId = ((Number) enterprise.get("id")).longValue();
    Map<String, Object> member =
        member(enterpriseId, context.userId(), "OWNER", 1, null, context.userId());
    mapper.insertMember(member);
    mapper.insertWallet(enterpriseId, context.userId());
    return PortalMaps.camel(mapper.findEnterprise(enterpriseId));
  }

  public Map<String, Object> update(Map<String, Object> body) {
    Map<String, Object> member = context.requireRoles("OWNER");
    Long enterpriseId = ((Number) member.get("enterpriseId")).longValue();
    Map<String, Object> enterprise = new LinkedHashMap<>();
    enterprise.put("id", enterpriseId);
    enterprise.put("name", required(body, "name"));
    enterprise.put("contactName", required(body, "contactName"));
    enterprise.put("contactPhone", required(body, "contactPhone"));
    enterprise.put("userId", context.userId());
    mapper.updateEnterprise(enterprise);
    return PortalMaps.camel(mapper.findEnterprise(enterpriseId));
  }

  @Transactional
  public Map<String, Object> join(Map<String, Object> body) {
    String code = required(body, "code");
    Map<String, Object> invite = PortalMaps.camel(mapper.findInviteByCode(code));
    if (invite == null) throw new BusinessException(404, "邀请码不存在");
    Integer status = ((Number) invite.get("status")).intValue();
    LocalDateTime expires = (LocalDateTime) invite.get("expiresAt");
    Number max = (Number) invite.get("maxUseCount"), used = (Number) invite.get("usedCount");
    if (status != 1
        || (expires != null && expires.isBefore(LocalDateTime.now()))
        || (max != null && used.intValue() >= max.intValue()))
      throw new BusinessException(400, "邀请码已失效");
    Long enterpriseId = ((Number) invite.get("enterpriseId")).longValue();
    Map<String, Object> existing = mapper.findMemberByUser(enterpriseId, context.userId());
    if (existing != null && PortalMaps.intValue(existing, "status", "status") != 3)
      throw new BusinessException(400, "已经是该企业成员");
    Map<String, Object> member =
        member(
            enterpriseId,
            context.userId(),
            "ISSUER",
            1,
            ((Number) invite.get("id")).longValue(),
            context.userId());
    if (mapper.reactivateMember(member) == 0) mapper.insertMember(member);
    if (mapper.consumeInvite(((Number) invite.get("id")).longValue()) == 0)
      throw new BusinessException(400, "邀请码已失效");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("enterpriseId", enterpriseId);
    result.put("roleCode", "ISSUER");
    return result;
  }

  public TableData<Map<String, Object>> invites(int pageNum, int pageSize) {
    Long enterpriseId =
        ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    pageNum = positive(pageNum, 1);
    pageSize = limit(pageSize);
    return new TableData<>(
        mapper.countInvites(enterpriseId),
        PortalMaps.camel(mapper.findInvites(enterpriseId, (pageNum - 1) * pageSize, pageSize)));
  }

  public Map<String, Object> createInvite(Map<String, Object> body) {
    Long enterpriseId =
        ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    Object expiresValue = body.get("expiresAt");
    if (expiresValue == null) throw new BusinessException(400, "过期时间不能为空");
    LocalDateTime expires = parseTime(expiresValue.toString());
    if (!expires.isAfter(LocalDateTime.now())) throw new BusinessException(400, "过期时间必须晚于当前时间");
    Integer max =
        body.get("maxUseCount") == null
            ? null
            : Integer.valueOf(body.get("maxUseCount").toString());
    if (max != null && max < 1) throw new BusinessException(400, "邀请人数必须大于 0");
    Map<String, Object> invite = new LinkedHashMap<>();
    invite.put("enterpriseId", enterpriseId);
    invite.put("maxUseCount", max);
    invite.put("expiresAt", expires);
    invite.put("userId", context.userId());
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.INVITE_CODE,
        codes::inviteCode,
        code -> invite.put("code", code),
        () -> mapper.insertInvite(invite));
    return PortalMaps.camel(mapper.findInviteByCode(String.valueOf(invite.get("code"))));
  }

  public boolean deleteInvite(Map<String, Object> body) {
    Long enterpriseId =
        ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    Long id = number(body, "id");
    if (mapper.deleteInvite(id, enterpriseId) == 0) throw new BusinessException(404, "邀请码不存在");
    return true;
  }

  public TableData<Map<String, Object>> members(
      int pageNum, int pageSize, String keyword, String roleCode, Integer status) {
    Long enterpriseId = context.enterpriseId();
    pageNum = positive(pageNum, 1);
    pageSize = limit(pageSize);
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("enterpriseId", enterpriseId);
    query.put("offset", (pageNum - 1) * pageSize);
    query.put("pageSize", pageSize);
    query.put("keyword", keyword);
    query.put("roleCode", roleCode);
    query.put("status", status);
    return new TableData<>(mapper.countMembers(query), PortalMaps.camel(mapper.findMembers(query)));
  }

  public Map<String, Object> updateRole(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long id = number(body, "memberId");
    String role = required(body, "roleCode");
    if (!Set.of("ADMIN", "ISSUER").contains(role)) throw new BusinessException(400, "成员角色参数不正确");
    Map<String, Object> target = PortalMaps.camel(mapper.findMember(id, enterpriseId));
    if (target == null) throw new BusinessException(404, "成员不存在");
    if ("OWNER".equals(target.get("roleCode"))) throw new BusinessException(400, "企业拥有者角色不能修改");
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", id);
    update.put("enterpriseId", enterpriseId);
    update.put("roleCode", role);
    update.put("userId", context.userId());
    mapper.updateMemberRole(update);
    return PortalMaps.camel(mapper.findMember(id, enterpriseId));
  }

  @Transactional
  public Map<String, Object> updateStatus(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long id = number(body, "memberId");
    int status = ((Number) body.getOrDefault("status", -1)).intValue();
    if (status != 0 && status != 1) throw new BusinessException(400, "成员状态参数不正确");
    Map<String, Object> target = PortalMaps.camel(mapper.findMember(id, enterpriseId));
    if (target == null) throw new BusinessException(404, "成员不存在");
    if ("OWNER".equals(target.get("roleCode")) && status == 0)
      throw new BusinessException(400, "企业拥有者不能被停用");
    if (status == 1 && ((Number) target.get("status")).intValue() != 1) {
      mapper.lockEnterprise(enterpriseId);
      Integer userLimit = mapper.findCurrentUserLimit(enterpriseId);
      int count = mapper.countActiveMembers(enterpriseId);
      if (userLimit != null && count >= userLimit)
        throw new BusinessException(409, "当前套餐最多启用 " + userLimit + " 名成员，请先升级套餐或停用其他成员");
    }
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", id);
    update.put("enterpriseId", enterpriseId);
    update.put("status", status);
    update.put("userId", context.userId());
    mapper.updateMemberStatus(update);
    return PortalMaps.camel(mapper.findMember(id, enterpriseId));
  }

  @Transactional
  public Map<String, Object> transfer(Map<String, Object> body) {
    Map<String, Object> owner = context.requireRoles("OWNER");
    Long enterpriseId = ((Number) owner.get("enterpriseId")).longValue();
    Long targetId = number(body, "toMemberId");
    Map<String, Object> target = PortalMaps.camel(mapper.findMember(targetId, enterpriseId));
    if (target == null || ((Number) target.get("status")).intValue() != 1)
      throw new BusinessException(400, "只能转让给同企业的启用成员");
    Long from = context.userId(), to = ((Number) target.get("userId")).longValue();
    if (from.equals(to)) throw new BusinessException(400, "不能转让给自己");
    Map<String, Object> transfer = new LinkedHashMap<>();
    transfer.put("enterpriseId", enterpriseId);
    transfer.put("fromUserId", from);
    transfer.put("toUserId", to);
    transfer.put("toMemberId", targetId);
    if (mapper.updateOwner(transfer) == 0) throw new BusinessException(409, "企业拥有者已发生变化，请刷新后重试");
    mapper.demoteOldOwner(transfer);
    mapper.promoteNewOwner(transfer);
    mapper.insertTransferLog(transfer);
    transfer.put("transferredAt", LocalDateTime.now());
    return transfer;
  }

  public TableData<Map<String, Object>> transferLogs(int pageNum, int pageSize) {
    Long enterpriseId = context.enterpriseId();
    pageNum = positive(pageNum, 1);
    pageSize = limit(pageSize);
    return new TableData<>(
        mapper.countTransferLogs(enterpriseId),
        PortalMaps.camel(
            mapper.findTransferLogs(enterpriseId, (pageNum - 1) * pageSize, pageSize)));
  }

  @Transactional
  public boolean exit() {
    Map<String, Object> member = context.currentMember();
    if ("OWNER".equals(member.get("roleCode"))) throw new BusinessException(400, "企业拥有者不可退出企业");
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", member.get("id"));
    update.put("enterpriseId", member.get("enterpriseId"));
    update.put("status", 3);
    update.put("userId", context.userId());
    mapper.updateMemberStatus(update);
    return true;
  }

  private Map<String, Object> member(
      Long enterpriseId, Long userId, String role, int status, Long inviteId, Long operator) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enterpriseId", enterpriseId);
    m.put("userId", userId);
    m.put("roleCode", role);
    m.put("status", status);
    m.put("inviteId", inviteId);
    m.put("operatorId", operator);
    return m;
  }

  private String required(Map<String, Object> b, String k) {
    Object v = b.get(k);
    if (v == null || v.toString().isBlank()) throw new BusinessException(400, k + "不能为空");
    return v.toString().trim();
  }

  private Long number(Map<String, Object> b, String k) {
    Object v = b.get(k);
    if (v == null) throw new BusinessException(400, k + "不能为空");
    return Long.valueOf(v.toString());
  }

  private int positive(int v, int d) {
    return v < 1 ? d : v;
  }

  private int limit(int v) {
    return v < 1 ? 10 : Math.min(v, 100);
  }

  private LocalDateTime parseTime(String value) {
    try {
      if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
        return LocalDate.parse(value).atTime(23, 59, 59);
      }
      return LocalDateTime.parse(value.replace(" ", "T"));
    } catch (Exception e) {
      throw new BusinessException(400, "时间格式不正确");
    }
  }
}
