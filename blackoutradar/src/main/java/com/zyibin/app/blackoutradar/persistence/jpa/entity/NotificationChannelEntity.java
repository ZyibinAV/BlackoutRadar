package com.zyibin.app.blackoutradar.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_channel")
@Getter
@Setter
public class NotificationChannelEntity extends AbstractTimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
