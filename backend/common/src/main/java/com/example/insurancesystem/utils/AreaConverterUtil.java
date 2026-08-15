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
import java.util.List;

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
