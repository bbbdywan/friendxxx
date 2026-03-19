package com.xzh.friendxxx.example;

import com.xzh.friendxxx.service.PdfGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF生成示例类
 * 演示如何在代码中使用PDF生成服务
 */
@Component
public class PdfGenerationExample {

    @Autowired
    private PdfGeneratorService pdfGeneratorService;

    /**
     * 生成PDF文件到指定路径
     * @param outputPath 输出路径
     * @throws IOException IO异常
     */
    public void generatePdfToFile(String outputPath) throws IOException {
        // 生成PDF字节数组
        byte[] pdfBytes = pdfGeneratorService.generatePrivateOverseasTravelRequirementsPdf();
        
        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(pdfBytes);
            System.out.println("PDF文件已生成：" + outputPath);
        }
    }

    /**
     * 生成带时间戳的PDF文件
     * @param baseDir 基础目录
     * @throws IOException IO异常
     */
    public void generatePdfWithTimestamp(String baseDir) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "因私出国境工作要求_" + timestamp + ".pdf";
        String fullPath = baseDir + "/" + filename;
        
        generatePdfToFile(fullPath);
    }

    /**
     * 获取PDF字节数组（用于其他处理，如发送邮件、上传等）
     * @return PDF字节数组
     * @throws IOException IO异常
     */
    public byte[] getPdfBytes() throws IOException {
        return pdfGeneratorService.generatePrivateOverseasTravelRequirementsPdf();
    }
}
