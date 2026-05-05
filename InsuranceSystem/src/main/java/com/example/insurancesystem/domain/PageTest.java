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
public class PageTest {
    @TableId
    private Long id;

    private Integer number;

    @Override
    public String toString() {
        return "PageTest{" +
                "id=" + id +
                ", number=" + number +
                '}';
    }
}
