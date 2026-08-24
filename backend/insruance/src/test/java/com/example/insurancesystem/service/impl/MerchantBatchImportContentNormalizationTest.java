package com.example.insurancesystem.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 验证批量导入数据行的空白规范化规则，覆盖用户手工输入、跨应用复制和 Excel 自动换行产生的常见空白。
 */
class MerchantBatchImportContentNormalizationTest {

    /**
     * 单元格内容中的首尾及内部空白都应移除，同时保留业务区域使用的逗号分隔符，确保后续地区拆分正确。
     */
    @Test
    void shouldRemoveCommonWhitespaceWithoutRemovingBusinessSeparators() {
        String content = " 广东省 广州市，\u00A0上海市、\u3000南京市\r\n ";

        assertEquals("广东省广州市，上海市、南京市",
                MerchantBatchImportServiceImpl.normalizeImportedContent(content));
    }

    /** 空值和只含空白的单元格应统一成为空字符串，以便沿用现有必填校验。 */
    @Test
    void shouldNormalizeNullAndWhitespaceOnlyValuesToEmpty() {
        assertEquals("", MerchantBatchImportServiceImpl.normalizeImportedContent(null));
        assertEquals("", MerchantBatchImportServiceImpl.normalizeImportedContent(" \t\u3000"));
    }
}
