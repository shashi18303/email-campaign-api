package com.kasplo.emailcampaign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasplo.emailcampaign.entity.Campaign;
import com.kasplo.emailcampaign.entity.Recipient;
import com.kasplo.emailcampaign.repository.CampaignRepository;
import com.kasplo.emailcampaign.repository.RecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RecipientRepository recipientRepository;

    @BeforeEach
    void clearDatabase() {
        recipientRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    void createsValidCampaignAsDraft() throws Exception {
        String campaignId = createCampaign();

        mockMvc.perform(get("/api/campaigns/" + campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaign.status", is("DRAFT")))
                .andExpect(jsonPath("$.totalRecipients", is(0)));
    }

    @Test
    void rejectsInvalidCampaignInput() throws Exception {
        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "subject": "Subject",
                                  "senderEmail": "not-an-email",
                                  "content": "Content",
                                  "scheduledAt": "2030-01-01T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    void preventsDuplicateRecipients() throws Exception {
        String campaignId = createCampaign();
        String recipients = """
                [
                  {"name": "First", "email": "person@example.com"},
                  {"name": "Same Person", "email": "PERSON@example.com"}
                ]
                """;

        mockMvc.perform(post("/api/campaigns/" + campaignId + "/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recipients))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/campaigns/" + campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipients", is(0)));
    }

    @Test
    void preventsSchedulingWithoutRecipients() throws Exception {
        String campaignId = createCampaign();

        mockMvc.perform(post("/api/campaigns/" + campaignId + "/schedule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("A campaign must have at least one recipient")));
    }

    @Test
    void doesNotProcessCampaignTwice() throws Exception {
        Campaign campaign = scheduledCampaignWithRecipients();

        mockMvc.perform(post("/api/campaigns/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCampaignIds", hasSize(1)))
                .andExpect(jsonPath("$.processedCampaignIds[0]", is(campaign.getId())));

        mockMvc.perform(post("/api/campaigns/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCampaignIds", hasSize(0)));
    }

    @Test
    void returnsCorrectCampaignStatistics() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setName("Statistics campaign");
        campaign.setSubject("Statistics");
        campaign.setSenderEmail("sender@example.com");
        campaign.setContent("Content");
        campaign.setScheduledAt(LocalDateTime.now().plusHours(1));
        campaign.setRecipients(new ArrayList<>());
        campaign = campaignRepository.save(campaign);

        Recipient delivered = recipient("Delivered", "delivered@example.com",
                Recipient.DeliveryStatus.DELIVERED, campaign);
        Recipient failed = recipient("Failed", "failed@example.com",
                Recipient.DeliveryStatus.FAILED, campaign);
        Recipient pending = recipient("Pending", "pending@example.com",
                Recipient.DeliveryStatus.PENDING, campaign);
        recipientRepository.saveAll(java.util.List.of(delivered, failed, pending));

        mockMvc.perform(get("/api/campaigns/" + campaign.getId() + "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipients", is(3)))
                .andExpect(jsonPath("$.deliveredCount", is(1)))
                .andExpect(jsonPath("$.failedCount", is(1)))
                .andExpect(jsonPath("$.pendingCount", is(1)));
    }

    private String createCampaign() throws Exception {
        String response = mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Welcome campaign",
                                  "subject": "Welcome",
                                  "senderEmail": "sender@example.com",
                                  "content": "Hello there",
                                  "scheduledAt": "2030-01-01T10:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asText();
    }

    private Campaign scheduledCampaignWithRecipients() {
        Campaign campaign = new Campaign();
        campaign.setName("Process campaign");
        campaign.setSubject("Process");
        campaign.setSenderEmail("sender@example.com");
        campaign.setContent("Content");
        campaign.setScheduledAt(LocalDateTime.now().minusMinutes(1));
        campaign.setStatus(Campaign.Status.SCHEDULED);
        campaign.setRecipients(new ArrayList<>());

        Recipient recipient = recipient("Recipient", "recipient@example.com",
                Recipient.DeliveryStatus.PENDING, campaign);
        campaign.getRecipients().add(recipient);
        return campaignRepository.save(campaign);
    }

    private Recipient recipient(String name, String email,
                                Recipient.DeliveryStatus status, Campaign campaign) {
        Recipient recipient = new Recipient();
        recipient.setName(name);
        recipient.setEmail(email);
        recipient.setDeliveryStatus(status);
        recipient.setCampaign(campaign);
        return recipient;
    }
}