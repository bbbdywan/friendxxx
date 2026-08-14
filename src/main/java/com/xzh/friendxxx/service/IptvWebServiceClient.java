package com.xzh.friendxxx.service;

import cn.hutool.core.util.XmlUtil;
import com.xzh.friendxxx.controller.iptv.dto.IptvQueryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;

import javax.xml.xpath.XPathConstants;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
public class IptvWebServiceClient {

    @Value("${iptv.hvs.url}")
    private String serviceUrl;

    private final RestClient restClient = RestClient.create();

    public IptvQueryResponse queryByCustomerId(String customerId) {
        String requestXml = buildSoapXml(customerId, generateSequenceId());
        String responseXml = restClient.post()
                .uri(serviceUrl)
                .contentType(MediaType.parseMediaType("text/xml;charset=UTF-8"))
                .header("SOAPAction", "")
                .body(requestXml)
                .retrieve()
                .body(String.class);
        return parseResponse(responseXml);
    }

    private String buildSoapXml(String customerId, String sequenceId) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<soapenv:Envelope ");
        xml.append("xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        xml.append("xmlns:soap=\"http://soapheader.webservice.bss.hvs.huawei.com\" ");
        xml.append("xmlns:web=\"http://webservice.bss.hvs.huawei.com/\" ");
        xml.append("xmlns:bean=\"http://bean.webservice.bss.hvs.huawei.com/\">");
        xml.append("<soapenv:Header><soap:HVSSoapHeader>");
        xml.append("<version>1</version><bossID></bossID><randomValue>1</randomValue>");
        xml.append("<authenticatorSource>1</authenticatorSource>");
        xml.append("<timeStamp>").append(escapeXml(timestamp)).append("</timeStamp>");
        xml.append("</soap:HVSSoapHeader></soapenv:Header>");
        xml.append("<soapenv:Body><web:queryCustomerInfoReq>");
        xml.append("<bean:customerID>").append(escapeXml(customerId)).append("</bean:customerID>");
        xml.append("<bean:sequenceID>").append(escapeXml(sequenceId)).append("</bean:sequenceID>");
        xml.append("</web:queryCustomerInfoReq></soapenv:Body></soapenv:Envelope>");
        return xml.toString();
    }

    private IptvQueryResponse parseResponse(String responseXml) {
        IptvQueryResponse response = new IptvQueryResponse();
        if (responseXml == null || responseXml.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("WebService返回内容为空");
            return response;
        }

        Document document;
        try {
            document = XmlUtil.readXML(new StringReader(responseXml));
        } catch (Exception e) {
            throw new IllegalStateException("SOAP响应不是有效XML：" + e.getMessage(), e);
        }

        String faultString = getXmlValue(document, "faultstring");
        if (faultString != null && !faultString.isEmpty()) {
            response.setSuccess(false);
            response.setMessage("SOAP Fault：" + faultString);
            return response;
        }

        String returnCode = getXmlValue(document, "returnCode");
        response.setReturnCode(returnCode);
        response.setMessage(getXmlValue(document, "returnMessage"));
        response.setSuccess("0".equals(returnCode));
        if (response.isSuccess()) {
            response.setCustomerId(getXmlValue(document, "customerID"));
            response.setUserId(getXmlValue(document, "userID"));
            response.setDeviceId(getXmlValue(document, "deviceID"));
        } else if (returnCode == null || returnCode.isEmpty()) {
            response.setMessage("SOAP响应缺少returnCode");
        }
        return response;
    }

    private String getXmlValue(Document document, String nodeName) {
        String xpath = "string(//*[local-name()='" + nodeName + "'][1])";
        Object value = XmlUtil.getByXPath(xpath, document, XPathConstants.STRING);
        String text = value == null ? null : value.toString().trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private String generateSequenceId() {
        return "gz" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
