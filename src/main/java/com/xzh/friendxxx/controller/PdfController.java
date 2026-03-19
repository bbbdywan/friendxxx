package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.service.PdfGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF生成控制器
 */
@RestController
@RequestMapping("/pdf")
@Tag(name = "PDF生成", description = "PDF文档生成相关接口")
public class PdfController {

    @Autowired
    private PdfGeneratorService pdfGeneratorService;

    /**
     * 生成因私出国境工作要求PDF
     * @return PDF文件
     */
    @GetMapping("/private-overseas-travel-requirements")
    @Operation(summary = "生成因私出国境工作要求PDF", description = "生成包含因私出国境工作要求内容的PDF文档")
    public ResponseEntity<byte[]> generatePrivateOverseasTravelRequirementsPdf() {
        try {
            byte[] pdfBytes = pdfGeneratorService.generatePrivateOverseasTravelRequirementsPdf();
            
            // 生成文件名，包含当前时间戳
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "因私出国境工作要求_" + timestamp + ".pdf";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 在线预览因私出国境工作要求PDF
     * @return PDF文件（在线预览）
     */
    @GetMapping("/private-overseas-travel-requirements/preview")
    @Operation(summary = "在线预览因私出国境工作要求PDF", description = "在浏览器中预览因私出国境工作要求PDF文档")
    public ResponseEntity<byte[]> previewPrivateOverseasTravelRequirementsPdf() {
        try {
            byte[] pdfBytes = pdfGeneratorService.generatePrivateOverseasTravelRequirementsPdf();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "因私出国境工作要求.pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 测试PDF服务是否正常
     * @return 简单的文本响应
     */
    @GetMapping("/test")
    @Operation(summary = "测试PDF服务", description = "测试PDF控制器是否正常工作")
    public ResponseEntity<String> testPdfService() {
        return ResponseEntity.ok("PDF服务正常运行！访问路径：\n" +
                "下载: /api/pdf/private-overseas-travel-requirements\n" +
                "预览: /api/pdf/private-overseas-travel-requirements/preview");
    }

    /**
     * 获取PDF的Base64编码（用于前端直接显示）
     * @return Base64编码的PDF数据
     */
    @GetMapping("/private-overseas-travel-requirements/base64")
    @Operation(summary = "获取PDF的Base64编码", description = "返回Base64编码的PDF数据，用于前端直接显示")
    public ResponseEntity<java.util.Map<String, String>> getPdfAsBase64() {
        try {
            byte[] pdfBytes = pdfGeneratorService.generatePrivateOverseasTravelRequirementsPdf();
            String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);

            java.util.Map<String, String> response = new java.util.HashMap<>();
            response.put("pdfData", "data:application/pdf;base64," + base64Pdf);
            response.put("filename", "因私出国境工作要求.pdf");

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            e.printStackTrace();
            java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "PDF生成失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
