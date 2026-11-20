package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.web.bind.annotation.*;

/** SaaS向监控提供精简目录，只读企业和账号，不修改成员状态；共享密钥校验后才访问业务表。 */
@RestController
@RequestMapping("/internal/log-directory")
public class InternalLogDirectoryController {
    private final NamedParameterJdbcTemplate jdbc;
    private final String secret;
    /** 注入SaaS数据库与既有内部鉴权密钥，不接受终端JWT作为服务间凭证。 */
    public InternalLogDirectoryController(NamedParameterJdbcTemplate jdbc,@Value("${maintenance.participant.internal-secret}") String secret){this.jdbc=jdbc;this.secret=secret;}
    /** 按企业名称包含匹配，返回最多20个未删除企业。 */
    @GetMapping("/enterprises") public ResponseResult<?> enterprises(@RequestHeader("X-Maintenance-Secret") String credential,@RequestParam(defaultValue="") String keyword) {
        authorize(credential);if(keyword.isBlank())return new ResponseResult<>(200,List.of());
        return new ResponseResult<>(200,jdbc.queryForList("SELECT id,name FROM tenant_enterprise WHERE deleted=0 AND name LIKE :keyword ESCAPE '!' ORDER BY name,id LIMIT 20",new MapSqlParameterSource("keyword",pattern(keyword))));
    }
    /** 当前企业下按姓名或邮箱包含匹配，返回账号ID而非成员主键，包含停用但未删除成员。 */
    @GetMapping("/users") public ResponseResult<?> users(@RequestHeader("X-Maintenance-Secret") String credential,@RequestParam Long enterpriseId,@RequestParam(defaultValue="") String keyword) {
        authorize(credential);if(enterpriseId<=0)throw new HttpBusinessException(400,"企业参数不合法");if(keyword.isBlank())return new ResponseResult<>(200,List.of());
        return new ResponseResult<>(200,jdbc.queryForList("SELECT DISTINCT u.id,u.real_name realName,u.email FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id JOIN tenant_enterprise e ON e.id=m.enterprise_id WHERE m.enterprise_id=:enterpriseId AND e.deleted=0 AND m.deleted=0 AND u.deleted=0 AND (u.real_name LIKE :keyword ESCAPE '!' OR u.email LIKE :keyword ESCAPE '!') ORDER BY u.real_name,u.id LIMIT 20",new MapSqlParameterSource("enterpriseId",enterpriseId).addValue("keyword",pattern(keyword))));
    }
    /** 共享密钥无效直接拒绝，不能依靠内部URL前缀作为权限边界。 */
    private void authorize(String credential){if(secret==null||secret.isBlank()||!secret.equals(credential))throw new HttpBusinessException(403,"内部调用凭证无效");}
    /** 将通配符转义，用户输入只按字面关键词搜索，不拼接SQL。 */
    private String pattern(String keyword){return "%"+keyword.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
}
