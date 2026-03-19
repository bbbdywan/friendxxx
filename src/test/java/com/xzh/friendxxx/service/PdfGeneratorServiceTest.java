package com.xzh.friendxxx.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF生成服务测试类
 */
@SpringBootTest
public class PdfGeneratorServiceTest {

    @Autowired
    private PdfGeneratorService pdfGeneratorService;

    @Test
    public void testGeneratePrivateOverseasTravelRequirementsPdf() throws IOException {
        // 生成PDF
        byte[] pdfBytes = pdfGeneratorService.generatePrivateOverseasTravelRequirementsPdf();
        
        // 验证PDF不为空
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        
        // 验证PDF文件头（PDF文件以%PDF开头）
        String pdfHeader = new String(pdfBytes, 0, 4);
        assertEquals("%PDF", pdfHeader);
        
        // 可选：将PDF保存到文件系统进行手动验证
        // 注意：这行代码会在项目根目录生成测试PDF文件
        try (FileOutputStream fos = new FileOutputStream("test_因私出国境工作要求.pdf")) {
            fos.write(pdfBytes);
            System.out.println("测试PDF已生成：test_因私出国境工作要求.pdf");
        }
    }
}
