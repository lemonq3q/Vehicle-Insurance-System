package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.workorder.Workorder;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.handler.exception.BusinessException;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规范化承保完整提交，只接收保单、保费政策与收款反馈的白名单字段。
 * 与报价、支付历史录入隔离；取消险种显式生成 NULL，金额模式和比例模式互斥。
 * 本类无持久状态和数据库写入，服务在同一工单事务中使用校验结果。
 */
public final class WorkorderPolicyValidator {
    private static final String[] TYPES = {"COMMERCIAL", "COMPULSORY", "NON_MOTOR"};
    private static final String[] PREFIXES = {"commercial", "compulsory", "nonMotor"};
    private static final String[] FILE_TYPES = {"acceptInsuranceCommercial", "acceptInsuranceCompulsory", "acceptInsuranceOther"};
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("999999.99");

    /**

     * 工具仅提供无状态校验入口，禁止实例化。

     */
    private WorkorderPolicyValidator() {}

    /**
     * 对 HTTP 承保完整表单执行险种、文件类别、时间边界和金额校验，返回持久化白名单。
     * @param request 当前登录用户提交的详情模型，policyTypes 和附件必须表示完整快照。
     * @return 规范化的主表/金额表对象，取消字段为 NULL，比例费用以原规则补算。
     * @throws BusinessException 参数缺失、重复、超范围或模式冲突时返回 400，不产生状态变化。
     */
    public static Workorder normalize(WorkorderDTO request) {
        List<String> selected = request.getPolicyTypes();
        if (selected == null || selected.isEmpty() || new HashSet<>(selected).size() != selected.size()
                || !Set.of(TYPES).containsAll(selected)) fail("请选择有效且不重复的保单类型");
        if (request.getWorkorderFileList() == null || request.getWorkorderFileList().stream().anyMatch(java.util.Objects::isNull)) fail("请提交完整保单附件列表");
        Workorder result = new Workorder();
        result.setId(request.getId());
        BeanWrapper input = new BeanWrapperImpl(request);
        BeanWrapper output = new BeanWrapperImpl(result);

        /*
         * 选中保单仅要求有效起期和保费；终期可空，填写时必须晚于起期。
         * 公司名称与保单号允许空值但仍限制长度。
         * 未选择时全部保持空值，完整更新会清除旧资料；附件可为空但不能超过类别上限。
         */
        for (int index = 0; index < TYPES.length; index++) {
            if (!selected.contains(TYPES[index])) continue;
            String prefix = PREFIXES[index];
            String insurer = text(input.getPropertyValue(prefix + "InsurerName"), 200, false);
            String number = text(input.getPropertyValue(prefix + "PolicyNo"), 100, false);
            Long start = (Long) input.getPropertyValue(prefix + "InsuranceStartTime");
            Long end = (Long) input.getPropertyValue(prefix + "InsuranceEndTime");
            if (start == null || start <= 0 || start > 253402271999L
                    || (end != null && (end <= start || end > 253402271999L)))
                fail("保险起期必须有效；填写终期时须晚于起期，时间单位为秒");
            BigDecimal amount = (BigDecimal) input.getPropertyValue(prefix + "Amount");
            money(amount, true, MAX_AMOUNT);
            final String fileType = FILE_TYPES[index];
            long files = request.getWorkorderFileList().stream().filter(file -> fileType.equals(file.getType())).count();
            if (files > ("NON_MOTOR".equals(TYPES[index]) ? 6 : 1))
                fail("保单附件数量超过限制");
            output.setPropertyValue(prefix + "InsurerName", insurer);
            output.setPropertyValue(prefix + "PolicyNo", number);
            output.setPropertyValue(prefix + "InsuranceStartTime", start);
            output.setPropertyValue(prefix + "InsuranceEndTime", end);
            output.setPropertyValue(prefix + "Amount", amount);
        }
        if (selected.contains("COMPULSORY")) {
            money(request.getVehicleAndTaxAmount(), false, MAX_AMOUNT);
            result.setVehicleAndTaxAmount(request.getVehicleAndTaxAmount());
        }
        if (selected.contains("NON_MOTOR")) {
            result.setNonMotorInsuranceName(text(request.getNonMotorInsuranceName(), 100, false));
            money(request.getNonMotorCoverageAmount(), false, MAX_AMOUNT);
            result.setNonMotorCoverageAmount(request.getNonMotorCoverageAmount());
        }
        for (int index = 0; index < TYPES.length; index++) {
            final String fileType = FILE_TYPES[index];
            if (!selected.contains(TYPES[index]) && request.getWorkorderFileList().stream().anyMatch(file -> fileType.equals(file.getType())))
                fail("未选择险种不能提交对应保单附件");
        }
        /*
         * 其他单证独立于三类保单附件，只记录承保补充资料，不要求勾选非车险。
         * 服务层仍会核验真实文件归属、重复引用和完整快照替换。
         */
        if (request.getWorkorderFileList().stream().filter(file -> "acceptInsuranceMisc".equals(file.getType())).count() > 6)
            fail("其他单证最多上传六份");

        /*
         * 原上下游四维费用逐项保持输入模式；比例模式从本次保费重算，不能沿用旧派生金额。
         * 手工金额模式不重算，车船税政策基数仍是交强组的税额，不混入合计保费。
         */
        for (String side : List.of("upstream", "downstream")) {
            Integer computeType = (Integer) input.getPropertyValue(side + "ComputeType");
            if (computeType == null || (computeType != 0 && computeType != 1)) fail("请选择正确的政策费用计算方式");
            output.setPropertyValue(side + "ComputeType", computeType);
            String[] dimensions = {"Commercial", "Compulsory", "VehicleAndVesselTax", "NonMotor"};
            boolean[] enabled = {selected.contains("COMMERCIAL"), selected.contains("COMPULSORY"), selected.contains("COMPULSORY"), selected.contains("NON_MOTOR")};
            for (int index = 0; index < dimensions.length; index++) {
                if (!enabled[index]) continue;
                String key = side + dimensions[index];
                BigDecimal amount = (BigDecimal) input.getPropertyValue(key + "Amount");
                BigDecimal percentage = (BigDecimal) input.getPropertyValue(key + "Percentage");
                money(amount, false, MAX_AMOUNT);
                money(percentage, false, MAX_PERCENTAGE);
                if (amount != null && percentage != null) fail("政策费用只能选择金额或比例其中一种");
                output.setPropertyValue(key + "Amount", amount);
                output.setPropertyValue(key + "Percentage", percentage);
            }
        }
        result.computeAmount();
        for (String side : List.of("upstream", "downstream")) {
            for (String dimension : List.of("Commercial", "Compulsory", "VehicleAndVesselTax", "NonMotor")) {
                String property = side + dimension + "Amount";
                BigDecimal amount = (BigDecimal) output.getPropertyValue(property);
                BigDecimal rounded = amount == null ? null : amount.setScale(2, java.math.RoundingMode.HALF_UP);
                money(rounded, false, MAX_AMOUNT);
                output.setPropertyValue(property, rounded);
            }
        }
        result.setPayName(text(request.getPayName(), 100, false));
        result.setPayIdNum(text(request.getPayIdNum(), 20, false));
        result.setPayBank(text(request.getPayBank(), 100, false));
        result.setPayBankCardNum(text(request.getPayBankCardNum(), 100, false));
        result.setAcceptInsuranceRemark(text(request.getAcceptInsuranceRemark(), 500, false));
        return result;
    }

