package com.deanrobin.yx.service;

import com.deanrobin.yx.notify.Notifier;
import com.deanrobin.yx.notify.NotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyDispatcher {

    private final List<Notifier> notifiers;

    public void dispatch(NotifyMessage message) {
        notifiers.stream()
                .filter(Notifier::isEnabled)
                .forEach(notifier -> {
                    try {
                        notifier.send(message);
                    } catch (Exception e) {
                        log.error("Notifier {} failed: {}", notifier.getClass().getSimpleName(), e.getMessage());
                    }
                });
    }
}
