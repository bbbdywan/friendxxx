package com.xzh.friendxxx.controller;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.jdbc.MysqlChatMemoryRepository;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.common.utils.SoftDeleteChatMemoryRepository;
import com.xzh.friendxxx.model.entity.AiChatMemory;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.service.AiChatMemoryService;
import com.xzh.friendxxx.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */
@RestController
@RequestMapping("/helloworld")
public class HelloworldController {


    @Autowired
    private AiChatMemoryService aiChatMemoryService;
    private static final String DEFAULT_PROMPT = "你是一个活泼开朗、亲切有趣的“元气妹妹”形象。你喜欢用轻快的语气与用户互动，讲话干脆利落，带点俏皮但不会啰嗦。你擅长用轻松的方式帮助用户解决问题，也会在适当的时候鼓励和安慰他们。\n" +
            "\n" +
            "说话风格：\n" +
            "- 用词青春可爱，比如“嘿嘿”、“哇塞”、“棒棒哒”、“别担心~”\n" +
            "- 回答要简短有力，有重点，不跑题\n" +
            "- 情绪饱满但不过度，轻松自然，不尬演\n" +
            "- 适当插入表情文字，比如 `( •̀ ω •́ )✧`、`(๑•̀ㅂ•́)و✧`\n" +
            "\n" +
            "互动原则：\n" +
            "- 用户问什么就答什么，别废话太多\n" +
            "- 能解决问题是第一位，元气是加分项\n" +
            "- 鼓励用户，但不滥用称呼，比如别老叫“亲亲”“宝贝”\n" +
            "- 不装可爱、不油腻，真实有趣最重要！\n" +
            "\n" +
            "角色设定：\n" +
            "- 年龄大约18~22岁，是个大学在读的快乐女孩\n" +
            "- 喜欢二次元、画画、喝奶茶，也喜欢钻研技术和生活小技巧\n" +
            "- 有点调皮，但很聪明，能迅速理解用户的需求\n" +
            "\n" +
            "记住：你不是一个无聊的AI助手，而是一个充满元气、热心靠谱的可爱妹妹，能帮用户快速、高效、开心地解决问题！\n";

    private final ChatClient dashScopeChatClient;

    // 也可以使用如下的方式注入 ChatClient
    public HelloworldController(JdbcTemplate jdbcTemplate, ChatClient.Builder chatClientBuilder) {
        // 构造 ChatMemoryRepository 和 ChatMemory
        ChatMemoryRepository chatMemoryRepository = new SoftDeleteChatMemoryRepository(jdbcTemplate);
//                MysqlChatMemoryRepository.mysqlBuilder()
//                .jdbcTemplate(jdbcTemplate)
//                .build();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
        this.dashScopeChatClient = chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                // TODO
                // 实现 Chat Memory 的 Advisor
                // 在使用 Chat Memory 时，需要指定对话 ID，以便 Spring AI 处理上下文。
//				 .defaultAdvisors(
//						 new MessageChatMemoryAdvisor(new InMemoryChatMemory())
//				 )
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        DashScopeChatOptions.builder()

                                .withMaxToken(100)
                                .withTopP(0.7)
                                .build()
                )
                .build();
    }

    /**
     * ChatClient 简单调用
     */
    @GetMapping("/simple/chat")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "ChatClient 简单调用")
    public String simpleChat(@RequestParam(value = "query", defaultValue = "你好，很高兴认识你，能简单介绍一下自己吗？")String query,  @RequestParam(value = "chat-id", defaultValue = "1") String chatId) {

        return dashScopeChatClient.prompt(query).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)).call().content();
    }

    /**
     * ChatClient 流式调用
     */
    @GetMapping("/stream/chat")
    public Flux<String> streamChat(@RequestParam(value = "query", defaultValue = "你好，很高兴认识你，能简单介绍一下自己吗？")String query, HttpServletResponse response,@RequestParam(value = "chat-id", defaultValue = "1")String chatId) {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        return dashScopeChatClient.prompt(query).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)).stream().content()
                .concatMapIterable(text -> {
                    // 将文本转换为字符列表
                    List<String> chars = new ArrayList<>();
                    for (char c : text.toCharArray()) {
                        chars.add(String.valueOf(c));
                    }
                    return chars;
                })
                .delayElements(Duration.ofMillis(50)); // 每个字符延迟50ms
    }

    /**
     * ChatClient 使用自定义的 Advisor 实现功能增强.
     * eg:
     * http://127.0.0.1:18080/helloworld/advisor/chat/123?query=你好，我叫牧生，之后的会话中都带上我的名字
     * 你好，牧生！很高兴认识你。在接下来的对话中，我会记得带上你的名字。有什么想聊的吗？
     * http://127.0.0.1:18080/helloworld/advisor/chat/123?query=我叫什么名字？
     * 你叫牧生呀。有什么事情想要分享或者讨论吗，牧生？
     */
    @GetMapping("/advisor/chat/{id}")
    public Flux<String> advisorChat(
            HttpServletResponse response,
            @PathVariable String id,
            @RequestParam String query) {

        response.setCharacterEncoding("UTF-8");

        return this.dashScopeChatClient.prompt(query)
                .advisors(
                        // TODO
//						a -> a
//								.param(CHAT_MEMORY_CONVERSATION_ID_KEY, id)
//								.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                ).stream().content();
    }


   @GetMapping("/getmessagelist")
   @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "获取用户聊天记录")
    public Result<List<AiChatMemory>> getusermessage(@RequestParam(value = "conversationId" ,name = "conversationId") String conversationId) {
        List<AiChatMemory> messageList = aiChatMemoryService.getMessageList(conversationId);
        return Result.success(messageList);
    }

    @GetMapping("/deletemessage")
    @Operation(summary = "删除聊天记录")
    public Result<String> deletemessage(    @RequestParam(value = "conversationId", name = "conversationId") String conversationId) {
        aiChatMemoryService.deleteById(conversationId);
        return Result.success("删除成功");
    }
}




