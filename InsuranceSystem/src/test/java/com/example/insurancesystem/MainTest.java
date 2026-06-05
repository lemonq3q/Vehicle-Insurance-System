package com.example.insurancesystem;

import com.example.insurancesystem.domain.IdCard;
import com.example.insurancesystem.domain.PageTest;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.merchant.MerchantArea;
import com.example.insurancesystem.domain.merchant.UpstreamDTO;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.mapper.PageTestMapper;
import com.example.insurancesystem.system.DataArchive;
import com.example.insurancesystem.system.MaintenanceManager;
import com.example.insurancesystem.utils.*;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.example.insurancesystem.utils.AreaConverterUtil.fuzzySearchCode;

@SpringBootTest
public class MainTest {
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private JsonUtil jsonUtil;

    @Autowired
    private OCRUtil ocrUtil;


    @Test
    public void testAllText(){
        String url = "https://lemonqwq.oss-cn-hangzhou.aliyuncs.com/%" +
                "E8%A1%8C%E9%A9%B6%E8%AF%81%E6%AD%A3%E9%9D%A2.pn" +
                "g?Expires=1779550090&OSSAccessKeyId=TMP.3Ky7Etar" +
                "CVQKZdZNhhRKA6ACVpTVim1MJ6aWE8eRSbRLGuDnSQuTKkjPh" +
                "4Uma9Vpvh6cAXAFMfXT61GtEfigccTGPqyufy&Signature=W" +
                "VrDZ%2BVfw50ceeV7Ic1EVMfpIsc%3D";
        String type = "VehicleLicense";
        String result = ocrUtil.recognizeAllText(url, type);
        System.out.println(result);
    }

    @Test
    public void testPasswordEncoder() {
        System.out.println(passwordEncoder.encode("123456"));
    }

