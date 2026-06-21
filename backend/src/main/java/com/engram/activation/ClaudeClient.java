package com.engram.activation;

public interface ClaudeClient {
    LlmResponse complete(ModelTier tier, String system, String userText, int maxTokens);
}
