package com.example.insurancesystem.utils;


import com.alibaba.excel.EasyExcel;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebUtils {
    public static String renderString(HttpServletResponse response, String string){
        try{
            response.setStatus(200);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().print(string);
        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    public static void renderExcel(HttpServletResponse response, List<?> data, Class<?> clazz) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        // 处理中文文件名乱码（关键）
        String fileName = URLEncoder.encode("机构数据导出_" + System.currentTimeMillis(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        // 禁止缓存（避免前端缓存旧文件）
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            EasyExcel.write(response.getOutputStream(), clazz)
                    .sheet("表1") // Excel工作表名称
                    .doWrite(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
