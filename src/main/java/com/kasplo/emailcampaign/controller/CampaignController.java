package com.kasplo.emailcampaign.controller;

import com.kasplo.emailcampaign.entity.Campaign;
import com.kasplo.emailcampaign.entity.Recipient;
import com.kasplo.emailcampaign.exception.BadRequestException;
import com.kasplo.emailcampaign.service.CampaignService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    // 1. Create Campaign
    @PostMapping
    public ResponseEntity<Campaign> createCampaign(@Valid @RequestBody Campaign campaign) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(campaign));
    }

    // 2. Add Recipients
    @PostMapping("/{campaignId}/recipients")
    public ResponseEntity<List<Recipient>> addRecipients(
            @PathVariable String campaignId,
            @RequestBody @Valid List<Recipient> recipients) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.addRecipients(campaignId, recipients));
    }

    // 3. Schedule Campaign
    @PostMapping("/{campaignId}/schedule")
    public ResponseEntity<Campaign> scheduleCampaign(@PathVariable String campaignId) {
        return ResponseEntity.ok(campaignService.scheduleCampaign(campaignId));
    }

    // 4. Simulate Processing
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processCampaigns() {
        List<String> processed = campaignService.processScheduledCampaigns();
        return ResponseEntity.ok(Map.of("message", "Processed successfully", "processedCampaignIds", processed));
    }

    // 5. Campaign Listing (Pagination, Filtering, Search, Sorting)
    @GetMapping
    public ResponseEntity<Page<Campaign>> listCampaigns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "desc") String sort) {

        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("Size must be between 1 and 100");
        }

        Sort.Direction direction = sort.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));

        return ResponseEntity.ok(campaignService.listCampaigns(status, search, pageable));
    }

    // 6. Campaign Details & Statistics
    @GetMapping("/{campaignId}")
    public ResponseEntity<Map<String, Object>> getCampaignDetails(@PathVariable String campaignId) {
        return ResponseEntity.ok(campaignService.getCampaignStats(campaignId));
    }

    @GetMapping("/{campaignId}/statistics")
    public ResponseEntity<Map<String, Object>> getCampaignStatistics(@PathVariable String campaignId) {
        return ResponseEntity.ok(campaignService.getCampaignStats(campaignId));
    }
}