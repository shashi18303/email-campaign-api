package com.kasplo.emailcampaign.service;

import com.kasplo.emailcampaign.entity.Campaign;
import com.kasplo.emailcampaign.entity.Recipient;
import com.kasplo.emailcampaign.exception.BadRequestException;
import com.kasplo.emailcampaign.exception.ResourceNotFoundException;
import com.kasplo.emailcampaign.repository.CampaignRepository;
import com.kasplo.emailcampaign.repository.RecipientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;
    private final RecipientRepository recipientRepository;

    public CampaignService(CampaignRepository campaignRepository,
                           RecipientRepository recipientRepository) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
    }

    @Transactional
    public Campaign createCampaign(Campaign campaign) {
        campaign.setId(null);
        campaign.setStatus(Campaign.Status.DRAFT);
        campaign.setRecipients(new ArrayList<>());
        campaign.setCreatedAt(null);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public List<Recipient> addRecipients(String campaignId, List<Recipient> recipients) {
        Campaign campaign = getCampaign(campaignId);

        if (campaign.getStatus() != Campaign.Status.DRAFT) {
            throw new BadRequestException("Recipients can only be added to a draft campaign");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new BadRequestException("At least one recipient is required");
        }

        Set<String> requestEmails = new HashSet<>();
        for (Recipient recipient : recipients) {
            String email = normalizeEmail(recipient.getEmail());
            if (!requestEmails.add(email)
                    || recipientRepository.existsByCampaignIdAndEmailIgnoreCase(campaignId, email)) {
                throw new BadRequestException("Duplicate recipient email: " + email);
            }
            recipient.setId(null);
            recipient.setName(recipient.getName().trim());
            recipient.setEmail(email);
            recipient.setDeliveryStatus(Recipient.DeliveryStatus.PENDING);
            recipient.setCampaign(campaign);
        }

        return recipientRepository.saveAll(recipients);
    }

    @Transactional
    public Campaign scheduleCampaign(String campaignId) {
        Campaign campaign = getCampaign(campaignId);

        if (campaign.getStatus() != Campaign.Status.DRAFT) {
            throw new BadRequestException("Only a draft campaign can be scheduled");
        }
        if (campaign.getRecipients().isEmpty()) {
            throw new BadRequestException("A campaign must have at least one recipient");
        }
        if (campaign.getScheduledAt() == null
                || !campaign.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Scheduled time must be in the future");
        }

        campaign.setStatus(Campaign.Status.SCHEDULED);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public List<String> processScheduledCampaigns() {
        LocalDateTime now = LocalDateTime.now();
        List<Campaign> campaigns = campaignRepository
                .findByStatusAndScheduledAtLessThanEqual(Campaign.Status.SCHEDULED, now);
        List<String> processedCampaignIds = new ArrayList<>();

        for (Campaign campaign : campaigns) {
            // The conditional update makes claiming a campaign atomic.
            if (campaignRepository.claimScheduledCampaign(
                    campaign.getId(),
                    Campaign.Status.SCHEDULED,
                    Campaign.Status.PROCESSING) == 0) {
                continue;
            }

            campaign.setStatus(Campaign.Status.PROCESSING);
            for (Recipient recipient : campaign.getRecipients()) {
                recipient.setDeliveryStatus(ThreadLocalRandom.current().nextBoolean()
                        ? Recipient.DeliveryStatus.DELIVERED
                        : Recipient.DeliveryStatus.FAILED);
            }
            campaign.setStatus(Campaign.Status.COMPLETED);
            campaignRepository.save(campaign);
            processedCampaignIds.add(campaign.getId());
            log.info("Processed campaign {} with {} recipients",
                    campaign.getId(), campaign.getRecipients().size());
        }

        return processedCampaignIds;
    }

    @Transactional(readOnly = true)
    public Page<Campaign> listCampaigns(String statusText, String search, Pageable pageable) {
        Campaign.Status status = parseStatus(statusText);
        String campaignName = search == null ? "" : search.trim();

        if (status != null && !campaignName.isEmpty()) {
            return campaignRepository.findByStatusAndNameContainingIgnoreCase(
                    status, campaignName, pageable);
        }
        if (status != null) {
            return campaignRepository.findByStatus(status, pageable);
        }
        if (!campaignName.isEmpty()) {
            return campaignRepository.findByNameContainingIgnoreCase(campaignName, pageable);
        }
        return campaignRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignStats(String campaignId) {
        Campaign campaign = campaignRepository.findWithRecipientsById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        long delivered = campaign.getRecipients().stream()
                .filter(r -> r.getDeliveryStatus() == Recipient.DeliveryStatus.DELIVERED)
                .count();
        long failed = campaign.getRecipients().stream()
                .filter(r -> r.getDeliveryStatus() == Recipient.DeliveryStatus.FAILED)
                .count();
        long pending = campaign.getRecipients().stream()
                .filter(r -> r.getDeliveryStatus() == Recipient.DeliveryStatus.PENDING)
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("campaign", campaign);
        response.put("totalRecipients", campaign.getRecipients().size());
        response.put("deliveredCount", delivered);
        response.put("failedCount", failed);
        response.put("pendingCount", pending);
        return response;
    }

    private Campaign getCampaign(String campaignId) {
        return campaignRepository.findWithRecipientsById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
    }

    private Campaign.Status parseStatus(String statusText) {
        if (statusText == null || statusText.isBlank()) {
            return null;
        }
        try {
            return Campaign.Status.valueOf(statusText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid campaign status: " + statusText);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}