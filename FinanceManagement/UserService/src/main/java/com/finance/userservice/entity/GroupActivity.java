package com.finance.userservice.entity;

import com.finance.userservice.constant.GroupActivityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_group_activities", indexes = {
        @Index(name = "ix_group_activities_group_id_created_at", columnList = "group_id, createdAt")
})
public class GroupActivity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private GroupActivityType type;

    @Column(name = "message", length = 500)
    private String message;

    /**
     * JSON metadata for extra context (targetUserId, accountNumber, bankName, etc.)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}


