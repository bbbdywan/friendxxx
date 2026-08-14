package com.xzh.friendxxx.controller.mock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * IPTV HVS接口本地Mock服务。
 *
 * 接收与正式HVS接口相同的SOAP 1.1报文，
 * 返回与正式接口相同结构的SOAP响应。
 */
@Controller
@RequestMapping("/mock")
public class HvsMockWebServiceController {

    /**
     * 模拟数据库。
     *
     * key：IPTV客户账号
     * value：机顶盒MAC
     */
    private static final Map<String, String> CUSTOMER_MAC_MAP =
            new HashMap<String, String>();

    static {
        CUSTOMER_MAC_MAP.put(
                "test20230314",
                "9C2F4E900F63"
        );

        CUSTOMER_MAC_MAP.put(
                "71002722102957484@071",
                "AABBCCDDEEFF"
        );

        CUSTOMER_MAC_MAP.put(
                "071600081374@071",
                "001122334455"
        );
    }

    /**
     * 模拟正式HVS WebService。
     *
     * 本地地址：
     * http://localhost:8080/项目上下文/mock/HVSService
     */
    @PostMapping(
            value = "/HVSService",
            consumes = "text/xml",
            produces = "text/xml;charset=UTF-8"
    )
    public ResponseEntity<String> queryCustomerInfo(
            @RequestBody String requestXml
    ) {
        try {
            System.out.println("收到SOAP请求：");
            System.out.println(requestXml);

            String customerId = getXmlValue(
                    requestXml,
                    "customerID"
            );

            String sequenceId = getXmlValue(
                    requestXml,
                    "sequenceID"
            );

            if (customerId == null || customerId.trim().isEmpty()) {
                return createSoapFault(
                        "Client",
                        "customerID不能为空"
                );
            }

            String mac = CUSTOMER_MAC_MAP.get(customerId);

            String responseXml;

            if (mac == null) {
                responseXml = buildBusinessErrorResponse(
                        "1001",
                        "未查询到IPTV账号对应的用户信息"
                );
            } else {
                responseXml = buildSuccessResponse(
                        customerId,
                        sequenceId,
                        mac
                );
            }

            return createXmlResponse(
                    responseXml,
                    HttpStatus.OK
            );

        } catch (Exception e) {
            e.printStackTrace();

            return createSoapFault(
                    "Server",
                    "Mock服务处理异常：" + e.getMessage()
            );
        }
    }

    /**
     * 构造成功响应。
     */
    private String buildSuccessResponse(
            String customerId,
            String sequenceId,
            String deviceId
    ) {
        String userId;

        if (customerId.contains("@")) {
            userId = customerId;
        } else {
            userId = customerId + "@071";
        }

        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        xml.append(
                "<soap:Envelope "
                        + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        );

        xml.append("<soap:Body>");

        xml.append(
                "<ns3:queryCustomerResp "
                        + "xmlns:ns2=\"http://bean.webservice.bss.hvs.huawei.com/\" "
                        + "xmlns:ns3=\"http://webservice.bss.hvs.huawei.com/\" "
                        + "xmlns:ns4=\"http://soapheader.webservice.bss.hvs.huawei.com\">"
        );

        xml.append("<ns2:resultInfo>");
        xml.append("<ns2:returnCode>0</ns2:returnCode>");
        xml.append("<ns2:returnMessage>操作成功.</ns2:returnMessage>");
        xml.append("</ns2:resultInfo>");

        xml.append("<ns2:userInfoArray>");
        xml.append("<ns2:item>");

        xml.append("<ns2:customerID>")
                .append(escapeXml(customerId))
                .append("</ns2:customerID>");

        xml.append("<ns2:userID>")
                .append(escapeXml(userId))
                .append("</ns2:userID>");

        xml.append("<ns2:userName>")
                .append(escapeXml(customerId))
                .append("</ns2:userName>");

        xml.append("<ns2:state>F0A</ns2:state>");
        xml.append("<ns2:payType>1</ns2:payType>");
        xml.append("<ns2:openTime>20230314111417</ns2:openTime>");
        xml.append("<ns2:orderLevel>999</ns2:orderLevel>");

        xml.append("<ns2:deviceID>")
                .append(escapeXml(deviceId))
                .append("</ns2:deviceID>");

        xml.append("<ns2:userAreaID>1112</ns2:userAreaID>");
        xml.append("<ns2:userGroupID>0</ns2:userGroupID>");

        xml.append("</ns2:item>");
        xml.append("</ns2:userInfoArray>");

        /*
         * 原文档的响应没有sequenceID。
         * 因此这里也不返回，以保证响应结构一致。
         */

        xml.append("</ns3:queryCustomerResp>");
        xml.append("</soap:Body>");
        xml.append("</soap:Envelope>");

        return xml.toString();
    }

    /**
     * 构造业务失败响应。
     *
     * HTTP状态仍然是200，
     * 业务是否成功由returnCode判断。
     */
    private String buildBusinessErrorResponse(
            String returnCode,
            String returnMessage
    ) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        xml.append(
                "<soap:Envelope "
                        + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        );

        xml.append("<soap:Body>");

        xml.append(
                "<ns3:queryCustomerResp "
                        + "xmlns:ns2=\"http://bean.webservice.bss.hvs.huawei.com/\" "
                        + "xmlns:ns3=\"http://webservice.bss.hvs.huawei.com/\">"
        );

        xml.append("<ns2:resultInfo>");

        xml.append("<ns2:returnCode>")
                .append(escapeXml(returnCode))
                .append("</ns2:returnCode>");

        xml.append("<ns2:returnMessage>")
                .append(escapeXml(returnMessage))
                .append("</ns2:returnMessage>");

        xml.append("</ns2:resultInfo>");

        xml.append("</ns3:queryCustomerResp>");
        xml.append("</soap:Body>");
        xml.append("</soap:Envelope>");

        return xml.toString();
    }

    /**
     * 构造SOAP Fault。
     *
     * SOAP Fault一般返回HTTP 500。
     */
    private ResponseEntity<String> createSoapFault(
            String faultCode,
            String faultString
    ) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        xml.append(
                "<soap:Envelope "
                        + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        );

        xml.append("<soap:Body>");
        xml.append("<soap:Fault>");

        xml.append("<faultcode>soap:")
                .append(escapeXml(faultCode))
                .append("</faultcode>");

        xml.append("<faultstring>")
                .append(escapeXml(faultString))
                .append("</faultstring>");

        xml.append("</soap:Fault>");
        xml.append("</soap:Body>");
        xml.append("</soap:Envelope>");

        return createXmlResponse(
                xml.toString(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<String> createXmlResponse(
            String xml,
            HttpStatus status
    ) {
        HttpHeaders headers = new HttpHeaders();

        headers.set(
                HttpHeaders.CONTENT_TYPE,
                "text/xml;charset=UTF-8"
        );

        return new ResponseEntity<String>(
                xml,
                headers,
                status
        );
    }

    /**
     * 按节点本地名称获取XML内容。
     *
     * 不依赖soapenv、bean等前缀。
     */
    private String getXmlValue(
            String xml,
            String nodeName
    ) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);

        // 防止XXE
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)
                );

        try {
            Document document = builder.parse(inputStream);

            NodeList nodeList =
                    document.getElementsByTagNameNS(
                            "*",
                            nodeName
                    );

            if (nodeList.getLength() == 0) {
                return null;
            }

            String value =
                    nodeList.item(0).getTextContent();

            return value == null
                    ? null
                    : value.trim();

        } finally {
            inputStream.close();
        }
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
