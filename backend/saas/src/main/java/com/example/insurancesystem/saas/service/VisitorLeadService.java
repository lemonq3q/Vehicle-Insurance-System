package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.VisitorLeadMapper;
import com.example.insurancesystem.saas.support.BusinessCodeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 官网游客线索业务服务，负责表单字段标准化、枚举约束、合作诉求 JSON 序列化和公开编号生成。
 * 服务不接收或保存客户端声明的来源，来源可信度和频率限制由控制器调用保护组件先行处理。
 */
@Service
public class VisitorLeadService {
    private static final Set<String> ROLES = Set.of("OPC_AGENT", "CAR_DEALER", "INSURANCE_AGENCY", "OTHER");
    private static final Set<String> INTENTS = Set.of("TRIAL", "DEMO", "CUSTOM_COOPERATION");
    private final VisitorLeadMapper mapper;
    private final BusinessCodeGenerator codeGenerator;
    private final ObjectMapper objectMapper;

    public VisitorLeadService(VisitorLeadMapper mapper, BusinessCodeGenerator codeGenerator, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.codeGenerator = codeGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验并写入一条游客提交。合作诉求必须为一至三项且去重；编号唯一键冲突最多重新生成三次。
     * 成功只返回后续二维码需要的 leadNo，不向匿名调用方暴露数据库主键或其他游客数据。
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        if (body == null) throw new BusinessException(400, "提交内容不能为空");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", required(body.get("name"), "姓名", 100));
        row.put("contact", required(body.get("contact"), "联系方式", 100));
        String role = optionalCode(body.get("roleCode"), 32);
        if (role != null && !ROLES.contains(role)) throw new BusinessException(400, "角色选择无效");
        row.put("roleCode", role);
        row.put("expectedMonthlyOrders", optionalInteger(body.get("expectedMonthlyOrders"), "预计月单量", 0, 100000000));
        List<String> intents = intents(body.get("intentCodes"));
        try { row.put("intentCodesJson", intents.isEmpty() ? null : objectMapper.writeValueAsString(intents)); }
        catch (Exception exception) { throw new BusinessException(400, "合作诉求格式无效"); }
        row.put("remark", nullable(body.get("remark"), 1000));
        for (int attempt = 0; attempt < 3; attempt++) {
            String leadNo = codeGenerator.visitorLeadNo(); row.put("leadNo", leadNo);
            try { mapper.insert(row); return Map.of("leadNo", leadNo); }
            catch (DuplicateKeyException conflict) { if (attempt == 2) throw new BusinessException(500, "游客编号生成失败，请稍后重试"); }
        }
        throw new BusinessException(500, "游客编号生成失败，请稍后重试");
    }

    /** 将可选请求数组转换为保持选择顺序的唯一编码列表，并拒绝未知合作诉求。 */
    private List<String> intents(Object value) {
        if (value == null || "".equals(value)) return List.of();
        if (!(value instanceof List)) throw new BusinessException(400, "合作诉求格式无效");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            String code = String.valueOf(item).trim().toUpperCase();
            if (!INTENTS.contains(code)) throw new BusinessException(400, "合作诉求选择无效");
            unique.add(code);
        }
        return new ArrayList<>(unique);
    }

    /** 读取并限制必填文本，避免空白字符串和超长内容进入数据库。 */
    private String required(Object value, String label, int max) { String text = value == null ? "" : String.valueOf(value).trim(); if (text.isBlank()) throw new BusinessException(400, "请填写" + label); if (text.length() > max) throw new BusinessException(400, label + "不能超过" + max + "个字符"); return text; }
    /** 读取可选文本，空白统一存为 null，非空值遵守数据库长度上限。 */
    private String nullable(Object value, int max) { String text = value == null ? "" : String.valueOf(value).trim(); if (text.isBlank()) return null; if (text.length() > max) throw new BusinessException(400, "备注不能超过" + max + "个字符"); return text; }
    /** 标准化可选枚举编码，空白值存为 null，非空值限制数据库字段长度。 */
    private String optionalCode(Object value, int max) { String text = value == null ? "" : String.valueOf(value).trim().toUpperCase(); if (text.isBlank()) return null; if (text.length() > max) throw new BusinessException(400, "角色选择无效"); return text; }
    /** 可选数值为空时返回 null，填写后必须是指定闭区间内整数。 */
    private Integer optionalInteger(Object value, String label, int min, int max) { if (value == null || String.valueOf(value).trim().isBlank()) return null; try { int result = Integer.parseInt(String.valueOf(value)); if (result < min || result > max) throw new NumberFormatException(); return result; } catch (Exception exception) { throw new BusinessException(400, label + "必须是" + min + "至" + max + "之间的整数"); } }
}