    @Test
    public void testBCryptPasswordEncoder() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encode = passwordEncoder.encode("123456");
        System.out.println(encode);
        System.out.println(encoder.matches("123456", "$2a$10$amA/chxjv2Jz0Up6g0lVMe1So2S52ySuYdrzwyCN6iu42UwHuLeqy"));

    }

    @Autowired
    private PageTestMapper pageTestMapper;

    @Test
    public void testInsertPageTest(){
        for (int i = 0; i < 20; i++) {
            PageTest pageTest = new PageTest();
            pageTest.setNumber(i);
            pageTestMapper.insert(pageTest);
        }

    }

    @Test
    public void testPageHelper(){
        int pageNum = 3;
        int pageSize = 8;
        PageHelper.startPage(pageNum, pageSize);
        List<PageTest> pageTests = pageTestMapper.selectList(null);
        PageInfo<PageTest> pageTestPageInfo = new PageInfo<>(pageTests);
        System.out.println(pageTestPageInfo.getList());
    }

    @Test
    public void stringUtilsTest(){
        String test = "createTime";
        String emptyStr = "";
        String nullStr = null;
        System.out.println(StringUtils.capitalize(test));
        System.out.println(StringUtils.isEmpty(emptyStr));
        System.out.println(StringUtils.isEmpty(nullStr));
    }

    @Test
    public void testList(){
        UpstreamDTO upstreamDTO = new UpstreamDTO();
        upstreamDTO.setBusinessArea(Arrays.asList("1", "2", "3"));
        upstreamDTO.getBusinessArea().set(0, "11");
        System.out.println(upstreamDTO.getBusinessArea());
    }

    @Test
    public void testLoad(){
//        System.out.println(ConverterUtil.chinaCity);
        System.out.println(AreaConverterUtil.areaCodeConvert("666"));
    }

    @Test
    public void fileDownload(){
        String objectName = "dc6b7213-ae28-42a5-8576-353da0c5af67QQ20251213-000907.jpg";
        String tmpUrl = ossUtil.getTmpUrl(objectName);
        System.out.println(tmpUrl);
        String lastUrl = "http://lemonqwq.oss-cn-hangzhou.aliyuncs.com/dc6b7213-ae28-42a5-8576-353da0c5af67QQ20251213" +
                "-000907.jpg?x-oss-date=20260220T101853Z&x-oss-expires=86399&x-oss-signature-version=OSS4-HMAC-SHA256&x" +
                "-oss-credential=LTAI5tNqUDLtMpehCZ5RW7tH%2F20260220%2Fcn-hangzhou%2Foss%2Faliyun_v4_request&x-oss-signat" +
                "ure=dad71ae80b8fbdf5dc839d4993d945db8d48eade5fa27e4e3828786ef1fcf4af";
        System.out.println(lastUrl.equals(tmpUrl));
    }

    @Test
    public void testRecognition(){
        String url = "https://lemonqwq.oss-cn-hangzhou.aliyuncs.com/%E5%8F%91%E7%A5%A8.png?Expires=1771688566&OSSAccessKeyId=TMP.3KrKEQfNVCNQFApMacLTRXTwcrFSCXSPby4FgvxvcdoDYeR62PgimiMeoKojtU8aFCe1hrNBpgDNHWjBKVukmngLQGjhRo&Signature=z6SFnFPEy2B91Zd1%2BS3orNC7fOg%3D";
        VehicleInvoice result = ocrUtil.recognizeVehicleInvoice(url);
        System.out.println(result);
    }

    @Test
    public void testFileDelete(){
        String objectName = "测试.png";
        boolean deleteFile = ossUtil.deleteFile(objectName);
        System.out.println(deleteFile);
    }


    @Autowired
    private MaintenanceManager maintenanceManager;

    @Autowired
    private DataArchive dataArchive;

    @Test
    public void testDailyTask(){
        System.out.println("============= 凌晨4点定时任务开始执行 =============");

        try {
            maintenanceManager.startMaintenance();
            maintenanceManager.waitForAllRequestsComplete();

            dataArchive.archive();

            System.out.println("============= 凌晨4点定时任务全部执行完成 =============");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("凌晨4点定时任务执行异常：" + e.getMessage());
        } finally {
            maintenanceManager.stopMaintenance();
        }
    }

    @Test
    public void testSearchCode(){
        // 测试模糊查询
        AreaConverterUtil.CityCodeResult result1 = fuzzySearchCode("潍坊");
        System.out.println(result1);

        AreaConverterUtil.CityCodeResult result2 = fuzzySearchCode("陕西");
        System.out.println(result2);

        AreaConverterUtil.CityCodeResult result3 = fuzzySearchCode("东城");
        System.out.println(result3);
    }



    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantAreaMapper merchantAreaMapper;

    @Test
    public void testBatchInsert() throws Exception {
        List<String> nameList = new ArrayList<>();
        List<String> areaList = new ArrayList<>();

        List<String> fileNameList = Arrays.asList("1.xlsx", "2.xlsx", "3.xlsx", "4.xlsx", "5.xlsx", "6.xlsx");
        for(String fileName : fileNameList){
            nameList.addAll(ExcelUtil.readSingleColumn("file/" + fileName, 1, false));
            areaList.addAll(ExcelUtil.readSingleColumn("file/" + fileName, 2, false));
        }


        for(int i=0 ; i<nameList.size(); i++){
            String cityCode = AreaConverterUtil.fuzzySearchCode(areaList.get(i)).getCityCode();
            Merchant merchant = new Merchant();
            merchant.setName(nameList.get(i));
            merchant.setCode(SystemCommonUtil.buildCode());
            merchant.setType("车商店铺");
            merchant.setLocation(cityCode);
            merchant.setAddress(areaList.get(i));
            merchant.setUpdateBy(1L);
            merchantMapper.insert(merchant);

            if(cityCode != null){
                MerchantArea merchantArea = new MerchantArea();
                merchantArea.setMerchantId(merchant.getId());
                merchantArea.setAreaCode(cityCode);
                merchantArea.setUpdateBy(1L);
                merchantAreaMapper.insert(merchantArea);
            }
        }

    }
}
