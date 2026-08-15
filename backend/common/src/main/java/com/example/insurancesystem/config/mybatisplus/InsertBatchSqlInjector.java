package com.example.insurancesystem.config.mybatisplus;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;

import java.util.List;

/**
 * 扩展 MyBatis-Plus 默认 SQL 注入器，在保留所有标准 CRUD 方法的同时为 BatchBaseMapper 注册批量插入语句。
 */
public class InsertBatchSqlInjector extends DefaultSqlInjector {

    @Override
    /**
     * 获取默认方法集合并追加 InsertBatchSomeColumn，框架会按实体 TableInfo 生成对应表和列的批量 INSERT SQL。
     */
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        methodList.add(new InsertBatchSomeColumn());
        return methodList;
    }
}
