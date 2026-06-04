package com.wcdk.ai.aiagenttest.agent.pipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Component;

@Component
public class LearningModule {

    private final Map<String, LongAdder> actionCounts = new ConcurrentHashMap<>();
    private volatile FeedbackModule.FeedbackRecord lastFeedback;

    public void learn(FeedbackModule.FeedbackRecord feedback) {
        if (feedback == null) {
            return;
        }
        actionCounts.computeIfAbsent(feedback.action(), ignored -> new LongAdder()).increment();
        lastFeedback = feedback;
    }

    public Map<String, Long> actionSnapshot() {
        var snapshot = new ConcurrentHashMap<String, Long>();
        actionCounts.forEach((action, count) -> snapshot.put(action, count.sum()));
        return snapshot;
    }

    public FeedbackModule.FeedbackRecord lastFeedback() {
        return lastFeedback;
    }
}
