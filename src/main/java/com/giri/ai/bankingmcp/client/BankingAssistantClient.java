package com.giri.ai.bankingmcp.client;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class BankingAssistantClient {

    private final RestClient restClient;
    private final String adminApiKey;

    public BankingAssistantClient(@Value("${banking.assistant.base-url}") String baseUrl,
                                  @Value("${banking.assistant.admin-api-key}") String adminApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.adminApiKey = adminApiKey;
    }

    public record ChatRequest(String conversationId, String message) {}
    public record SourceReference(String source, String docType, Integer chunkIndex) {}
    public record ChatResponse(String answer, List<SourceReference> sources) {}
    public record UploadResponse(String documentId, String filename, String message) {}
    public record AskRequest(String question) {}

    public ChatResponse askFaq(String question) {
        String conversationId = UUID.randomUUID().toString();

        ChatRequest request = new ChatRequest(conversationId, question);

        ChatResponse response= restClient.post()
                .uri("/api/chat")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        logInteraction(conversationId, question, response.sources, response.answer);

        return response;
    }

    public ChatResponse askAboutDocument(String documentId, String question) {
        AskRequest request = new AskRequest(question);

        ChatResponse response=  restClient.post()
                .uri("/api/documents/{documentId}/ask", documentId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        logInteraction("N/A", question, response.sources, response.answer);

        return response;
    }

    public UploadResponse uploadDocument(String filename, byte[] fileContent) {
        ByteArrayResource resource = new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        return restClient.post()
                .uri("/api/documents/upload")
                .header("X-Admin-Api-Key", adminApiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(UploadResponse.class);
    }

    private void logInteraction(String conversationId, String userMessage,
                                List<SourceReference> source, String response) {
        System.out.printf("""
            [Conversation: %s]
            Question: %s
            Retrieved chunks: %d
            Sources: %s
            Answer: %s
            ---
            """,
                conversationId, userMessage, source.size(),
                source.stream().map(d -> d.source).toList(),
                response);
    }

}