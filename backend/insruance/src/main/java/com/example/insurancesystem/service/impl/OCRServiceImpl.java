package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.BusinessLicense;
import com.example.insurancesystem.domain.IdCard;
import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.VehicleCertificate;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import com.example.insurancesystem.service.OCRService;
import com.example.insurancesystem.utils.OCRUtil;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class OCRServiceImpl implements OCRService {

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private OCRUtil ocrUtil;

    @Override
    public ResponseResult idCardRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeIdcard);
    }

    @Override
    public ResponseResult businessLicenseRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeBusinessLicense);
    }

    @Override
    public ResponseResult vehicleCertificateRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeVehicleCertificate);
    }

    @Override
    public ResponseResult vehicleInvoiceRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeVehicleInvoice);
    }

    @Override
    public ResponseResult vehicleLicenseRecognition(SystemFile systemFile) {
        return recognize(systemFile, ocrUtil::recognizeVehicleLicense);
    }

    private <T> ResponseResult recognize(SystemFile systemFile, Function<String, T> recognizer) {
        if (systemFile == null || systemFile.getPath() == null || systemFile.getPath().trim().isEmpty()) {
            return new ResponseResult(400, "文件信息不能为空");
        }

        String url = ossUtil.getTmpUrl(systemFile.getPath());
        if (url == null || url.isEmpty()) {
            return new ResponseResult(500, "生成文件访问地址失败");
        }

        T recognitionData = recognizer.apply(url);
        Map<String, Object> data = new HashMap<>();
        data.put("fileInfo", systemFile);
        data.put("recognitionData", recognitionData);
        return new ResponseResult(200, data);
    }
}
