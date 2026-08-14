package com.xzh.friendxxx.common.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.DeleteObjectsResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 阿里云OSS工具类
 * @author ForeverGreenDam
 */
@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 文件上传
     *
     * @param bytes
     * @param objectName
     * @return
     */
    public String upload(byte[] bytes, String objectName) {
        // 创建日期文件夹路径：friendxxx/2025-01-20/
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fullObjectName = "friendxxx/" + dateFolder + "/" + objectName;

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 创建PutObject请求。
            ossClient.putObject(bucketName, fullObjectName, new ByteArrayInputStream(bytes));
        } catch (OSSException oe) {
            log.error("OSS拒绝上传: errorCode={}, requestId={}", oe.getErrorCode(), oe.getRequestId(), oe);
            throw new IllegalStateException("文件上传失败", oe);
        } catch (ClientException ce) {
            log.error("OSS客户端上传失败", ce);
            throw new IllegalStateException("文件上传失败", ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        //文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(fullObjectName);

        log.info("文件上传到:{}", stringBuilder.toString());

        return stringBuilder.toString();
    }
    /**
     * 删除文件
     * @param urls 文件的访问路径列表
     */
    public void delete(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<String> keys = urls.stream()
                .map(this::extractObjectKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toList());
        if (keys.isEmpty()) {
            return;
        }
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            // 创建PutObject请求。
            DeleteObjectsResult deleteObjectsResult = ossClient.deleteObjects(new DeleteObjectsRequest(bucketName).withKeys(keys).withQuiet(false).withEncodingType("url"));
            log.info("删除成功，删除的文件数: {}，删除的文件列表: {}", deleteObjectsResult.getDeletedObjects().size(), deleteObjectsResult.getDeletedObjects());
        } catch (OSSException oe) {
            log.error("OSS拒绝删除: errorCode={}, requestId={}", oe.getErrorCode(), oe.getRequestId(), oe);
            throw new IllegalStateException("文件删除失败", oe);
        } catch (ClientException ce) {
            log.error("OSS客户端删除失败", ce);
            throw new IllegalStateException("文件删除失败", ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private String extractObjectKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(url).getPath();
            return path == null ? null : path.replaceFirst("^/", "");
        } catch (IllegalArgumentException e) {
            log.warn("忽略无效的OSS文件地址");
            return null;
        }
    }
}
