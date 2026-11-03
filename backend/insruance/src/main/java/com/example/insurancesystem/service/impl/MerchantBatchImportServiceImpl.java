package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.merchant.MerchantArea;
import com.example.insurancesystem.domain.merchant.MerchantCategoryCode;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportFailure;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportLimits;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportResult;
import com.example.insurancesystem.domain.merchant.importbatch.BatchTemplateValidationResult;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.service.MerchantBatchImportService;
import com.example.insurancesystem.utils.AreaConverterUtil;
import com.example.insurancesystem.utils.SystemCommonUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实现上游与下游 Excel 批量导入。
 * 工作簿结构预检只验证官方模板的稳定结构；正式导入逐行业务校验并排除失败行，
 * 随后按500条分批写入主体与区域关系。下游联系人直接作为机构字段导入，全部批次处于同一事务。
 */
@Service
public class MerchantBatchImportServiceImpl implements MerchantBatchImportService {

    private static final int HEADER_ROW_INDEX = 3;
    private static final int DATA_START_ROW_INDEX = 4;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{12,30}$");
    private static final Pattern IMPORT_WHITESPACE_PATTERN = Pattern.compile("[\\s\\u00A0\\u3000]+");

    private static final LinkedHashMap<String, List<String>> UPSTREAM_TEMPLATE = template(
            "使用说明", Collections.emptyList(),
            "上游机构", Arrays.asList("* 机构名称", "* 所在地区", "机构地址", "* 联系人", "* 联系电话", "* 业务区域", "备注"),
            "字段说明", Collections.emptyList());

    private static final LinkedHashMap<String, List<String>> DOWNSTREAM_TEMPLATE = template(
            "使用说明", Collections.emptyList(),
            "下游机构", Arrays.asList("* 下游名称", "* 下游类型", "* 所在地区", "详细地址", "* 开户银行", "* 银行卡号", "* 联系人", "* 联系电话", "备注"),
            "字段说明", Collections.emptyList());

    private final MerchantMapper merchantMapper;
    private final MerchantAreaMapper merchantAreaMapper;
    private final DataFormatter formatter = new DataFormatter();

    public MerchantBatchImportServiceImpl(MerchantMapper merchantMapper,
                                          MerchantAreaMapper merchantAreaMapper) {
        this.merchantMapper = merchantMapper;
        this.merchantAreaMapper = merchantAreaMapper;
    }

    /**
     * 打开上游工作簿并核对固定 Sheet 与表头，不读取和评价数据行。
     */
    @Override
    public ResponseResult validateUpstreamTemplate(MultipartFile file) {
        return validateTemplate(file, UPSTREAM_TEMPLATE);
    }

    /**
     * 打开下游工作簿并核对含联系人字段的单个机构数据 Sheet 表头，不读取和评价数据行。
     */
    @Override
    public ResponseResult validateDownstreamTemplate(MultipartFile file) {
        return validateTemplate(file, DOWNSTREAM_TEMPLATE);
    }

