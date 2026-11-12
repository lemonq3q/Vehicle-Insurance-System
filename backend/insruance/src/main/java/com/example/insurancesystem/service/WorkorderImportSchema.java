package com.example.insurancesystem.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 管理续保工单长表导入契约，模板生成、字段说明与解析共用 resources/import/workorder-columns.json。
 * 仅允许契约列写入工单，防止导入企业ID、操作人、附件或续保完成周期等内部状态。
 * 启动时加载一次且不对外暴露修改入口，配置损坏则明确阻止服务启动。
 */
@Component
public class WorkorderImportSchema {
    private final List<Column> columns;
    private final List<Column> legacyColumns;

    /**
     * 从随版本发布的契约加载列定义，保持模板与解析顺序一致。
     * @param mapper 项目已有 JSON 转换器。
     * @throws IOException 契约资源缺失或无效。
     */
    public WorkorderImportSchema(ObjectMapper mapper) throws IOException {
        try (InputStream input = new ClassPathResource("import/workorder-columns.json").getInputStream()) {
            columns = List.copyOf(mapper.readValue(input, new TypeReference<List<Column>>() { }));
        }
        try (InputStream input = new ClassPathResource("import/workorder-columns-legacy.json").getInputStream()) {
            legacyColumns = List.copyOf(mapper.readValue(input, new TypeReference<List<Column>>() { }));
        }
    }

    /**
     * 返回固定顺序的只读列集合，供导入校验及模板核对使用。
     * @return 按前端业务流程排列的当前101列契约。
     */
    public List<Column> getColumns() { return columns; }

    /**
     * 返回旧101列契约，导入器还可读取其前93列，防止用户已填写的历史模板因新版调序而错位。
     * @return 旧模板原始字段顺序。
     */
    public List<Column> getLegacyColumns() { return legacyColumns; }

    /**
     * 描述单个 Excel 列的业务字段路径、类型、必填性和长度/枚举约束。
     * 实例仅在启动反序列化时创建，解析时不修改，不承载任何用户或租户状态。
     */
    public record Column(String label, String path, String type, String group, boolean required,
                         Integer maxLength, Map<String, Integer> values) { }
}