    /**
     * 校验十进制金额/比例，不对空值自动补零，防止历史未知变成已确认零元。
     * @param value JSON 中解析的 BigDecimal。
     * @param required 当前险种是否必填。
     * @param maximum 对应数据库 DECIMAL 可容纳的最大值。
     */
    public static void money(BigDecimal value, boolean required, BigDecimal maximum) {
        if (value == null) { if (required) fail("请填写保费及代收车船税"); return; }
        if (value.signum() < 0 || value.compareTo(maximum) > 0 || value.stripTrailingZeros().scale() > 2)
            fail("金额或比例必须非负、不超字段范围且最多两位小数");
    }

    /**
     * 规范化文本且保留空字符串的清空语义，必填信息不允许全空白。
     * @param value 请求文本。
     * @param maximum 数据库字符长度上限。
     * @param required 是否为选中保单的必填资料。
     * @return 去除首尾空白的文本；缺省仍为 NULL。
     */
    public static String text(Object value, int maximum, boolean required) {
        String text = value == null ? null : value.toString().trim();
        if ((required && (text == null || text.isEmpty())) || (text != null && text.length() > maximum))
            fail("保单或反馈文本缺失或超过允许长度");
        return text;
    }

    /**

     * 统一抛出项目业务异常，由全局异常处理转换为 400 响应并回滚服务事务。

     */
    private static void fail(String message) { throw new BusinessException(400, message); }
}

