package com.deanrobin.yx.repository;

import com.deanrobin.yx.model.TweetRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TweetRecordRepository extends JpaRepository<TweetRecord, Long> {
    boolean existsByTweetId(String tweetId);
    Optional<TweetRecord> findByTweetId(String tweetId);
}
