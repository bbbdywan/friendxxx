package com.xzh.friendxxx.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class PageDTO {

    private Integer pageNum;

    private Integer pageSize;

    private String username;

    private LocalDateTime createTimeBegin;

    private LocalDateTime createTimeEnd;
}
