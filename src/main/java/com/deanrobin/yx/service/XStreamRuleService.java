package com.deanrobin.yx.service;

import com.deanrobin.yx.repository.MonitoredAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理 X Filtered Stream 过滤规则。
 * 规则来源：数据库中已启用的监控账户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XStreamRuleService {

    private final BearerTokenProvider bearerTokenProvider;
    private final MonitoredAccountRepository accountRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String RULES_URL = "https://api.twitter.com/2/tweets/search/stream/rules";

    // 当前生效的 handle 集合（内存缓存，用于比对是否需要更新规则）
    private Set<String> activeHandles = new HashSet<>();

    /**
     * 同步规则：从 DB 加载启用账户，与当前规则比对，有变化才更新。
     * 初次调用或账户列表变化时重建规则。
     */
    public synchronized boolean syncRulesFromDb() throws Exception {
        List<String> dbHandles = accountRepository.findByEnabledTrue()
                .stream()
                .map(a -> a.getHandle().toLowerCase())
                .sorted()
                .collect(Collectors.toList());

        Set<String> dbHandleSet = new HashSet<>(dbHandles);

        if (dbHandleSet.equals(activeHandles)) {
            log.debug("Stream rules unchanged, skip update.");
            return false;
        }

        log.info("Detected account list change: {} → {}", activeHandles, dbHandleSet);
        deleteAllRules();

        if (!dbHandles.isEmpty()) {
            addRules(dbHandles);
        }

        activeHandles = dbHandleSet;
        return true;
    }

    private void deleteAllRules() throws Exception {
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(RULES_URL))
                .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                .GET().build();

        HttpResponse<String> res = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(res.body());
        JsonNode data = root.path("data");

        if (!data.isArray() || data.isEmpty()) {
            log.info("No existing stream rules to delete.");
            return;
        }

        List<String> ids = new ArrayList<>();
        data.forEach(rule -> ids.add(rule.path("id").asText()));

        Map<String, Object> body = Map.of("delete", Map.of("ids", ids));
        String json = objectMapper.writeValueAsString(body);

        HttpRequest delReq = HttpRequest.newBuilder()
                .uri(URI.create(RULES_URL))
                .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();

        httpClient.send(delReq, HttpResponse.BodyHandlers.ofString());
        log.info("Deleted {} old stream rules.", ids.size());
    }

    private void addRules(List<String> handles) throws Exception {
        // X 单条规则最长 512 字符，按需分批
        List<String> rules = buildRuleBatches(handles);

        List<Map<String, String>> addList = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            addList.add(Map.of("value", rules.get(i), "tag", "yx-monitor-" + i));
        }

        Map<String, Object> body = Map.of("add", addList);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(RULES_URL))
                .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("Added {} stream rule(s) for {} accounts. Response: {}",
                rules.size(), handles.size(), res.body());
    }

    /**
     * 将 handle 列表拆分为不超过 512 字符的规则批次
     */
    private List<String> buildRuleBatches(List<String> handles) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String handle : handles) {
            String part = "from:" + handle;
            if (current.length() == 0) {
                current.append(part);
            } else if (current.length() + 4 + part.length() <= 512) {
                current.append(" OR ").append(part);
            } else {
                batches.add(current.toString());
                current = new StringBuilder(part);
            }
        }
        if (current.length() > 0) batches.add(current.toString());
        return batches;
    }

    public Set<String> getActiveHandles() {
        return Collections.unmodifiableSet(activeHandles);
    }
}
