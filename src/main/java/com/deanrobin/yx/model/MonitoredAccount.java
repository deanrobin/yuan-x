package com.deanrobin.yx.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "monitored_account")
public class MonitoredAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String handle;          // @handle（不含@）

    private String name;            // 备注名称

    private String xUserId;         // X 平台的 user_id（首次查询后缓存）

    private String notes;           // 备注（如：BTC KOL、AI领域等）

    private String lastTweetId;     // 最后一条已处理的 tweet_id

    private boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();
}
