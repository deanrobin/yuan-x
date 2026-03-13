package com.deanrobin.yx.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 被监控关注动态的账户
 * 例如：监控 @elonmusk 关注/取关了谁
 */
@Data
@Entity
@Table(name = "follow_watcher")
public class FollowWatcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String handle;

    private String name;

    private String xUserId;

    private boolean enabled = true;

    private LocalDateTime lastCheckedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
