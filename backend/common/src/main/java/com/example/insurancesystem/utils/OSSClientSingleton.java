package com.example.insurancesystem.utils;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;

/**
 * 以线程安全懒加载方式复用阿里云 OSS 客户端，避免每次上传、签名或删除都重新创建连接资源。
 * 客户端使用 V4 签名和杭州区域，凭证从系统统一配置读取。
 */
public class OSSClientSingleton {

    private static volatile OSS ossClient;

    private static final String OUT_ENDPOINT = "https://oss-cn-hangzhou.aliyuncs.com";
    private static final String INTERNAL_ENDPOINT = "https://oss-cn-hangzhou-internal.aliyuncs.com";
    private static final String REGION = "cn-hangzhou";

    /**
     * 单例工具不允许外部实例化。
     */
    private OSSClientSingleton() {}

    /**
     * 使用 volatile 与双重检查锁延迟创建 OSS 客户端。首次创建配置默认凭证、V4 签名、外网端点和区域，
     * 后续线程直接复用已安全发布的实例，减少同步开销。
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
     * 关闭底层连接资源并清空单例引用，供应用停机或凭证配置需要重新初始化时使用。
     */
    public static void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
            ossClient = null;
        }
    }
}
