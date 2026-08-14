package com.xzh.friendxxx.controller.iptv.dto;

import lombok.Data;

@Data
public class IptvQueryResponse {
    private boolean success;
    private String returnCode;
    private String message;
    private String customerId;
    private String userId;
    private String deviceId;
}
