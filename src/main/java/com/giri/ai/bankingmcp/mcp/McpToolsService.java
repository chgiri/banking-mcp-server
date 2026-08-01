package com.giri.ai.bankingmcp.mcp;

import com.giri.ai.bankingmcp.client.BankingAssistantClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class McpToolsService {

    private final BankingAssistantClient bankingAssistantClient;

    public McpToolsService(BankingAssistantClient bankingAssistantClient) {
        this.bankingAssistantClient = bankingAssistantClient;
    }

    @McpTool(description = "Answer a banking customer's question using the bank's official FAQ knowledge base " +
            "(FD withdrawal policy, account fees, loan FAQs). Only answers from official documents — " +
            "does not guess if the answer isn't covered.")
    public String answerBankingFaq(
            @McpToolParam(description = "The customer's question about banking policies, fees, or FAQs", required = true)
            String question) {

        BankingAssistantClient.ChatResponse response = bankingAssistantClient.askFaq(question);
        return response.answer();
    }

    @McpTool(description = "Answer a question about a specific previously-uploaded document (e.g. a loan agreement " +
            "or terms & conditions PDF), using its documentId. Only answers from that document's actual content — " +
            "does not guess if the answer isn't covered, and cannot see any other document's content.")
    public String askAboutDocument(
            @McpToolParam(description = "The documentId returned when the document was uploaded", required = true)
            String documentId,
            @McpToolParam(description = "The question to ask about this specific document", required = true)
            String question) {

        BankingAssistantClient.ChatResponse response = bankingAssistantClient.askAboutDocument(documentId, question);
        return response.answer();
    }

    @McpTool(description = "Upload a new PDF document (e.g. a loan agreement or terms & conditions) so it can " +
            "be queried afterward with askAboutDocument. Provide the original file name and the file's content " +
            "encoded as a Base64 string. Returns a documentId — save this and use it with askAboutDocument to " +
            "ask questions about the uploaded document.")
    public String uploadDocument(
            @McpToolParam(description = "The original file name, e.g. loan-agreement.pdf", required = true)
            String fileName,
            @McpToolParam(description = "The PDF file's raw bytes, encoded as a Base64 string", required = true)
            String base64Content) {

        byte[] fileBytes = java.util.Base64.getDecoder().decode(base64Content);
        BankingAssistantClient.UploadResponse response = bankingAssistantClient.uploadDocument(fileName, fileBytes);

        return "Document uploaded successfully. documentId: " + response.documentId()
                + ". Use this ID with askAboutDocument to ask questions about it.";
    }
}