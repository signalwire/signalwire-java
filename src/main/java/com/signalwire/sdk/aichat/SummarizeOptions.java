/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/**
 * Sampling / prompt options for {@link AIChatClient#summarize}. All fields are optional. Build with
 * {@link #builder()}.
 */
public final class SummarizeOptions {

  private final String summaryPrompt;
  private final Double temperature;
  private final Double topP;
  private final Double frequencyPenalty;
  private final Double presencePenalty;
  private final Integer maxTokens;

  private SummarizeOptions(Builder b) {
    this.summaryPrompt = b.summaryPrompt;
    this.temperature = b.temperature;
    this.topP = b.topP;
    this.frequencyPenalty = b.frequencyPenalty;
    this.presencePenalty = b.presencePenalty;
    this.maxTokens = b.maxTokens;
  }

  /**
   * A new builder.
   *
   * @return a builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Custom prompt steering the summary (wire {@code summary_prompt}).
   *
   * @return the prompt, or {@code null}.
   */
  public String getSummaryPrompt() {
    return summaryPrompt;
  }

  /**
   * Sampling temperature (wire {@code temperature}).
   *
   * @return the temperature, or {@code null}.
   */
  public Double getTemperature() {
    return temperature;
  }

  /**
   * Nucleus-sampling top-p (wire {@code top_p}).
   *
   * @return the top-p, or {@code null}.
   */
  public Double getTopP() {
    return topP;
  }

  /**
   * Frequency penalty (wire {@code frequency_penalty}).
   *
   * @return the frequency penalty, or {@code null}.
   */
  public Double getFrequencyPenalty() {
    return frequencyPenalty;
  }

  /**
   * Presence penalty (wire {@code presence_penalty}).
   *
   * @return the presence penalty, or {@code null}.
   */
  public Double getPresencePenalty() {
    return presencePenalty;
  }

  /**
   * Max tokens for the summary (wire {@code max_tokens}).
   *
   * @return the max tokens, or {@code null}.
   */
  public Integer getMaxTokens() {
    return maxTokens;
  }

  /** Builder for {@link SummarizeOptions}. */
  public static final class Builder {
    private String summaryPrompt;
    private Double temperature;
    private Double topP;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private Integer maxTokens;

    private Builder() {}

    /**
     * @param summaryPrompt custom prompt steering the summary.
     * @return this builder.
     */
    public Builder summaryPrompt(String summaryPrompt) {
      this.summaryPrompt = summaryPrompt;
      return this;
    }

    /**
     * @param temperature sampling temperature.
     * @return this builder.
     */
    public Builder temperature(Double temperature) {
      this.temperature = temperature;
      return this;
    }

    /**
     * @param topP nucleus-sampling top-p.
     * @return this builder.
     */
    public Builder topP(Double topP) {
      this.topP = topP;
      return this;
    }

    /**
     * @param frequencyPenalty frequency penalty.
     * @return this builder.
     */
    public Builder frequencyPenalty(Double frequencyPenalty) {
      this.frequencyPenalty = frequencyPenalty;
      return this;
    }

    /**
     * @param presencePenalty presence penalty.
     * @return this builder.
     */
    public Builder presencePenalty(Double presencePenalty) {
      this.presencePenalty = presencePenalty;
      return this;
    }

    /**
     * @param maxTokens max tokens for the summary.
     * @return this builder.
     */
    public Builder maxTokens(Integer maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    /**
     * Build the options.
     *
     * @return the built options.
     */
    public SummarizeOptions build() {
      return new SummarizeOptions(this);
    }
  }
}
