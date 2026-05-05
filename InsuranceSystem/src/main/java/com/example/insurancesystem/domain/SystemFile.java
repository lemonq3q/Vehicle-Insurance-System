package com.example.insurancesystem.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "system_file")
public class SystemFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String path;

    private String fileName;

    private Integer isLinked;

    private Long createTime;

    private Long updateTime;

    private Long updateBy;

    private Integer isDelete;
}
