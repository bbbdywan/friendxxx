package com.xzh.friendxxx.common.utils;




import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * SSL 工具类
 * 用于绕过 HTTPS 证书验证
 */
public class SSLUtil {

    /**
     * 创建一个忽略证书校验的 SSLContext
     *
     * @return SSLContext 可用于 HttpClient
     * @throws Exception 异常抛出
     */
    public static SSLContext createIgnoreVerifySSL() throws Exception {
        // 实现 X509TrustManager，跳过证书验证
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // 不校验客户端证书
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // 不校验服务端证书
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };

        // 初始化 SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustManager}, new java.security.SecureRandom());
        return sslContext;
    }
}


