package com.wcdk.ai.aiagenttest.agent.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public class EdgeTtsService {

    private final WcdkProperties properties;

    public EdgeTtsService(WcdkProperties properties) {
        this.properties = properties;
    }


    public byte[] synthesize(TtsRequest request) {
        var tts = properties.getAgent().getTts();
        var text = normalizeText(request.text(), tts.getMaxTextLength());
        var voice = StringUtils.hasText(request.voice()) ? request.voice().trim() : tts.getVoice();
        var timeout = Duration.ofSeconds(tts.getTimeoutSeconds());

        try {
            var output = Files.createTempFile("edge-tts-", ".mp3");
            try {
                var command = new ArrayList<String>();
                command.add(tts.getCommand());
                command.add("--voice");
                command.add(voice);
                command.add("--text");
                command.add(text);
                command.add("--write-media");
                command.add(output.toString());

                var process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();

                if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("edge-tts timed out.");
                }

                var processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (process.exitValue() != 0) {
                    throw new IllegalStateException("edge-tts failed: " + processOutput.strip());
                }

                return Files.readAllBytes(output);
            } finally {
                Files.deleteIfExists(output);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("edge-tts is not available. Install it with: pip install edge-tts", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("edge-tts was interrupted.", exception);
        }
    }

    private String normalizeText(String text, int maxTextLength) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text must not be blank.");
        }
        var normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() > maxTextLength) {
            return normalized.substring(0, maxTextLength);
        }
        return normalized;
    }
}
