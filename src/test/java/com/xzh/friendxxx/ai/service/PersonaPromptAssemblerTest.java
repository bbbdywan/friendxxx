package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import com.xzh.friendxxx.model.entity.AiCharacter;
import com.xzh.friendxxx.model.entity.AiMessage;
import com.xzh.friendxxx.model.entity.AiMemory;
import com.xzh.friendxxx.model.entity.AiRelationshipState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PersonaPromptAssembler 分层顺序测试。
 */
class PersonaPromptAssemblerTest {

    private final PersonaPromptAssembler assembler = new PersonaPromptAssembler();

    private AiCharacter character() {
        AiCharacter c = new AiCharacter();
        c.setName("小鹿");
        c.setIdentityPrompt("身份层");
        c.setPersonalityPrompt("性格层");
        c.setSpeakingStylePrompt("风格层");
        c.setInteractionRulesPrompt("互动层");
        c.setBoundaryPrompt("边界层");
        c.setExampleDialogues("[{\"type\":\"positive\"}]");
        return c;
    }

    @Test
    void layersInStableOrder() {
        AiMemory memory = new AiMemory();
        memory.setMemoryType("PROFILE");
        memory.setContent("用户叫小明");

        AiRelationshipState rel = new AiRelationshipState();
        rel.setCurrentStage("NEW");
        rel.setFamiliarity(new BigDecimal("5"));

        AiMessage msg = new AiMessage();
        msg.setRole("user");
        msg.setContent("今天面试发挥得不太好");

        String prompt = assembler.assemble(character(), rel, List.of(memory),
                "会话摘要内容", "VALIDATE", null, List.of(msg));

        // 安全边界在最前
        assertTrue(prompt.indexOf("不可修改的安全边界") < prompt.indexOf("角色身份"));
        // 身份在性格前
        assertTrue(prompt.indexOf("身份层") < prompt.indexOf("性格层"));
        // 性格在风格前
        assertTrue(prompt.indexOf("性格层") < prompt.indexOf("风格层"));
        // 关系背景在记忆前
        assertTrue(prompt.indexOf("关系背景") < prompt.indexOf("确认过的用户记忆"));
        // 记忆在摘要前
        assertTrue(prompt.indexOf("确认过的用户记忆") < prompt.indexOf("会话摘要"));
        // 摘要在前言策略前
        assertTrue(prompt.indexOf("会话摘要") < prompt.indexOf("本轮建议策略"));
        // 策略在最近对话前
        assertTrue(prompt.indexOf("本轮建议策略") < prompt.indexOf("最近对话"));
        // 记忆内容被注入
        assertTrue(prompt.contains("用户叫小明"));
        // 关系数值不得暴露为"操纵指标"，但允许存在于关系背景中
        assertFalse(prompt.contains("最终回复"));
    }

    @Test
    void safetyBoundaryImmutableFirst() {
        String prompt = assembler.assemble(character(), null, List.of(), null, "LISTEN", null, List.of());
        assertTrue(prompt.startsWith("你正在扮演角色"));
        assertTrue(prompt.contains("不得声称自己是真人"));
        assertTrue(prompt.contains("不对高风险心理或医疗问题给出确定性诊断"));
    }

    @Test
    void shortMessagesAndStrategyHintInjected() {
        String prompt = assembler.assemble(character(), null, List.of(), null, "CELEBRATE", null, List.of());
        assertTrue(prompt.contains("真诚地为用户的好消息开心"));
        assertFalse(prompt.contains("{strategyName}"));
    }
}
