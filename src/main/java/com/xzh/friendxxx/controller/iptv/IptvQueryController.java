package com.xzh.friendxxx.controller.iptv;

import com.xzh.friendxxx.controller.iptv.dto.IptvQueryRequest;
import com.xzh.friendxxx.controller.iptv.dto.IptvQueryResponse;
import com.xzh.friendxxx.service.IptvWebServiceClient;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iptv")
public class IptvQueryController {

    @Resource
    private IptvWebServiceClient iptvWebServiceClient;

    @PostMapping("/queryMac")
    public IptvQueryResponse queryMac(@RequestBody(required = false) IptvQueryRequest request) {
        if (request == null || request.getCustomerId() == null
                || request.getCustomerId().trim().isEmpty()) {
            IptvQueryResponse response = new IptvQueryResponse();
            response.setSuccess(false);
            response.setMessage("IPTV账号不能为空");
            return response;
        }
        try {
            return iptvWebServiceClient.queryByCustomerId(request.getCustomerId().trim());
        } catch (Exception e) {
            IptvQueryResponse response = new IptvQueryResponse();
            response.setSuccess(false);
            response.setMessage("IPTV接口调用失败：" + e.getMessage());
            return response;
        }
    }
}