    /**
     * 解析上游数据，依次验证必填项、长度、手机号、地区、业务区域以及文件内和数据库内重复名称。
     * 通过的机构由数据库生成主键后，商户和业务区域按500条分批写入；失败行不会进入事务写入集合。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult importUpstream(MultipartFile file) {
        String structureError = inspectTemplate(file, UPSTREAM_TEMPLATE);
        if (structureError != null) return new ResponseResult(400, structureError);

        try (Workbook workbook = openWorkbook(file)) {
            List<ExcelRow> rows = readRows(workbook.getSheet("上游机构"), UPSTREAM_TEMPLATE.get("上游机构"));
            BatchImportResult result = new BatchImportResult();
            result.setTotalRows(rows.size());
            Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
            Long userId = SystemCommonUtil.getNowUserId();
            Set<String> repeatedNames = repeatedValues(rows, 0);
            Set<String> existingNames = existingMerchantNames(enterpriseId, rows, "UPSTREAM");
            Long categoryId = merchantMapper.selectCategoryIdByCode(MerchantCategoryCode.INSURANCE_ORG);
            if (categoryId == null) return new ResponseResult(500, "上游商户分类字典缺失");

            List<Merchant> merchants = new ArrayList<>();
            Map<String, List<String>> areaCodesByMerchantCode = new LinkedHashMap<>();
            for (ExcelRow row : rows) {
                List<String> reasons = new ArrayList<>();
                String name = required(row, 0, "机构名称", reasons);
                String locationText = required(row, 1, "所在地区", reasons);
                String contact = required(row, 3, "联系人", reasons);
                String phone = required(row, 4, "联系电话", reasons);
                String areaText = required(row, 5, "业务区域", reasons);
                checkLength(name, 100, "机构名称", reasons);
                checkLength(value(row, 2), 100, "机构地址", reasons);
                checkLength(contact, 100, "联系人", reasons);
                checkLength(value(row, 6), 400, "备注", reasons);
                if (!phone.isEmpty() && !PHONE_PATTERN.matcher(phone).matches()) reasons.add("联系电话必须为11位中国大陆手机号");
                if (!name.isEmpty() && repeatedNames.contains(name.toLowerCase(java.util.Locale.ROOT))) reasons.add("机构名称在文件中重复");
                if (!name.isEmpty() && existingNames.contains(name.toLowerCase(java.util.Locale.ROOT))) reasons.add("当前企业已存在同名机构");

                String cityCode = resolveCity(locationText, reasons);
                List<String> areaCodes = resolveAreas(areaText, reasons);
                if (!reasons.isEmpty()) {
                    result.addFailure(failure("上游机构", row, reasons));
                    continue;
                }

                Merchant merchant = merchant(row, enterpriseId, userId, categoryId, cityCode);
                merchant.setDirection("UPSTREAM");
                merchant.setContact(contact);
                merchant.setPhone(phone);
                merchants.add(merchant);
                areaCodesByMerchantCode.put(merchant.getCode(), areaCodes);
            }

            /*
             * 商户主体必须先由数据库分配自增主键。批量 SQL 返回后再使用回填 ID 组装区域关系，既保持
             * 按500条批量写入，也避免区域引用内存预生成但数据库并未采用的主键。
             */
            insertMerchantBatches(merchants, "上游机构");
            List<MerchantArea> areas = new ArrayList<>();
            for (Merchant merchant : merchants) {
                for (String areaCode : areaCodesByMerchantCode.get(merchant.getCode())) {
                    areas.add(area(merchant.getId(), enterpriseId, userId, areaCode));
                }
            }
            /* 多区域关联也按相同批次写入，全部批次仍在本次导入事务内一起提交或回滚。 */
            for (int offset = 0; offset < areas.size(); offset += BatchImportLimits.WRITE_BATCH_SIZE) {
                merchantAreaMapper.insertBatchSomeColumn(new ArrayList<>(areas.subList(offset,
                        Math.min(offset + BatchImportLimits.WRITE_BATCH_SIZE, areas.size()))));
            }
            result.setSuccessRows(merchants.size());
            return new ResponseResult(200, "导入完成", result);
        } catch (BusinessException exception) {
            return new ResponseResult(exception.getCode(), exception.getMsg());
        } catch (Exception exception) {
            throw new IllegalArgumentException("读取或导入 Excel 失败: " + rootMessage(exception), exception);
        }
    }

    /**
     * 按行导入下游基础信息与联系人；同一行原子校验，不再解析人员 Sheet 或创建角色关系。
     * 失败行报告原因，有效行在同一事务内批量写入主体，自增主键继续由数据库生成。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult importDownstream(MultipartFile file) {
        String structureError = inspectTemplate(file, DOWNSTREAM_TEMPLATE);
        if (structureError != null) return new ResponseResult(400, structureError);

        try (Workbook workbook = openWorkbook(file)) {
            List<ExcelRow> merchantRows = readRows(workbook.getSheet("下游机构"), DOWNSTREAM_TEMPLATE.get("下游机构"));
            BatchImportResult result = new BatchImportResult();
            result.setTotalRows(merchantRows.size());
            Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
            Long userId = SystemCommonUtil.getNowUserId();
            Set<String> repeatedNames = repeatedValues(merchantRows, 0);
            Set<String> existingNames = existingMerchantNames(enterpriseId, merchantRows, "DOWNSTREAM");
            Map<String, Merchant> validMerchantByName = new LinkedHashMap<>();

            for (ExcelRow row : merchantRows) {
                List<String> reasons = new ArrayList<>();
                String name = required(row, 0, "下游名称", reasons);
                String type = required(row, 1, "下游类型", reasons);
                String locationText = required(row, 2, "所在地区", reasons);
                String bank = required(row, 4, "开户银行", reasons);
                String bankCard = required(row, 5, "银行卡号", reasons).replace("'", "");
                checkLength(name, 100, "下游名称", reasons);
                checkLength(value(row, 3), 100, "详细地址", reasons);
                checkLength(bank, 100, "开户银行", reasons);
                String contact = required(row, 6, "联系人", reasons);
                String phone = required(row, 7, "联系电话", reasons);
                checkLength(contact, 100, "联系人", reasons);
                if (!PHONE_PATTERN.matcher(phone).matches()) reasons.add("联系电话必须为11位中国大陆手机号");
                checkLength(value(row, 8), 400, "备注", reasons);
                if (!BANK_CARD_PATTERN.matcher(bankCard).matches()) reasons.add("银行卡号必须为12至30位数字");
                if (!name.isEmpty() && repeatedNames.contains(name.toLowerCase(java.util.Locale.ROOT))) reasons.add("下游名称在文件中重复");
                if (!name.isEmpty() && existingNames.contains(name.toLowerCase(java.util.Locale.ROOT))) reasons.add("当前企业已存在同名下游");
                String categoryCode = downstreamCategory(type, reasons);
                Long categoryId = categoryCode == null ? null : merchantMapper.selectCategoryIdByCode(categoryCode);
                if (categoryCode != null && categoryId == null) reasons.add("下游类型对应的分类字典不存在");
                String cityCode = resolveCity(locationText, reasons);
                if (!reasons.isEmpty()) {
                    result.addFailure(failure("下游机构", row, reasons));
                    continue;
                }
                Merchant merchant = merchant(row, enterpriseId, userId, categoryId, cityCode);
                merchant.setAddress(value(row, 3));
                merchant.setDirection("DOWNSTREAM");
                merchant.setContact(contact);
                merchant.setPhone(phone);
                merchant.setBank(bank);
                merchant.setBankCardNum(bankCard);
                validMerchantByName.put(name, merchant);
            }

            /*
             * 联系人已经合并到下游同一行，有效主体分批写入，不再创建任何人员及角色记录。
             * 仍核验数据库生成主键，以保持现有批量写入失败的事务回滚行为。
             */
            List<Merchant> merchants = new ArrayList<>(validMerchantByName.values());
            insertMerchantBatches(merchants, "下游机构");

            result.setSuccessRows(merchants.size());
            return new ResponseResult(200, "导入完成", result);
        } catch (BusinessException exception) {
            return new ResponseResult(exception.getCode(), exception.getMsg());
        } catch (Exception exception) {
            throw new IllegalArgumentException("读取或导入 Excel 失败: " + rootMessage(exception), exception);
        }
    }

    /**
     * 将校验通过的主体按500条拆分写入，避免1万行产生过长SQL或超出预编译参数数量。
     * 每批都校验数据库自增ID已回填，区域关联继续引用同一对象；不在此方法新建事务，
     * 任意一批失败由调用方整文件事务回滚，原有成功/失败行返回语义保持一致。
     * @param merchants 本文件全部有效主体对象，允许为空集合。
     * @param businessName 缺失回填主键时的业务提示名称。
     */
    private void insertMerchantBatches(List<Merchant> merchants, String businessName) {
        for (int offset = 0; offset < merchants.size(); offset += BatchImportLimits.WRITE_BATCH_SIZE) {
            List<Merchant> batch = new ArrayList<>(merchants.subList(offset,
                    Math.min(offset + BatchImportLimits.WRITE_BATCH_SIZE, merchants.size())));
            merchantMapper.insertImportBatch(batch);
            requireGeneratedIds(batch, businessName);
        }
    }

    /**
     * 执行轻量结构校验并把错误作为正常业务结果返回，方便前端在上传步骤直接展示模板是否可继续。
     */
    private ResponseResult validateTemplate(MultipartFile file, LinkedHashMap<String, List<String>> template) {
        String error = inspectTemplate(file, template);
        String name = file == null ? "" : file.getOriginalFilename();
        BatchTemplateValidationResult result = new BatchTemplateValidationResult(error == null, name,
                error == null ? "模板结构校验通过，可以继续导入" : error);
        return new ResponseResult(200, result);
    }

    /**
     * 只检查 xlsx 文件、固定 Sheet 以及数据 Sheet 第四行表头；说明文字、示例数据和空行均不参与判断。
     */
    private String inspectTemplate(MultipartFile file, LinkedHashMap<String, List<String>> template) {
        if (file == null || file.isEmpty()) return "请选择需要上传的 Excel 文件";
        if (file.getSize() > BatchImportLimits.MAX_FILE_BYTES) return "文件不能超过20MB";
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) return "仅支持 .xlsx 格式的官方模板";
        try (Workbook workbook = openWorkbook(file)) {
            for (Map.Entry<String, List<String>> entry : template.entrySet()) {
                Sheet sheet = workbook.getSheet(entry.getKey());
                if (sheet == null) return "缺少 Sheet：“ + entry.getKey() + ”";
                if (entry.getValue().isEmpty()) continue;
                Row header = sheet.getRow(HEADER_ROW_INDEX);
                if (header == null) return "Sheet“" + entry.getKey() + "”缺少第4行表头";
                for (int i = 0; i < entry.getValue().size(); i++) {
                    String actual = cell(header.getCell(i));
                    if (!entry.getValue().get(i).equals(actual)) {
                        return "Sheet“" + entry.getKey() + "”第" + (i + 1) + "列表头应为“" + entry.getValue().get(i) + "”";
                    }
                }
            }
            return null;
        } catch (Exception exception) {
            return "文件无法读取或不是有效的 Excel 工作簿: " + rootMessage(exception);
        }
    }

    /** 打开工作簿并限制调用方通过 try-with-resources 及时释放上传文件流。 */
    private Workbook openWorkbook(MultipartFile file) throws Exception {
        InputStream inputStream = file.getInputStream();
        return WorkbookFactory.create(inputStream);
    }

    /**
     * 从第五行开始读取非空数据，并保留原始 Excel 行号。所有业务单元格在进入必填、格式、重复、地区
     * 解析及数据库校验前统一移除空白字符，使用户粘贴产生的半角空格、全角空格、换行和不间断空格
     * 不会改变实际导入值；单个数据 Sheet 仍限制最大行数以控制单条 SQL 和内存规模。
     */
    private List<ExcelRow> readRows(Sheet sheet, List<String> headers) {
        List<ExcelRow> rows = new ArrayList<>();
        for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row source = sheet.getRow(rowIndex);
            List<String> values = new ArrayList<>();
            boolean empty = true;
            for (int column = 0; column < headers.size(); column++) {
                String value = source == null ? "" : normalizeImportedContent(cell(source.getCell(column)));
                values.add(value);
                if (!value.isEmpty()) empty = false;
            }
            if (!empty) rows.add(new ExcelRow(rowIndex + 1, headers, values));
            if (rows.size() > BatchImportLimits.MAX_DATA_ROWS) throw new BusinessException(400, "单个数据 Sheet 最多允许 " + BatchImportLimits.MAX_DATA_ROWS + " 行");
        }
        return rows;
    }

    /** 使用 POI DataFormatter 统一读取文本、数字及公式显示值，并清除模板示例中的文本前缀单引号。 */
    private String cell(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    /**
     * 清除导入内容中的全部常见空白。该规则只用于数据行，不用于模板 Sheet 名和表头，因此不会破坏
     * 官方模板的结构校验；中文逗号、英文逗号和顿号等业务区域分隔符会原样保留，仍可正常多选解析。
     *
     * @param value POI 按 Excel 显示格式读取出的单元格文本
     * @return 移除半角空白、全角空格及不间断空格后的业务值
     */
    static String normalizeImportedContent(String value) {
        if (value == null || value.isEmpty()) return "";
        return IMPORT_WHITESPACE_PATTERN.matcher(value).replaceAll("");
    }

    /** 创建固定顺序的模板描述，保证检查顺序和提示信息稳定。 */
    private static LinkedHashMap<String, List<String>> template(Object... entries) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], (List<String>) entries[i + 1]);
        return result;
    }

    /** 收集文件内重复名称，按数据库默认不区分大小写的规则比较；空名称由必填校验负责。 */
    private Set<String> repeatedValues(List<ExcelRow> rows, int index) {
        Set<String> seen = new HashSet<>();
        Set<String> repeated = new HashSet<>();
        for (ExcelRow row : rows) {
            String value = value(row, index).toLowerCase(java.util.Locale.ROOT);
            if (!value.isEmpty() && !seen.add(value)) repeated.add(value);
        }
        return repeated;
    }

    /** 一次查询文件涉及的全部名称，避免逐行访问数据库。 */
    private Set<String> existingMerchantNames(Long enterpriseId, List<ExcelRow> rows, String direction) {
        List<String> names = rows.stream().map(row -> value(row, 0)).filter(value -> !value.isEmpty()).distinct().toList();
        return names.isEmpty() ? Collections.emptySet() : merchantMapper.selectExistingNames(enterpriseId, names, direction).stream()
                .map(name -> name.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    /** 提取必填单元格并将缺失原因追加到当前行原因集合。 */
    private String required(ExcelRow row, int index, String label, List<String> reasons) {
        String value = value(row, index);
        if (value.isEmpty()) reasons.add(label + "不能为空");
        return value;
    }

    private String value(ExcelRow row, int index) {
        return index < row.values.size() ? row.values.get(index).trim() : "";
    }

    /** 对数据库 varchar 边界做导入前校验，避免整批 SQL 因单个超长值回滚。 */
    private void checkLength(String value, int max, String label, List<String> reasons) {
        if (value != null && value.length() > max) reasons.add(label + "不能超过" + max + "个字符");
    }

    /** 调用严格地区解析器并把可理解的错误归入对应数据行。 */
    private String resolveCity(String text, List<String> reasons) {
        if (text == null || text.isEmpty()) return null;
        try { return AreaConverterUtil.resolveImportCityCode(text); }
        catch (IllegalArgumentException exception) { reasons.add(exception.getMessage()); return null; }
    }

    /** 调用支持省份展开的业务区域解析器并把任一无效分项归入对应数据行。 */
    private List<String> resolveAreas(String text, List<String> reasons) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        try { return AreaConverterUtil.resolveImportBusinessAreaCodes(text); }
        catch (IllegalArgumentException exception) { reasons.add(exception.getMessage()); return Collections.emptyList(); }
    }

    /** 将模板中文下游类型映射到稳定分类编码，拒绝下拉选项之外的手工输入。 */
    private String downstreamCategory(String type, List<String> reasons) {
        String code = MerchantCategoryCode.fromLegacyName(type);
        if (code == null || MerchantCategoryCode.INSURANCE_ORG.equals(code)) {
            reasons.add("下游类型只能选择：车商店铺、汽修厂、代理人");
            return null;
        }
        return code;
    }

    /**
     * 组装机构的企业归属、业务编号和基础资料。数据库批量插入后回填自增ID，
     * 区域关系在主体写入完成后组装，下游联系人不再生成独立人员关系。
     */
    private Merchant merchant(ExcelRow row, Long enterpriseId, Long userId, Long categoryId, String cityCode) {
        Merchant merchant = new Merchant();
        merchant.setEnterpriseId(enterpriseId);
        merchant.setCode(SystemCommonUtil.buildCode());
        merchant.setName(value(row, 0));
        merchant.setCategoryId(categoryId);
        merchant.setLocation(cityCode);
        merchant.setAddress(value(row, 2));
        merchant.setUpdateBy(userId);
        merchant.setIsDelete(0);
        return merchant;
    }

    /** 组装上游承保区域关系，主键预生成以保持批量插入语句无后续回查。 */
    private MerchantArea area(Long merchantId, Long enterpriseId, Long userId, String areaCode) {
        MerchantArea area = new MerchantArea();
        area.setMerchantId(merchantId);
        area.setEnterpriseId(enterpriseId);
        area.setAreaCode(areaCode);
        area.setUpdateBy(userId);
        area.setIsDelete(0);
        return area;
    }

    /**
     * 确认批量主体写入后 JDBC 已把每一个 AUTO_INCREMENT 主键回填到原实体。关联数据只有在此检查通过
     * 后才允许组装；任一 ID 缺失都会抛出异常并回滚整个导入事务，避免再次产生孤立区域或角色记录。
     *
     * @param entities 已完成批量插入且应取得主键的商户或人员集合
     * @param businessName 用于异常定位的业务主体名称
     */
    private void requireGeneratedIds(List<?> entities, String businessName) {
        for (Object entity : entities) {
            Long id;
            if (entity instanceof Merchant) {
                id = ((Merchant) entity).getId();

            } else {
                throw new IllegalArgumentException("不支持校验主键的导入实体: " + entity.getClass().getSimpleName());
            }
            if (id == null) {
                throw new IllegalStateException(businessName + "批量插入后未返回数据库主键");
            }
        }
    }

    /** 将多个行级原因合并成稳定文本，并保留原始字段名和值供前端表格展示。 */
    private BatchImportFailure failure(String sheet, ExcelRow row, List<String> reasons) {
        return new BatchImportFailure(sheet, row.rowNumber, row.asMap(), String.join("；", new LinkedHashSet<>(reasons)));
    }

    /** 获取异常链最内层业务消息，避免只向用户返回无意义的包装异常类型。 */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** 保存一行解析后的列值和真实 Excel 行号，并按模板表头生成展示 Map。 */
    private static class ExcelRow {
        private final int rowNumber;
        private final List<String> headers;
        private final List<String> values;

        private ExcelRow(int rowNumber, List<String> headers, List<String> values) {
            this.rowNumber = rowNumber;
            this.headers = headers;
            this.values = values;
        }

        private Map<String, String> asMap() {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) map.put(headers.get(i).replace("* ", ""), values.get(i));
            return map;
        }
    }
}
