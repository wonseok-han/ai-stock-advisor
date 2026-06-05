package com.nowini.ai.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LlmJsonTest {

    @Test
    void stripsJsonCodeFence() {
        assertEquals("{\"a\":1}", LlmJson.extract("```json\n{\"a\":1}\n```"));
    }

    @Test
    void stripsBareCodeFence() {
        assertEquals("[{\"a\":1}]", LlmJson.extract("```\n[{\"a\":1}]\n```"));
    }

    @Test
    void passesThroughPureJson() {
        assertEquals("{\"a\":1}", LlmJson.extract("{\"a\":1}"));
    }

    @Test
    void trimsSurroundingText() {
        assertEquals("{\"a\":1}", LlmJson.extract("Here is the result: {\"a\":1} done"));
    }

    @Test
    void extractsArray() {
        assertEquals("[1,2]", LlmJson.extract("```json\n[1,2]\n```"));
    }

    @Test
    void handlesNull() {
        assertEquals("", LlmJson.extract(null));
    }

    @Test
    void handlesNoJson() {
        assertEquals("no json here", LlmJson.extract("no json here"));
    }
}
