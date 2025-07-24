package com.xzh.friendxxx;

import org.springframework.util.DigestUtils;

public class md5Test {
    public static void main(String[] args) {
        String md5Password = DigestUtils.md5DigestAsHex("12345678".getBytes());
        System.out.println(md5Password);
    }
}
