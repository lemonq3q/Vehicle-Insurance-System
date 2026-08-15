package com.example.insurancesystem.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value = "page_test")
@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 早期分页功能联调用的简单实体，保留用于验证 MyBatis-Plus 分页参数和返回结构。
 */
public class PageTest {
    @TableId
    private Long id;

    private Integer number;

    @Override
    /**
     * 输出分页测试实体字段，供早期 Mapper 分页联调日志检查。
     */
    public String toString() {
        return "PageTest{" +
                "id=" + id +
                ", number=" + number +
                '}';
    }
}
