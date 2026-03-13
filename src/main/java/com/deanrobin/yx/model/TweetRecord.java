package com.deanrobin.yx.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tweet_record")
public class TweetRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tweetId;

    private String handle;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String tweetUrl;

    private LocalDateTime tweetTime;

    private boolean notified = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}
