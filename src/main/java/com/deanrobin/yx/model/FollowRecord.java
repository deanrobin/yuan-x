package com.deanrobin.yx.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 关注关系快照记录
 * watcher_handle 关注了 following_handle
 */
@Data
@Entity
@Table(name = "follow_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"watcher_handle", "following_handle"}))
public class FollowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String watcherHandle;

    @Column(nullable = false)
    private String followingHandle;

    private String followingName;

    private String followingUserId;

    /** active = 正在关注, unfollowed = 已取关 */
    @Column(nullable = false)
    private String status = "active";

    private LocalDateTime followedAt;

    private LocalDateTime unfollowedAt;

    private LocalDateTime updatedAt = LocalDateTime.now();
}
