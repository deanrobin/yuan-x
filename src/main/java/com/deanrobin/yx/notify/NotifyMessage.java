package com.deanrobin.yx.notify;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotifyMessage {
    private String tweetId;
    private String handle;
    private String displayName;
    private String content;
    private String tweetUrl;
    private String tweetTime;
}
