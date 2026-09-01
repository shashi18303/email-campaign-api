package com.kasplo.emailcampaign.repository;

import com.kasplo.emailcampaign.entity.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, String> {

    List<Campaign> findByStatusAndScheduledAtLessThanEqual(
            Campaign.Status status, LocalDateTime dateTime);

    Page<Campaign> findByStatus(Campaign.Status status, Pageable pageable);

    Page<Campaign> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Campaign> findByStatusAndNameContainingIgnoreCase(
            Campaign.Status status, String name, Pageable pageable);

    @Query("select distinct c from Campaign c left join fetch c.recipients where c.id = :id")
    Optional<Campaign> findWithRecipientsById(@Param("id") String id);

    @Modifying
    @Query("update Campaign c set c.status = :processing " +
            "where c.id = :id and c.status = :scheduled")
    int claimScheduledCampaign(@Param("id") String id,
                               @Param("scheduled") Campaign.Status scheduled,
                               @Param("processing") Campaign.Status processing);
}