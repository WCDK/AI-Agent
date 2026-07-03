package com.wcdk.ai.agent.pipeline;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record TrainingRequest(
        @NotEmpty(message = "训练样本不能为空。")
        List<@Valid TrainingSample> samples,
        Integer epochs,
        String outputDirectory
) {
}
