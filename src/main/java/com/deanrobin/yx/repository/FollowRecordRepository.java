package com.deanrobin.yx.repository;

import com.deanrobin.yx.model.FollowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRecordRepository extends JpaRepository<FollowRecord, Long> {
    List<FollowRecord> findByWatcherHandleAndStatus(String watcherHandle, String status);
    Optional<FollowRecord> findByWatcherHandleAndFollowingHandle(String watcherHandle, String followingHandle);
}
