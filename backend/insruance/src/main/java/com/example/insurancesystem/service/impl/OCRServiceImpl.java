package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.BusinessLicense;
import com.example.insurancesystem.domain.IdCard;
import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.VehicleCertificate;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import com.example.insurancesystem.mapper.SystemFileMapper;
import com.example.insurancesystem.service.OCRService;
import com.example.insurancesystem.utils.OCRUtil;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
/**
 * 统一编排车险证照 OCR：校验企业文件记录、生成临时访问地址并调用对应识别器。
 * 各证照入口共享相同的文件安全检查和响应结构，仅识别算法不同。
 */
public class OCRServiceImpl implements OCRService {

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private OCRUtil ocrUtil;

    @Autowired
    private SystemFileMapper systemFileMapper;

    @Override
    /**
     * 识别身份证正反面信息。
     */
    public ResponseResult idCardRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeIdcard);
    }

    @Override
    /**
     * 识别企业营业执照信息。
     */
    public ResponseResult businessLicenseRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeBusinessLicense);
    }

    @Override
    /**
     * 识别车辆合格证信息。
     */
    public ResponseResult vehicleCertificateRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeVehicleCertificate);
    }

    @Override
    /**
     * 识别机动车销售发票信息。
     */
    public ResponseResult vehicleInvoiceRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeVehicleInvoice);
    }

    @Override
    /**
     * 识别机动车行驶证信息。
     */
    public ResponseResult vehicleLicenseRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeVehicleLicense);
    }

    /**
     * 执行所有证照共用的识别流程。只信任数据库中的文件路径，拒绝无主键、已删除或无法访问的文件，
     * 避免客户端直接传入任意外部地址；识别完成后将原文件信息和结构化结果一并返回。
     */
    private <T> ResponseResult recognize(SystemFile systemFile, Function<String, T> recognizer) {
        if (systemFile == null || systemFile.getId() == null) {
            return new ResponseResult(400, "文件信息不能为空");
        }

        SystemFile storedFile = systemFileMapper.selectById(systemFile.getId());
        if (storedFile == null || Integer.valueOf(1).equals(storedFile.getIsDelete())) {
            return new ResponseResult(404, "文件不存在");
        }

        String url = ossUtil.getTmpUrl(storedFile.getPath());
        if (url == null || url.isEmpty()) {
            return new ResponseResult(500, "生成文件访问地址失败");
        }

        T recognitionData = recognizer.apply(url);
        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", storedFile);
        data.put("recognitionData", recognitionData);
        return new ResponseResult(200, data);
    }
}
