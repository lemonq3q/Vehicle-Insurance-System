package com.example.insurancesystem.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 承载营业执照 OCR 提取及企业建档使用的组织名称、统一社会信用代码等证照字段。
 */
public class BusinessLicense {

    private String organizationName;

    private String socialCreditCode;
}
