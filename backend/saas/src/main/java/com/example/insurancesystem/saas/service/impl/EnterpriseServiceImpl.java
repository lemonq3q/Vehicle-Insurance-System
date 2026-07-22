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
import java.math.BigDecimal;
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
    if (subscription != null && subscription.get("planId") != null)
      subscription.put(
          "plan",
          PortalMaps.camel(
              financeMapper.findPlan(((Number) subscription.get("planId")).longValue())));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("enterprise", PortalMaps.camel(mapper.findEnterprise(enterpriseId)));
    data.put("member", member);
    data.put("wallet", walletView(enterpriseId));
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
        member(enterpriseId, context.userId(), "OWNER", 0, null, context.userId());
    mapper.insertMember(member);
    mapper.insertWallet(enterpriseId, context.userId());
    mapper.insertDefaultSubscription(enterpriseId);
    insertMemberChange(
        enterpriseId, "JOIN", context.userId(), context.userId(), null, "OWNER", null, "创建企业并加入");
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
    mapper.lockEnterprise(enterpriseId);
    invite = PortalMaps.camel(mapper.lockInviteByCode(code));
    if (invite == null) throw new BusinessException(404, "邀请码不存在");
    status = ((Number) invite.get("status")).intValue();
    expires = (LocalDateTime) invite.get("expiresAt");
    max = (Number) invite.get("maxUseCount");
    used = (Number) invite.get("usedCount");
    if (status != 1
        || (expires != null && expires.isBefore(LocalDateTime.now()))
        || (max != null && used.intValue() >= max.intValue()))
      throw new BusinessException(400, "邀请码已失效");
    Map<String, Object> existing =
        PortalMaps.camel(mapper.findMemberByUserIncludingDeleted(enterpriseId, context.userId()));
    if (existing != null && PortalMaps.intValue(existing, "deleted", "deleted") == 0)
      throw new BusinessException(400, "已经是该企业成员");
    Integer configuredLimit = mapper.findCurrentUserLimit(enterpriseId);
    int userLimit = configuredLimit == null ? 0 : Math.max(0, configuredLimit);
    int memberStatus = mapper.countActiveMembers(enterpriseId) < userLimit ? 1 : 0;
    Map<String, Object> member =
        member(
            enterpriseId,
            context.userId(),
            "ISSUER",
            memberStatus,
            ((Number) invite.get("id")).longValue(),
            context.userId());
    if (mapper.reactivateMember(member) == 0) mapper.insertMember(member);
    if (mapper.consumeInvite(((Number) invite.get("id")).longValue()) == 0)
      throw new BusinessException(400, "邀请码已失效");
    insertMemberChange(
        enterpriseId,
        "JOIN",
        context.userId(),
        context.userId(),
        null,
        "ISSUER",
        ((Number) invite.get("id")).longValue(),
        "通过邀请码加入企业");
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

  @Transactional
  public Map<String, Object> updateRole(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long id = number(body, "memberId");
    String role = required(body, "roleCode");
    if (!Set.of("ADMIN", "ISSUER").contains(role)) throw new BusinessException(400, "成员角色参数不正确");
    mapper.lockEnterprise(enterpriseId);
    Map<String, Object> target = PortalMaps.camel(mapper.lockMember(id, enterpriseId));
    if (target == null) throw new BusinessException(404, "成员不存在");
    if ("OWNER".equals(target.get("roleCode"))) throw new BusinessException(400, "企业拥有者角色不能修改");
    String oldRole = String.valueOf(target.get("roleCode"));
    if (oldRole.equals(role)) return target;
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", id);
    update.put("enterpriseId", enterpriseId);
    update.put("roleCode", role);
    update.put("userId", context.userId());
    mapper.updateMemberRole(update);
    insertMemberChange(
        enterpriseId,
        "ROLE_CHANGE",
        context.userId(),
        ((Number) target.get("userId")).longValue(),
        oldRole,
        role,
        null,
        "修改企业成员角色");
    return PortalMaps.camel(mapper.findMember(id, enterpriseId));
  }

  @Transactional
  public Map<String, Object> updateStatus(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long id = number(body, "memberId");
    int status = ((Number) body.getOrDefault("status", -1)).intValue();
    if (status != 0 && status != 1) throw new BusinessException(400, "成员状态参数不正确");
    mapper.lockEnterprise(enterpriseId);
    Map<String, Object> target = PortalMaps.camel(mapper.lockMember(id, enterpriseId));
    if (target == null) throw new BusinessException(404, "成员不存在");
    if ("OWNER".equals(target.get("roleCode")) && status == 0)
      throw new BusinessException(400, "企业拥有者不能被停用");
    if (status == 1 && ((Number) target.get("status")).intValue() != 1) {
      Integer configuredLimit = mapper.findCurrentUserLimit(enterpriseId);
      int userLimit = configuredLimit == null ? 0 : Math.max(0, configuredLimit);
      int count = mapper.countActiveMembers(enterpriseId);
      if (userLimit == 0)
        throw new BusinessException(409, "企业当前未开通任何套餐，暂时无法启用成员");
      if (count >= userLimit)
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
  public boolean removeMember(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long memberId = number(body, "memberId");
    mapper.lockEnterprise(enterpriseId);
    Map<String, Object> target = PortalMaps.camel(mapper.lockMember(memberId, enterpriseId));
    if (target == null) throw new BusinessException(404, "企业成员不存在");
    if ("OWNER".equals(target.get("roleCode")))
      throw new BusinessException(400, "企业拥有者不能被移出企业");
    if (context.userId().equals(((Number) target.get("userId")).longValue()))
      throw new BusinessException(400, "不能将自己踢出企业，请使用退出企业功能");

    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", memberId);
    update.put("enterpriseId", enterpriseId);
    update.put("userId", context.userId());
    if (mapper.softDeleteMember(update) == 0)
      throw new BusinessException(409, "成员状态已发生变化，请刷新后重试");
    insertMemberChange(
        enterpriseId,
        "KICK",
        context.userId(),
        ((Number) target.get("userId")).longValue(),
        String.valueOf(target.get("roleCode")),
        null,
        null,
        "移出企业成员");
    return true;
  }

  @Transactional
  public Map<String, Object> transfer(Map<String, Object> body) {
    Map<String, Object> owner = context.requireRoles("OWNER");
    Long enterpriseId = ((Number) owner.get("enterpriseId")).longValue();
    Long targetId = number(body, "toMemberId");
    mapper.lockEnterprise(enterpriseId);
    Map<String, Object> target = PortalMaps.camel(mapper.lockMember(targetId, enterpriseId));
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
    insertMemberChange(
        enterpriseId,
        "OWNER_TRANSFER",
        from,
        to,
        String.valueOf(target.get("roleCode")),
        "OWNER",
        null,
        "门户主动转让企业拥有者");
    transfer.put("transferredAt", LocalDateTime.now());
    return transfer;
  }

  public TableData<Map<String, Object>> memberChangeLogs(
      int pageNum, int pageSize, String eventType) {
    Long enterpriseId = context.enterpriseId();
    pageNum = positive(pageNum, 1);
    pageSize = limit(pageSize);
    return new TableData<>(
        mapper.countMemberChangeLogs(enterpriseId, eventType),
        PortalMaps.camel(
            mapper.findMemberChangeLogs(
                enterpriseId, eventType, (pageNum - 1) * pageSize, pageSize)));
  }

  @Transactional
  public boolean exit() {
    Map<String, Object> member = context.currentMember();
    Long enterpriseId = ((Number) member.get("enterpriseId")).longValue();
    Long memberId = ((Number) member.get("id")).longValue();
    mapper.lockEnterprise(enterpriseId);
    member = PortalMaps.camel(mapper.lockMember(memberId, enterpriseId));
    if (member == null) throw new BusinessException(404, "企业成员不存在");
    if ("OWNER".equals(member.get("roleCode")))
      throw new BusinessException(400, "企业拥有者不可退出企业");
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", memberId);
    update.put("enterpriseId", enterpriseId);
    update.put("userId", context.userId());
    if (mapper.softDeleteMember(update) == 0)
      throw new BusinessException(409, "成员关系已发生变化，请刷新后重试");
    insertMemberChange(
        enterpriseId,
        "EXIT",
        context.userId(),
        context.userId(),
        String.valueOf(member.get("roleCode")),
        null,
        null,
        "成员主动退出企业");
    return true;
  }

  private void insertMemberChange(
      Long enterpriseId,
      String eventType,
      Long operatorUserId,
      Long targetUserId,
      String beforeRoleCode,
      String afterRoleCode,
      Long inviteId,
      String remark) {
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("enterpriseId", enterpriseId);
    change.put("eventType", eventType);
    change.put("operatorUserId", operatorUserId);
    change.put("targetUserId", targetUserId);
    change.put("beforeRoleCode", beforeRoleCode);
    change.put("afterRoleCode", afterRoleCode);
    change.put("inviteId", inviteId);
    change.put("remark", remark);
    mapper.insertMemberChangeLog(change);
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

  private Map<String, Object> walletView(Long enterpriseId) {
    Map<String, Object> wallet = PortalMaps.camel(financeMapper.findWallet(enterpriseId));
    if (wallet != null) return wallet;

    Map<String, Object> emptyWallet = new LinkedHashMap<>();
    emptyWallet.put("balanceAmount", BigDecimal.ZERO.setScale(2));
    emptyWallet.put("currency", "CNY");
    return emptyWallet;
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
