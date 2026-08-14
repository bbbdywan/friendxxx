package com.xzh.friendxxx.ai.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseFrameDecoderTest {

    @Test
    void decodesFrameSplitAcrossNetworkChunks() {
        SseFrameDecoder decoder = new SseFrameDecoder();

        assertEquals(List.of(), decoder.accept("data: {\"choices\":[{\"delta\":{\"con"));
        assertEquals(List.of("{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"),
                decoder.accept("tent\":\"你好\"}}]}\n\n"));
    }

    @Test
    void decodesMultipleFramesAndDoneFromOneChunk() {
        SseFrameDecoder decoder = new SseFrameDecoder();

        assertEquals(List.of("{\"n\":1}", "{\"n\":2}", "[DONE]"),
                decoder.accept("data: {\"n\":1}\n\ndata: {\"n\":2}\n\ndata: [DONE]\n\n"));
    }

    @Test
    void supportsCrLfCommentsAndMultiLineData() {
        SseFrameDecoder decoder = new SseFrameDecoder();
        List<String> result = new ArrayList<>();

        result.addAll(decoder.accept(": keepalive\r\ndata: first\r\n"));
        result.addAll(decoder.accept("data: second\r\n\r\n"));

        assertEquals(List.of("first\nsecond"), result);
    }
}
