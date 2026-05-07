package com.xzh.friendxxx.service;

import com.github.pagehelper.PageInfo;
import com.xzh.friendxxx.model.dto.PageDTO;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import com.xzh.friendxxx.model.dto.RecommendRequest;
import com.xzh.friendxxx.model.vo.RecommendUserVO;

import java.util.List;

/**
* @author bb
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-07-17 17:48:09
*/
public interface UserService extends IService<User> {

    List<User> findUserByTag();

    User login(UserDTO userDTO);

    int updateuser(User user);

    String uploadAvatar(MultipartFile file);

    long getid(String userAccount);

    void removebyAccount(String account);

    PageInfo<User> findUserByTag(Integer pageNum, Integer pageSize);

    PageInfo<User> selectuser(PageDTO pageDTO);

    List<RecommendUserVO> recommend(RecommendRequest request);
}
