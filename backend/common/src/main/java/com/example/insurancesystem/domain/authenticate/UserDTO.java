package com.example.insurancesystem.domain.authenticate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 用户管理接口的数据传输对象，聚合账号基础资料与角色信息，避免直接暴露认证实体内部字段。
 */
public class UserDTO {
    private Long id;

    private String username;

    private String name;

    private List<String> perms;
}
