package com.example.insurancesystem.utils;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;

public class OSSClientSingleton {

    private static volatile OSS ossClient;

    private static final String OUT_ENDPOINT = "oss-cn-hangzhou.aliyuncs.com";
    private static final String INTERNAL_ENDPOINT = "oss-cn-hangzhou-internal.aliyuncs.com";
    private static final String REGION = "cn-hangzhou";

    // 私有构造函数防止外部实例化
    private OSSClientSingleton() {}

    /**
     * 获取单例 OSSClient 实例
     */
    public static OSS getInstance() {
        if (ossClient == null) {
            synchronized (OSSClientSingleton.class) {
                if (ossClient == null) {
                    CredentialsProvider credentialsProvider = CredentialsProviderFactory.newDefaultCredentialProvider(
                            SystemCommonUtil.getAccessKeyId(),
                            SystemCommonUtil.getAccessKeySecret()
                    );
                    ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
                    clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
                    ossClient = OSSClientBuilder.create()
                            .endpoint(OUT_ENDPOINT)
                            .credentialsProvider(credentialsProvider)
                            .clientConfiguration(clientBuilderConfiguration)
                            .region(REGION)
                            .build();
                }
            }
        }
        return ossClient;
    }

    /**
     * 关闭 OSSClient 实例
     */
    public static void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
            ossClient = null;
        }
    }
}