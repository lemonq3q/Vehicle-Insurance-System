package com.example.insurancesystem.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExcelUtil {

    /**
     * 读取 resources/file/ 下的 Excel，读取指定一列
     * @param filePath 文件路径，如file/test.xlsx
     * @param columnIndex 列号，从 1 开始（1=第一列，2=第二列）
     * @param isReadHeader 是否读取表头（true=读取，false=跳过）
     * @return 该列所有数据 List<String>
     * @throws Exception
     */
    public static List<String> readSingleColumn(
            String filePath,
            int columnIndex,
            boolean isReadHeader
    ) throws Exception {
        // 获取文件流
        ClassPathResource resource = new ClassPathResource(filePath);
        InputStream inputStream = resource.getInputStream();

        List<String> resultList = new ArrayList<>();
        int realIndex = columnIndex - 1;

        EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
                // 跳过表头
                if (!isReadHeader && context.readRowHolder().getRowIndex() == 0) {
                    return;
                }

                String value = "";
                if (rowData != null) {
                    Object obj = rowData.get(realIndex);
                    if (obj != null) {
                        value = obj.toString().trim();
                    }
                }

                resultList.add(value);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().doRead();

        return resultList;
    }
}