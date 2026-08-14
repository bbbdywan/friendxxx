package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.utils.Result;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/search")
public class SearchController {
    private static final String NEWS_URL =
            "http://101.35.2.25/api/xinwen/weibo2.php?id=10007198&key=e3d0a2bd0a403d288f872038f387cce5";

    private final RestTemplate restTemplate;

    public SearchController(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @GetMapping("/news")
    public Result<String> news() {
        return Result.success(restTemplate.getForObject(NEWS_URL, String.class));
    }
}
