package com.xzh.friendxxx.controller;

//import com.xzh.friendxxx.Repository.EsPostRepository;
//import com.xzh.friendxxx.Repository.EsUserRepository;
import com.xzh.friendxxx.common.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/search")
public class SearchController {
//
//    @Autowired
//    private EsPostRepository postRepository;
//    @Autowired
//    private EsUserRepository esUserRepository;


    private final RestTemplate restTemplate;

    public SearchController(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }
//
//    @GetMapping
//    public Object search(@RequestParam String query,
//                         @RequestParam(defaultValue = "post") String type,
//                         @RequestParam(defaultValue = "0") int page,
//                         @RequestParam(defaultValue = "10") int size) {
//
//        Pageable pageable = PageRequest.of(page, size);
//
//        if ("post".equalsIgnoreCase(type)) {
//            return postRepository.findByContentContaining(query, pageable);
//        } else if ("user".equalsIgnoreCase(type)) {
//            return esUserRepository.findByUsernameContaining(query, pageable);
//        } else {
//            throw new IllegalArgumentException("Invalid type: " + type);
//        }
//    }

    @GetMapping("/news")
    public Result<String> getnews()
    {
        //接口已失效
        //2025年9月17日14:21:25
        String baseurl = "http://101.35.2.25/api/xinwen/weibo2.php";
        String id = "10007198";
        String key = "e3d0a2bd0a403d288f872038f387cce5";
        String url=String.format("%s?id=%s&key=%s", baseurl, id, key);
        String forObject = restTemplate.getForObject(url, String.class);
        return  Result.success(forObject);
    }


}