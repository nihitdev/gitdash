package dev.nihit.gitdash.service;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.assertThat;
class UpdateServiceTest {@Test void providesPlatformAppropriateFallbackPrefix(){assertThat(UpdateService.inferPrefix(false).toString()).endsWith(".local");assertThat(UpdateService.inferPrefix(true).toString()).contains("Programs").endsWith("GitDash");}}
