package com.xzh.friendxxx.service;

//import com.xzh.friendxxx.Repository.EsPostRepository;
import com.xzh.friendxxx.mapper.SocialPostMapper;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.entity.esentity.EsPost;
import com.xzh.friendxxx.model.entity.esentity.EsUser;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostSyncService {


    @Autowired
    private SocialPostMapper socialPostMapper;

//    @Autowired
//    private EsPostRepository esPostRepository;

//    public void syncAll() {
//        List<SocialPost> list = socialPostMapper.selectAllWithTypeHandler();
//        esPostRepository.saveAll(list.stream().map(this::convertToEsPost).collect(Collectors.toList()));
//
//    }


    private EsPost convertToEsPost(SocialPost post) {
        EsPost esPost = new EsPost();
        BeanUtils.copyProperties(post, esPost);
        return esPost;
    }
}
