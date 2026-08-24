package com.example.insurancesystem.utils;

import com.example.insurancesystem.domain.chinacity.Area;
import com.example.insurancesystem.domain.chinacity.City;
import com.example.insurancesystem.domain.chinacity.Province;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
/**
 * 在应用启动时加载全国省市区树，并提供行政区划代码转名称及 OCR 地名模糊反查能力。
 */
public class AreaConverterUtil {

    public static List<Province> chinaCity;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    /**
     * 从 classpath ChinaCitys.json 反序列化完整省市区结构。基础数据缺失或格式错误会阻止应用启动，
     * 避免业务运行后才在地址转换处出现空指针或返回错误地区。
     */
    public void loadChinaCityData(){
        try {
            String jsonFileName = "ChinaCitys.json";
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFileName);

            if (inputStream == null) {
                throw new RuntimeException("未在resources目录下找到城市JSON文件：" + jsonFileName);
            }

            chinaCity = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Province>>() {}
            );

            inputStream.close();
        } catch (Exception e) {
            throw new RuntimeException("加载城市JSON数据失败", e);
        }
    }

    /**
     * 按行政区划代码前两位、前四位和完整代码逐级查找省、市、区，并以“ / ”连接已有层级。
     * 传入空值返回 null，未匹配代码返回空字符串。
     */
    public static String areaCodeConvert(String areaCode) {
        if (areaCode == null){
            return null;
        }
        List<String> names = new ArrayList<>();
        for (Province province : chinaCity) {
            if (province.getCode().equals(areaCode)){
                names.add(province.getProvince());
                break;
            }
            else if (areaCode.length() >= 2 && province.getCode().substring(0,2).equals(areaCode.substring(0,2))){
                names.add(province.getProvince());
                for (City city : province.getCitys()){
                    if (city.getCode().equals(areaCode)){
                        names.add(city.getCity());
                        break;
                    }
                    else if (areaCode.length() >= 4 && city.getCode().substring(0,4).equals(areaCode.substring(0,4))){
                        names.add(city.getCity());
                        for (Area area : city.getAreas()){
                            if (area.getCode().equals(areaCode)){
                                names.add(area.getArea());
                                break;
                            }
                        }
                        break;
                    }
                }
                break;
            }
        }
        return String.join(" / ", names);
    }

    /**
     * 以双向 contains 规则模糊匹配省、市或区县名称，并返回完整上级链及代码。
     * 只匹配到省或市时使用该节点第一个下级补齐表单需要的完整区域选择；无匹配返回字段为空的结果对象。
     * @param name 省份/城市/区县名称（支持模糊，如：潍坊、陕西、东城）
     * @return 包含 provinceCode, cityCode, areaCode 的对象
     */
    public static CityCodeResult fuzzySearchCode(String name) {
        if (name == null || name.isBlank() || chinaCity == null) {
            return new CityCodeResult();
        }

        String search = name.trim();

        for (Province province : chinaCity) {
            String provinceName = province.getProvince();
            String provinceCode = province.getCode();

            if (provinceName.contains(search) || search.contains(provinceName)) {
                CityCodeResult result = new CityCodeResult();
                result.setProvinceCode(provinceCode);
                result.setProvinceName(provinceName);
                if (province.getCitys() != null && !province.getCitys().isEmpty()) {
                    City firstCity = province.getCitys().get(0);
                    result.setCityCode(firstCity.getCode());
                    result.setCityName(firstCity.getCity());

                    if (firstCity.getAreas() != null && !firstCity.getAreas().isEmpty()) {
                        Area firstArea = firstCity.getAreas().get(0);
                        result.setAreaCode(firstArea.getCode());
                        result.setAreaName(firstArea.getArea());
                    }
                }
                return result;
            }

            if (province.getCitys() != null) {
                for (City city : province.getCitys()) {
                    String cityName = city.getCity();
                    String cityCode = city.getCode();

                    if (cityName.contains(search) || search.contains(cityName)) {
                        CityCodeResult result = new CityCodeResult();
                        result.setProvinceName(provinceName);
                        result.setProvinceCode(provinceCode);
                        result.setCityName(cityName);
                        result.setCityCode(cityCode);

                        if (city.getAreas() != null && !city.getAreas().isEmpty()) {
                            Area firstArea = city.getAreas().get(0);
                            result.setAreaCode(firstArea.getCode());
                            result.setAreaName(firstArea.getArea());
                        }
                        return result;
                    }

                    if (city.getAreas() != null) {
                        for (Area area : city.getAreas()) {
                            String areaName = area.getArea();
                            String areaCode = area.getCode();

                            if (areaName.contains(search) || search.contains(areaName)) {
                                CityCodeResult result = new CityCodeResult();
                                result.setProvinceName(provinceName);
                                result.setProvinceCode(provinceCode);
                                result.setCityName(cityName);
                                result.setCityCode(cityCode);
                                result.setAreaName(areaName);
                                result.setAreaCode(areaCode);
                                return result;
                            }
                        }
                    }
                }
            }
        }

        return new CityCodeResult();
    }

    /**
     * 将批量导入模板中的中文地区解析为唯一的市级行政区划代码。
     *
     * <p>该方法专门服务于 Excel 批量导入，不复用会自动选择第一个下级地区的表单模糊查询逻辑。
     * 输入会先移除空白、常见分隔符以及“省、市、自治区、区、县”等行政后缀，然后按照省、市、区县
     * 的层级顺序进行前缀匹配。用户填写到区县时返回其所属城市代码；只填写城市时会在全国范围内查找，
     * 只有结果唯一才接受。只填写普通省份、地区不存在或名称存在歧义时抛出明确异常，避免导入时静默
     * 保存错误地区。直辖市既是省级名称也是唯一市级名称，因此允许“北京”或“北京市”直接解析。</p>
     *
     * @param input Excel 单元格中的中文地区，例如“浙江省杭州市”“浙江杭州”“杭州市”或“杭州市西湖区”
     * @return 统一精确到市的六位行政区划代码
     * @throws IllegalArgumentException 输入为空、不能识别到市或存在多个候选城市时抛出
     */
    public static String resolveImportCityCode(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("地区不能为空");
        }
        if (chinaCity == null || chinaCity.isEmpty()) {
            throw new IllegalStateException("行政区划基础数据尚未加载");
        }

        String normalized = normalizeAreaText(input);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("地区不能为空");
        }

        /*
         * 首先识别输入开头的省份。省份存在时只在该省内部匹配城市和区县，既符合用户按省市逐级填写的
         * 习惯，也能消除不同省份存在同名区县时的歧义。多个省份别名命中时选择最长前缀。
         */
        Province matchedProvince = null;
        int provincePrefixLength = -1;
        for (Province province : chinaCity) {
            for (String alias : areaAliases(province.getProvince())) {
                if (normalized.startsWith(alias) && alias.length() > provincePrefixLength) {
                    matchedProvince = province;
                    provincePrefixLength = alias.length();
                }
            }
        }
        if (matchedProvince != null) {
            String remainder = normalized.substring(provincePrefixLength);
            if (remainder.isEmpty()) {
                if (matchedProvince.getCitys() != null && matchedProvince.getCitys().size() == 1
                        && sameAreaName(matchedProvince.getProvince(), matchedProvince.getCitys().get(0).getCity())) {
                    return matchedProvince.getCitys().get(0).getCode();
                }
                throw new IllegalArgumentException("地区仅包含省份，必须至少填写到城市: " + input);
            }
            String code = resolveWithinProvince(matchedProvince, remainder);
            if (code != null) {
                return code;
            }
            throw new IllegalArgumentException("无法在" + matchedProvince.getProvince() + "内识别到城市: " + input);
        }

        /*
         * 省份被省略时必须先按城市层级匹配。只要存在城市候选，就不能再混入区县候选；否则“南京”在
         * 正确命中南京市后，还会被“南县”“南区”的单字简称“南”命中，错误地产生益阳和香港候选。
         */
        Map<String, String> candidates = new LinkedHashMap<>();
        for (Province province : chinaCity) {
            collectCityCandidates(province, normalized, candidates);
        }
        if (candidates.size() == 1) {
            return candidates.keySet().iterator().next();
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("城市名称存在歧义，请补充省份: " + input + "，候选=" + candidates.values());
        }

        /*
         * 仅在没有任何城市命中时降级到区县匹配，使“西湖区”“天河区”等输入仍可提升到所属城市；
         * 同名区县对应多个城市时继续要求用户补充上级地区。
         */
        for (Province province : chinaCity) {
            collectDistrictCandidates(province, normalized, candidates);
        }
        if (candidates.size() == 1) {
            return candidates.keySet().iterator().next();
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("无法识别地区，请填写“省+市”，例如“浙江省杭州市”: " + input);
        }
        throw new IllegalArgumentException("地区名称存在歧义，请补充省份: " + input + "，候选=" + candidates.values());
    }

    /**
     * 将上游批量导入中的业务区域文本展开为去重后的市级行政区划代码列表。
     *
     * <p>业务区域与机构“所在地区”的语义不同：所在地区必须定位到单个城市，而业务区域允许用户只填写
     * 省份。单独填写省级名称时，该省下的全部城市都会按照行政区划基础数据顺序展开；填写城市或区县时，
     * 仍复用严格的市级解析规则。多个区域允许使用中英文逗号、顿号或分号分隔，省份与城市可以混合填写，
     * 最终通过有序集合去重。例如“浙江省、上海市、浙江省杭州市”会展开浙江全部城市和上海市，杭州市
     * 不会被重复保存。</p>
     *
     * @param input Excel 业务区域单元格文本
     * @return 至少包含一个元素的市级行政区划代码列表
     * @throws IllegalArgumentException 输入为空、任一分项无法识别或名称存在歧义时抛出
     */
    public static List<String> resolveImportBusinessAreaCodes(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("业务区域不能为空");
        }
        if (chinaCity == null || chinaCity.isEmpty()) {
            throw new IllegalStateException("行政区划基础数据尚未加载");
        }

        Set<String> cityCodes = new LinkedHashSet<>();
        String[] parts = input.split("[,，、;；]+");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String normalized = normalizeAreaText(part);
            Province provinceOnly = findExactProvince(normalized);
            if (provinceOnly != null) {
                if (provinceOnly.getCitys() == null || provinceOnly.getCitys().isEmpty()) {
                    throw new IllegalArgumentException("省份下没有可用城市数据: " + part.trim());
                }
                provinceOnly.getCitys().stream().map(City::getCode).forEach(cityCodes::add);
            } else {
                cityCodes.add(resolveImportCityCode(part));
            }
        }
        if (cityCodes.isEmpty()) {
            throw new IllegalArgumentException("业务区域不能为空");
        }
        return new ArrayList<>(cityCodes);
    }

    /**
     * 按省份全称或去后缀简称进行完全匹配。这里禁止前缀命中，确保“浙江省杭州市”不会被错误地当成
     * 整个浙江省，而是继续走单城市解析。
     */
    private static Province findExactProvince(String normalizedInput) {
        Province matched = null;
        for (Province province : chinaCity) {
            for (String alias : areaAliases(province.getProvince())) {
                if (normalizedInput.equals(alias)) {
                    if (matched != null && matched != province) {
                        throw new IllegalArgumentException("省份名称存在歧义: " + normalizedInput);
                    }
                    matched = province;
                }
            }
        }
        return matched;
    }

    /**
     * 在已经确定的省份内依次匹配城市前缀和区县前缀，返回区县所属的市级代码。
     * 城市优先于区县，避免城市名称与区县简称相似时误选下级节点。
     */
    private static String resolveWithinProvince(Province province, String remainder) {
        if (province.getCitys() == null) {
            return null;
        }
        for (City city : province.getCitys()) {
            if (matchesCityInput(remainder, city)) {
                return city.getCode();
            }
        }
        Map<String, String> candidates = new LinkedHashMap<>();
        collectDistrictCandidates(province, remainder, candidates);
        if (candidates.size() == 1) {
            return candidates.keySet().iterator().next();
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("区县名称存在歧义，请补充城市: " + remainder + "，候选=" + candidates.values());
        }
        return null;
    }

    /**
     * 只收集一个省份内由城市名称直接命中的候选，以城市代码去重。
     * 区县匹配由调用方确认全国没有城市候选后单独执行，保证行政层级优先级正确。
     */
    private static void collectCityCandidates(Province province, String input, Map<String, String> candidates) {
        if (province.getCitys() == null) {
            return;
        }
        for (City city : province.getCitys()) {
            if (matchesCityInput(input, city)) {
                candidates.put(city.getCode(), province.getProvince() + city.getCity());
            }
        }
    }

    /**
     * 将区县匹配结果提升到所属城市。输入允许带有已经识别的城市前缀，因此同时检查完整输入与去除城市
     * 前缀后的剩余部分，覆盖“杭州西湖区”和“西湖区”两类填写方式。
     */
    private static void collectDistrictCandidates(Province province, String input, Map<String, String> candidates) {
        if (province.getCitys() == null) {
            return;
        }
        for (City city : province.getCitys()) {
            if (city.getAreas() == null) {
                continue;
            }
            for (Area area : city.getAreas()) {
                if (matchesExactAreaName(input, area.getArea())) {
                    candidates.put(city.getCode(), province.getProvince() + city.getCity());
                }
            }
        }
    }

    /**
     * 严格判断输入是否等于城市全称/简称，或由“完整城市名 + 完整下辖区县名”组成。
     * 这里不接受城市名后跟任意字符，避免“南京黄”因以“南京”开头而被错误判定为南京市。
     */
    private static boolean matchesCityInput(String normalizedInput, City city) {
        for (String cityAlias : areaAliases(city.getCity())) {
            if (normalizedInput.equals(cityAlias)) {
                return true;
            }
            if (!normalizedInput.startsWith(cityAlias)) {
                continue;
            }
            String remainder = normalizedInput.substring(cityAlias.length());
            if (!remainder.isEmpty() && city.getAreas() != null) {
                for (Area area : city.getAreas()) {
                    if (matchesExactAreaName(remainder, area.getArea())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断输入是否与行政区全称或去行政后缀后的简称完全相等。
     * 完整相等使“南京”只能匹配“南京市”，而“南黄”不能因为“南县”的简称为“南”而被接受。
     */
    private static boolean matchesExactAreaName(String normalizedInput, String areaName) {
        return areaAliases(areaName).stream().anyMatch(normalizedInput::equals);
    }

    /**
     * 生成行政区全称和常用去后缀简称。别名按长度降序排列，使“广西壮族自治区”优先于“广西”。
     */
    private static List<String> areaAliases(String name) {
        String full = normalizeAreaText(name);
        List<String> aliases = new ArrayList<>();
        aliases.add(full);
        String shortName = full
                .replaceFirst("(壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区)$", "")
                .replaceFirst("(自治州|地区|盟|市|省|区|县)$", "");
        if (!shortName.isEmpty() && !shortName.equals(full)) {
            aliases.add(shortName);
        }
        aliases.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return aliases;
    }

    /**
     * 移除 Excel 输入中不影响地区语义的空白和层级分隔符，保留中文行政区名称用于前缀匹配。
     */
    private static String normalizeAreaText(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\s/\\\\,，、;；>-]+", "");
    }

    /**
     * 判断省级和市级名称在去除行政后缀后是否一致，用于识别北京、天津等直辖市。
     */
    private static boolean sameAreaName(String first, String second) {
        List<String> firstAliases = areaAliases(first);
        List<String> secondAliases = areaAliases(second);
        for (String left : firstAliases) {
            for (String right : secondAliases) {
                if (left.equals(right)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 封装模糊匹配得到的省、市、区名称与代码。允许只填充部分层级，供前端级联选择器逐级回显。
     */
    public static class CityCodeResult {
        private String provinceName;
        private String provinceCode;
        private String cityName;
        private String cityCode;
        private String areaName;
        private String areaCode;

        /**
         * 创建所有行政区划字段为空的结果，表示未匹配或等待逐级填充。
         */
        public CityCodeResult() {}

        /**
         * 返回匹配省份名称。
         */
        public String getProvinceName() { return provinceName; }
        /**
         * 设置匹配省份名称。
         */
        public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
        /**
         * 返回匹配省份代码。
         */
        public String getProvinceCode() { return provinceCode; }
        /**
         * 设置匹配省份代码。
         */
        public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
        /**
         * 返回匹配城市名称。
         */
        public String getCityName() { return cityName; }
        /**
         * 设置匹配城市名称。
         */
        public void setCityName(String cityName) { this.cityName = cityName; }
        /**
         * 返回匹配城市代码。
         */
        public String getCityCode() { return cityCode; }
        /**
         * 设置匹配城市代码。
         */
        public void setCityCode(String cityCode) { this.cityCode = cityCode; }
        /**
         * 返回匹配区县名称。
         */
        public String getAreaName() { return areaName; }
        /**
         * 设置匹配区县名称。
         */
        public void setAreaName(String areaName) { this.areaName = areaName; }
        /**
         * 返回匹配区县代码。
         */
        public String getAreaCode() { return areaCode; }
        /**
         * 设置匹配区县代码。
         */
        public void setAreaCode(String areaCode) { this.areaCode = areaCode; }

        @Override
        /**
         * 输出所有行政区划字段，便于本地匹配调试和日志诊断。
         */
        public String toString() {
            return "CityCodeResult{" +
                    "provinceName='" + provinceName + '\'' +
                    ", provinceCode='" + provinceCode + '\'' +
                    ", cityName='" + cityName + '\'' +
                    ", cityCode='" + cityCode + '\'' +
                    ", areaName='" + areaName + '\'' +
                    ", areaCode='" + areaCode + '\'' +
                    '}';
        }
    }

    /**
     * 预留的本地区划转换调试入口，不参与应用运行。
     */
    public static void main(String[] args) {

    }
}
