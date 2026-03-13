package com.deanrobin.yx.notify;

public interface Notifier {
    boolean isEnabled();
    void send(NotifyMessage message);
}
