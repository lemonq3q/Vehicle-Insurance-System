package com.example.insurancesystem.utils;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;
import com.example.insurancesystem.domain.authenticate.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SystemCommonUtil {

    private static final String ACCESS_KEY_ID = "";

    private static final String ACCESS_KEY_SECRET = "";

    public static String getAccessKeyId(){
        return ACCESS_KEY_ID;
    }

    public static String getAccessKeySecret(){
        return ACCESS_KEY_SECRET;
    }

    public static Client getCredentialClient() {
        Config credentialConfig = new Config();
        credentialConfig.setType("access_key");
        credentialConfig.setAccessKeyId(getAccessKeyId());
        credentialConfig.setAccessKeySecret(getAccessKeySecret());
        return new Client(credentialConfig);
    }

    /**
     * 未确定算法，暂时用时间戳代替
     * @return 不重复的code
     */
    public static String buildCode(){
        return UUID.randomUUID().toString()
                .replace("-", "")
                .toUpperCase()
                .substring(0, 10);
    }

    /**
     * 获取进行操作的用户id
     * @return 当前上下文认证的用户id
     */
    public static Long getNowUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null){
            LoginUser nowUser = (LoginUser) authentication.getPrincipal();
            return nowUser.getUser().getId();
        }
        else{
            return 1L;
        }
    }

    public static List<String> getNowUserPerms(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null){
            LoginUser nowUser = (LoginUser) authentication.getPrincipal();
            return nowUser.getPermissions();
        }
        else{
            return new ArrayList<>();
        }
    }

    public static boolean hasPerm(String perm){
        List<String> perms = getNowUserPerms();
        return perms.contains(perm);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++){
            System.out.println(buildCode());
        }
    }

}
