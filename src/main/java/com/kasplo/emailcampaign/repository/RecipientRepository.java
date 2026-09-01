package com.kasplo.emailcampaign.repository;

import com.kasplo.emailcampaign.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<Recipient, String> {
    boolean existsByCampaignIdAndEmailIgnoreCase(String campaignId, String email);
}