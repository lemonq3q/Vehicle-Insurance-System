package com.example.insurancesystem.domain.authenticate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "user_role")
/**
 * 用户与系统角色的关联实体，用于持久化多角色授权关系。
 */
public class UserRole {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roleId;

    private Long createTime;

    private Long updateTime;

    private Long updateBy;

    private Integer isDelete;
}
