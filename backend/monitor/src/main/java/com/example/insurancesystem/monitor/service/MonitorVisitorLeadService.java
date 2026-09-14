package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 监控系统游客信息查询服务，只提供按编号精确查询和无条件分页列表，不开放修改或删除游客个人信息。
 * JSON 合作诉求在服务层还原为数组，确保监控前端与官网提交接口共享相同字段契约。
 */
@Service
public class MonitorVisitorLeadService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MonitorVisitorLeadService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据可选的完整游客编号分页查询。编号不为空时使用等值条件命中唯一索引；为空时按提交时间和主键倒序。
     * 分页大小限制为 1 至 100，防止后台页面单次加载过多个人信息。
     */
    public Map<String, Object> page(String leadNo, int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(400, "分页参数不合法");
        leadNo = leadNo == null ? "" : leadNo.trim().toUpperCase();
        if (leadNo.length() > 32) throw new BusinessException(400, "游客编号不能超过32个字符");
        String condition = leadNo.isBlank() ? "" : " AND lead_no=?";
        Object[] queryArgs = leadNo.isBlank() ? new Object[]{} : new Object[]{leadNo};
        long total = jdbc.queryForObject("SELECT COUNT(1) FROM saas_visitor_lead WHERE deleted=0" + condition,
                Long.class, queryArgs);
        Object[] pageArgs = leadNo.isBlank() ? new Object[]{(pageNo - 1) * pageSize, pageSize}
                : new Object[]{leadNo, (pageNo - 1) * pageSize, pageSize};
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,lead_no leadNo,name,contact,role_code roleCode,expected_monthly_orders expectedMonthlyOrders," +
                "intent_codes intentCodes,remark,created_at createdAt FROM saas_visitor_lead WHERE deleted=0" + condition +
                " ORDER BY created_at DESC,id DESC LIMIT ?,?", pageArgs);
        rows.forEach(this::decodeIntents);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rows); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /** 将 MySQL JSON 驱动返回值解析为稳定字符串数组，异常历史值降级为空数组而不拖垮整页。 */
    @SuppressWarnings("unchecked")
    private void decodeIntents(Map<String, Object> row) {
        Object raw = row.get("intentCodes");
        try { row.put("intentCodes", raw == null ? Collections.emptyList() : objectMapper.readValue(String.valueOf(raw), List.class)); }
        catch (Exception invalidJson) { row.put("intentCodes", Collections.emptyList()); }
    }
}
