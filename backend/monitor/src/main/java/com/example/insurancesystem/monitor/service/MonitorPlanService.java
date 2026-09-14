package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监控平台套餐配置服务。直接维护 SaaS 结算域的 saas_plan 主数据，统一校验价格、计费周期、
 * 成员上限和免费工单存储额度，并为全部人工写操作生成统一系统日志。套餐更新只影响后续购买或
 * 续费读取的模板，不回写 saas_subscription 中已生效的权益快照，避免变相改变既有合同权益。
 */
@Service
public class MonitorPlanService {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,50}$");
    private static final String SELECT_FIELDS = "SELECT id,code,name,description,billing_period billingCycle," +
            "duration_days durationDays,user_limit memberLimit,workorder_limit workorderLimit,price," +
            "original_price listPrice,status,sort_no sortOrder,updated_at updatedAt " +
            "FROM saas_plan ";
    private final JdbcTemplate jdbc;
    private final MonitorSystemLogService systemLog;

    public MonitorPlanService(JdbcTemplate jdbc, MonitorSystemLogService systemLog) {
        this.jdbc = jdbc;
        this.systemLog = systemLog;
    }

    /**
     * 一次查询返回套餐管理所需的全部字段。下架套餐仍需展示以便重新上架，逻辑删除记录不返回。
     */
    public List<Map<String, Object>> list() {
        return jdbc.queryForList(SELECT_FIELDS + "WHERE deleted=0 ORDER BY sort_no,id");
    }

    /** 根据主键读取未删除套餐；不存在时返回明确业务错误而不是空页面。 */
    public Map<String, Object> detail(Long id) {
        if (id == null) throw new BusinessException(400, "套餐标识不能为空");
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_FIELDS + "WHERE id=? AND deleted=0", id);
        if (rows.isEmpty()) throw new BusinessException(404, "套餐不存在");
        return rows.get(0);
    }

    /**
     * 校验并创建一个普通正式套餐。编码在应用层检查唯一性，生成主键后读取数据库最终快照，
     * 套餐新增和审计日志共享事务，任一写入失败都会整体回滚。
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, Long operatorId, String operatorName) {
        PlanInput input = validate(body, true);
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM saas_plan WHERE code=? AND deleted=0",
                Integer.class, input.code);
        if (duplicate != null && duplicate > 0) throw new BusinessException(400, "套餐编码已存在");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO saas_plan(code,name,description,billing_period,duration_days,user_limit," +
                            "workorder_limit,price,original_price,status,sort_no,created_at,updated_at,updated_by,deleted) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),?,0)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, input.code); statement.setString(2, input.name);
            statement.setString(3, input.description); statement.setString(4, input.billingCycle);
            statement.setInt(5, input.durationDays); statement.setInt(6, input.memberLimit);
            statement.setInt(7, input.workorderLimit); statement.setBigDecimal(8, input.price);
            statement.setBigDecimal(9, input.listPrice); statement.setInt(10, input.status);
            statement.setInt(11, input.sortOrder); statement.setObject(12, operatorId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, "套餐创建失败");
        Map<String, Object> after = detail(key.longValue());
        systemLog.recordOperation("PLAN_CREATE", "创建套餐", "plan", operatorId, operatorName,
                null, null, "PLAN", key, input.name, input.reason, "创建套餐“" + input.name + "”", null, after);
        return after;
    }

    /**
     * 锁定并更新套餐配置，避免两个管理员并发编辑时审计前置快照不准确。客户端提交的 code 被忽略，
     * 数据库原编码始终保留；成员和工单额度只供未来订阅复制，不更新当前订阅快照。
     */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> before = lockedDetail(id);
        PlanInput input = validate(body, false);
        jdbc.update("UPDATE saas_plan SET name=?,description=?,billing_period=?,duration_days=?,user_limit=?," +
                        "workorder_limit=?,price=?,original_price=?,status=?,sort_no=?,updated_at=NOW(),updated_by=? " +
                        "WHERE id=? AND deleted=0", input.name, input.description, input.billingCycle,
                input.durationDays, input.memberLimit, input.workorderLimit, input.price, input.listPrice,
                input.status, input.sortOrder, operatorId, id);
        Map<String, Object> after = detail(id);
        systemLog.recordOperation("PLAN_UPDATE", "修改套餐", "plan", operatorId, operatorName,
                null, null, "PLAN", id, input.name, input.reason, "修改套餐“" + input.name + "”", before, after);
        return after;
    }

    /**
     * 仅更新上下架状态。重复提交相同状态视为幂等成功且不制造无变化的审计日志；状态变化时记录
     * 完整前后快照，已有企业订阅保持生效，不因套餐下架被取消。
     */
    @Transactional
    public Map<String, Object> updateStatus(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> before = lockedDetail(id);
        int status = integer(body, "status", 0, 1);
        String reason = requiredText(body, "reason", "操作原因", 500);
        if (number(before.get("status")).intValue() == status) return before;
        jdbc.update("UPDATE saas_plan SET status=?,updated_at=NOW(),updated_by=? WHERE id=? AND deleted=0",
                status, operatorId, id);
        Map<String, Object> after = detail(id);
        String action = status == 1 ? "上架" : "下架";
        systemLog.recordOperation("PLAN_STATUS", action + "套餐", "plan", operatorId, operatorName,
                null, null, "PLAN", id, String.valueOf(before.get("name")), reason,
                action + "套餐“" + before.get("name") + "”", before, after);
        return after;
    }

    /** 在当前事务内加行锁读取套餐，确保后续写入使用一致的审计前置快照。 */
    private Map<String, Object> lockedDetail(Long id) {
        if (id == null) throw new BusinessException(400, "套餐标识不能为空");
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_FIELDS + "WHERE id=? AND deleted=0 FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "套餐不存在");
        return rows.get(0);
    }

    /** 将接口字段转换为强类型套餐输入，并集中执行所有业务边界校验。 */
    private PlanInput validate(Map<String, Object> body, boolean creating) {
        if (body == null) throw new BusinessException(400, "套餐参数不能为空");
        String code = creating ? requiredText(body, "code", "套餐编码", 50).toUpperCase(Locale.ROOT) : null;
        if (creating && !CODE_PATTERN.matcher(code).matches())
            throw new BusinessException(400, "套餐编码仅支持大写字母、数字和下划线，长度为2至50位");
        String cycle = requiredText(body, "billingCycle", "计费周期", 20).toUpperCase(Locale.ROOT);
        if (!List.of("DAY", "MONTH", "YEAR").contains(cycle)) throw new BusinessException(400, "计费周期无效");
        BigDecimal price = decimal(body, "price", "销售价格");
        BigDecimal listPrice = decimal(body, "listPrice", "划线价格");
        if (listPrice.signum() > 0 && listPrice.compareTo(price) < 0)
            throw new BusinessException(400, "划线价格不能低于销售价格");
        return new PlanInput(code, requiredText(body, "name", "套餐名称", 100),
                requiredText(body, "description", "套餐描述", 500), cycle,
                integer(body, "durationDays", 1, Integer.MAX_VALUE), integer(body, "memberLimit", 1, Integer.MAX_VALUE),
                integer(body, "workorderLimit", 0, Integer.MAX_VALUE), price, listPrice,
                integer(body, "status", 0, 1), integer(body, "sortOrder", 0, Integer.MAX_VALUE),
                requiredText(body, "reason", "操作原因", 500));
    }

    /** 读取必填文本并限制数据库列宽，空白字符串不允许作为有效配置。 */
    private String requiredText(Map<String, Object> body, String key, String label, int maxLength) {
        Object value = body == null ? null : body.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) throw new BusinessException(400, "请填写" + label);
        if (text.length() > maxLength) throw new BusinessException(400, label + "不能超过" + maxLength + "个字符");
        return text;
    }

    /** 读取整数额度并校验闭区间，拒绝小数、空值和超出整数范围的数据。 */
    private int integer(Map<String, Object> body, String key, int min, int max) {
        try {
            BigDecimal value = new BigDecimal(String.valueOf(body.get(key)));
            int result = value.intValueExact();
            if (result < min || result > max) throw new ArithmeticException();
            return result;
        } catch (Exception exception) {
            throw new BusinessException(400, key + "参数无效");
        }
    }

    /** 读取非负两位小数金额，避免数据库隐式截断造成页面显示值与保存值不一致。 */
    private BigDecimal decimal(Map<String, Object> body, String key, String label) {
        try {
            BigDecimal value = new BigDecimal(String.valueOf(body.get(key)));
            if (value.signum() < 0 || value.scale() > 2) throw new ArithmeticException();
            return value;
        } catch (Exception exception) {
            throw new BusinessException(400, label + "必须是不小于0且最多保留两位小数的金额");
        }
    }

    /** 将查询结果中的状态值转换为 Number，兼容 JDBC 返回的不同整数实现。 */
    private Number number(Object value) {
        if (!(value instanceof Number)) throw new BusinessException(500, "套餐状态数据异常");
        return (Number) value;
    }

    /** 保存完成校验后的套餐字段，避免写入阶段重复解析不可信请求数据。 */
    private static final class PlanInput {
        private final String code; private final String name; private final String description;
        private final String billingCycle; private final int durationDays; private final int memberLimit;
        private final int workorderLimit; private final BigDecimal price; private final BigDecimal listPrice;
        private final int status; private final int sortOrder; private final String reason;

        private PlanInput(String code, String name, String description, String billingCycle, int durationDays,
                int memberLimit, int workorderLimit, BigDecimal price, BigDecimal listPrice, int status,
                int sortOrder, String reason) {
            this.code = code; this.name = name; this.description = description; this.billingCycle = billingCycle;
            this.durationDays = durationDays; this.memberLimit = memberLimit; this.workorderLimit = workorderLimit;
            this.price = price; this.listPrice = listPrice; this.status = status; this.sortOrder = sortOrder;
            this.reason = reason;
        }
    }
}
