package com.deanrobin.yx.repository;

import com.deanrobin.yx.model.MonitoredAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoredAccountRepository extends JpaRepository<MonitoredAccount, Long> {
    List<MonitoredAccount> findByEnabledTrue();
    Optional<MonitoredAccount> findByHandle(String handle);
}
