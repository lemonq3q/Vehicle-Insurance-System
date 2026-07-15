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
public class AreaConverterUtil {

    public static List<Province> chinaCity;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void loadChinaCityData(){
        try {
            // 读取resources下的JSON文件
            String jsonFileName = "ChinaCitys.json";
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFileName);

            if (inputStream == null) {
                throw new RuntimeException("未在resources目录下找到城市JSON文件：" + jsonFileName);
            }

            // 解析JSON为List<Province>
            chinaCity = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Province>>() {}
            );

            inputStream.close();
        } catch (Exception e) {
            throw new RuntimeException("加载城市JSON数据失败", e);
        }
    }

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
     * 模糊匹配名称，返回对应 省/市/区 的 code
     * @param name 省份/城市/区县名称（支持模糊，如：潍坊、陕西、东城）
     * @return 包含 provinceCode, cityCode, areaCode 的对象
     */
    public static CityCodeResult fuzzySearchCode(String name) {
        // 空值直接返回空结果
        if (name == null || name.isBlank() || chinaCity == null) {
            return new CityCodeResult();
        }

        String search = name.trim();

        // 遍历所有省份
        for (Province province : chinaCity) {
            String provinceName = province.getProvince();
            String provinceCode = province.getCode();

            // 匹配省份
            if (provinceName.contains(search) || search.contains(provinceName)) {
                CityCodeResult result = new CityCodeResult();
                result.setProvinceCode(provinceCode);
                result.setProvinceName(provinceName);
                // 省份匹配到，默认取第一个市、第一个区（可选）
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

            // 遍历该省下的所有市
            if (province.getCitys() != null) {
                for (City city : province.getCitys()) {
                    String cityName = city.getCity();
                    String cityCode = city.getCode();

                    // 匹配城市
                    if (cityName.contains(search) || search.contains(cityName)) {
                        CityCodeResult result = new CityCodeResult();
                        result.setProvinceName(provinceName);
                        result.setProvinceCode(provinceCode);
                        result.setCityName(cityName);
                        result.setCityCode(cityCode);

                        // 取第一个区
                        if (city.getAreas() != null && !city.getAreas().isEmpty()) {
                            Area firstArea = city.getAreas().get(0);
                            result.setAreaCode(firstArea.getCode());
                            result.setAreaName(firstArea.getArea());
                        }
                        return result;
                    }

                    // 遍历该市下的所有区
                    if (city.getAreas() != null) {
                        for (Area area : city.getAreas()) {
                            String areaName = area.getArea();
                            String areaCode = area.getCode();

                            // 匹配区县
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

        // 没匹配到返回空
        return new CityCodeResult();
    }

    /**
     * 封装返回结果：省市区名称 + code
     */
    public static class CityCodeResult {
        private String provinceName;
        private String provinceCode;
        private String cityName;
        private String cityCode;
        private String areaName;
        private String areaCode;

        // 全参/无参构造 + getter/setter
        public CityCodeResult() {}

        public String getProvinceName() { return provinceName; }
        public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
        public String getProvinceCode() { return provinceCode; }
        public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
        public String getCityName() { return cityName; }
        public void setCityName(String cityName) { this.cityName = cityName; }
        public String getCityCode() { return cityCode; }
        public void setCityCode(String cityCode) { this.cityCode = cityCode; }
        public String getAreaName() { return areaName; }
        public void setAreaName(String areaName) { this.areaName = areaName; }
        public String getAreaCode() { return areaCode; }
        public void setAreaCode(String areaCode) { this.areaCode = areaCode; }

        // 方便打印/调试
        @Override
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

    public static void main(String[] args) {

    }
}
