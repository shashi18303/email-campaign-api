package com.kasplo.emailcampaign.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "recipients", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"campaign_id", "email"})
})
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Recipient name is required")
    @Column(nullable = false)
    private String name;

    @Email(message = "Invalid recipient email")
    @NotBlank(message = "Recipient email is required")
    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    @JsonIgnore
    private Campaign campaign;

    public enum DeliveryStatus {
        PENDING, DELIVERED, FAILED
    }

    public Recipient() {}

    public Recipient(String id, String name, String email, DeliveryStatus deliveryStatus, Campaign campaign) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.deliveryStatus = deliveryStatus;
        this.campaign = campaign;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
}