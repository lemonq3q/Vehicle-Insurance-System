package com.example.insurancesystem.utils;

import com.example.insurancesystem.domain.chinacity.Province;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证批量导入地区文本最终稳定转换为市级行政区划代码。
 * 测试直接加载生产使用的 ChinaCitys.json，覆盖带/不带行政后缀、省份省略、精确到区县、直辖市和
 * 非法省级输入等边界，防止模板规则与实际基础数据产生偏差。
 */
class AreaConverterUtilImportTest {

    /**
     * 在全部用例执行前装载真实行政区划树，使静态解析方法具备与应用启动后一致的数据环境。
     */
    @BeforeAll
    static void loadAreaData() throws Exception {
        try (InputStream input = AreaConverterUtilImportTest.class.getClassLoader()
                .getResourceAsStream("ChinaCitys.json")) {
            if (input == null) {
                throw new IllegalStateException("测试资源 ChinaCitys.json 不存在");
            }
            AreaConverterUtil.chinaCity = new ObjectMapper().readValue(
                    input, new TypeReference<List<Province>>() { });
        }
    }

    /**
     * 验证模板允许的省市组合及后缀省略写法均解析为杭州市代码。
     */
    @Test
    void resolvesProvinceCityTextWithOptionalSuffixes() {
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("浙江省杭州市"));
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("浙江杭州"));
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("浙江省杭州"));
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("浙江杭州市"));
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("杭州"));
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("杭州市"));
        assertEquals("320100000000", AreaConverterUtil.resolveImportCityCode("南京"));
        assertEquals("320100000000", AreaConverterUtil.resolveImportCityCode("南京市"));
    }

    /**
     * 验证城市名称优先于低层级区县简称。“南京”不得被“南县”和“南区”的简称“南”追加错误候选，
     * 业务区域中的南京也应稳定解析为江苏省南京市。
     */
    @Test
    void prioritizesCityMatchBeforeDistrictAliases() {
        assertEquals("320100000000", AreaConverterUtil.resolveImportCityCode("南京"));
        assertEquals(List.of("310100000000", "320100000000"),
                AreaConverterUtil.resolveImportBusinessAreaCodes("上海市、南京"));
    }

    /**
     * 验证不存在的名称不会再通过行政区简称的任意前缀命中。“南黄”不等于“南县”的全称或简称，
     * “南京黄”也不是“南京市”或南京市下属的完整区县名称，因此两者都必须拒绝。
     */
    @Test
    void rejectsPartialAdministrativeNamePrefixes() {
        assertThrows(IllegalArgumentException.class,
                () -> AreaConverterUtil.resolveImportCityCode("南黄"));
        assertThrows(IllegalArgumentException.class,
                () -> AreaConverterUtil.resolveImportCityCode("南京黄"));
        assertEquals("430900000000", AreaConverterUtil.resolveImportCityCode("湖南省南县"));
    }

    /**
     * 验证用户在省市或城市之后继续填写区县时不会保存区县代码，而是统一提升为所属杭州市代码；
     * 单独填写全国存在重名的“西湖区”必须提示歧义，不能静默选错城市。
     */
    @Test
    void promotesDistrictInputToParentCity() {
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("浙江省杭州市西湖区"));
        assertEquals("330100000000", AreaConverterUtil.resolveImportCityCode("杭州西湖"));
        assertThrows(IllegalArgumentException.class,
                () -> AreaConverterUtil.resolveImportCityCode("西湖区"));
    }

    /**
     * 验证直辖市可直接填写名称，同时普通省份不能被随意映射到第一个城市。
     */
    @Test
    void handlesMunicipalityAndRejectsProvinceOnlyInput() {
        assertEquals("110100000000", AreaConverterUtil.resolveImportCityCode("北京市"));
        assertThrows(IllegalArgumentException.class,
                () -> AreaConverterUtil.resolveImportCityCode("浙江省"));
    }

    /**
     * 验证空值和不存在的地区返回明确校验异常，导入服务不得以空编码继续入库。
     */
    @Test
    void rejectsBlankAndUnknownAreaText() {
        assertThrows(IllegalArgumentException.class,
                () -> AreaConverterUtil.resolveImportCityCode(" "));
        assertThrows(IllegalArgumentException.class,
                () -> AreaConverterUtil.resolveImportCityCode("不存在省不存在市"));
    }

    /**
     * 验证业务区域只填写省份时展开该省全部城市，而明确填写城市时仍只产生一个市级代码。
     */
    @Test
    void expandsProvinceOnlyBusinessAreaToAllCities() {
        List<String> zhejiang = AreaConverterUtil.resolveImportBusinessAreaCodes("浙江省");
        assertEquals(11, zhejiang.size());
        assertTrue(zhejiang.contains("330100000000"));
        assertTrue(zhejiang.contains("330200000000"));
        assertEquals(List.of("330100000000"),
                AreaConverterUtil.resolveImportBusinessAreaCodes("浙江省杭州市"));
    }

    /**
     * 验证省市混合填写时按输入顺序展开并去重，已由省份覆盖的城市不会重复写入业务区域关系。
     */
    @Test
    void deduplicatesMixedProvinceAndCityBusinessAreas() {
        List<String> codes = AreaConverterUtil.resolveImportBusinessAreaCodes("浙江省、上海市、浙江省杭州市");
        assertEquals(12, codes.size());
        assertEquals("330100000000", codes.get(0));
        assertTrue(codes.contains("310100000000"));
    }
}
