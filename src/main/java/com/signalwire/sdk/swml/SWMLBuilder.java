/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for SWML documents.
 *
 * <p>Mirrors the Python reference {@code signalwire.core.swml_builder.SWMLBuilder} (which wraps an
 * {@code SWMLService}) and the Ruby {@code SignalWire::SWML::SWMLBuilder}. It delegates to an
 * underlying {@link Service} instance for the actual document creation.
 *
 * <p>The explicit verb helpers ({@link #answer()}, {@link #hangup()}, {@link #play(Map)}, {@link
 * #ai(Map)}, {@link #say(String)}) cover the common verbs; every other schema verb is dispatched
 * through {@link #verb(String, Map)} / {@link #sleepVerb(int)} — the Java analog of the Python
 * reference's runtime {@code __getattr__} verb dispatch (Java has no {@code __getattr__} / {@code
 * method_missing}, so the catch-all is a named method).
 */
public class SWMLBuilder {

  private final Service service;

  /**
   * Initialize with a {@link Service} instance to delegate to.
   *
   * @param service the SWML service to delegate document construction to
   */
  public SWMLBuilder(Service service) {
    this.service = service;
  }

  /** Expose the underlying service (tests / subclasses). */
  public Service getService() {
    return service;
  }

  // ------------------------------------------------------------------
  // Explicit verb helpers
  // ------------------------------------------------------------------

  /**
   * Add an 'answer' verb to the main section with no options.
   *
   * @return this for chaining
   */
  public SWMLBuilder answer() {
    return answer(null, null);
  }

  /**
   * Add an 'answer' verb to the main section.
   *
   * @param maxDuration maximum duration in seconds, or {@code null} to omit
   * @param codecs comma-separated list of codecs, or {@code null} to omit
   * @return this for chaining
   */
  public SWMLBuilder answer(Integer maxDuration, String codecs) {
    Map<String, Object> config = new LinkedHashMap<>();
    if (maxDuration != null) {
      config.put("max_duration", maxDuration);
    }
    if (codecs != null) {
      config.put("codecs", codecs);
    }
    service.getDocument().addVerb("answer", config);
    return this;
  }

  /**
   * Add a 'hangup' verb to the main section with no reason.
   *
   * @return this for chaining
   */
  public SWMLBuilder hangup() {
    return hangup(null);
  }

  /**
   * Add a 'hangup' verb to the main section.
   *
   * @param reason optional reason for hangup, or {@code null} to omit
   * @return this for chaining
   */
  public SWMLBuilder hangup(String reason) {
    Map<String, Object> config = new LinkedHashMap<>();
    if (reason != null) {
      config.put("reason", reason);
    }
    service.getDocument().addVerb("hangup", config);
    return this;
  }

  /**
   * Add an 'ai' verb to the main section from a pre-built config map.
   *
   * <p>Convenience overload for callers that already have the config assembled; the
   * fully-parametric overload {@link #ai(String, List, String, String, Map, Map)} builds the exact
   * wire shape.
   *
   * @param config the AI verb config (may be {@code null})
   * @return this for chaining
   */
  public SWMLBuilder ai(Map<String, Object> config) {
    service.getDocument().addVerb("ai", config != null ? config : new LinkedHashMap<>());
    return this;
  }

  /**
   * Add an 'ai' verb to the main section.
   *
   * <p>The SWML {@code ai} verb requires {@code prompt} to be an OBJECT — {@code {"text": ...}} or
   * {@code {"pom": [...]}}; a bare string is a fatal error in the AI engine, so the text/pom form
   * is wrapped accordingly.
   *
   * @param promptText text prompt (mutually exclusive with promptPom), or {@code null}
   * @param promptPom POM structure prompt (mutually exclusive with promptText), or {@code null}
   * @param postPrompt optional post-prompt text, or {@code null}
   * @param postPromptUrl optional post-prompt URL, or {@code null}
   * @param swaig optional SWAIG configuration, or {@code null}
   * @param kwargs additional AI parameters merged at the top level of the config, or {@code null}
   * @return this for chaining
   */
  public SWMLBuilder ai(
      String promptText,
      List<Map<String, Object>> promptPom,
      String postPrompt,
      String postPromptUrl,
      Map<String, Object> swaig,
      Map<String, Object> kwargs) {
    Map<String, Object> config = new LinkedHashMap<>();

    if (promptText != null) {
      Map<String, Object> prompt = new LinkedHashMap<>();
      prompt.put("text", promptText);
      config.put("prompt", prompt);
    } else if (promptPom != null) {
      Map<String, Object> prompt = new LinkedHashMap<>();
      prompt.put("pom", promptPom);
      config.put("prompt", prompt);
    }

    if (postPrompt != null) {
      Map<String, Object> pp = new LinkedHashMap<>();
      pp.put("text", postPrompt);
      config.put("post_prompt", pp);
    }
    if (postPromptUrl != null) {
      config.put("post_prompt_url", postPromptUrl);
    }
    if (swaig != null) {
      config.put("SWAIG", swaig);
    }

    // Merge any additional kwargs (parity with Python's config.update(kwargs)).
    if (kwargs != null) {
      config.putAll(kwargs);
    }

    service.getDocument().addVerb("ai", config);
    return this;
  }

  /**
   * Add a 'play' verb to the main section from a pre-built config map.
   *
   * @param config the play verb config (may be {@code null})
   * @return this for chaining
   */
  public SWMLBuilder play(Map<String, Object> config) {
    service.getDocument().addVerb("play", config != null ? config : new LinkedHashMap<>());
    return this;
  }

  /**
   * Add a 'play' verb to the main section.
   *
   * @param url single URL to play (mutually exclusive with urls), or {@code null}
   * @param urls list of URLs to play (mutually exclusive with url), or {@code null}
   * @param volume volume level (-40 to 40), or {@code null}
   * @param sayVoice voice for text-to-speech, or {@code null}
   * @param sayLanguage language for text-to-speech, or {@code null}
   * @param sayGender gender for text-to-speech, or {@code null}
   * @param autoAnswer whether to auto-answer the call, or {@code null}
   * @return this for chaining
   * @throws IllegalArgumentException if neither url nor urls is provided
   */
  public SWMLBuilder play(
      String url,
      List<String> urls,
      Double volume,
      String sayVoice,
      String sayLanguage,
      String sayGender,
      Boolean autoAnswer) {
    Map<String, Object> config = new LinkedHashMap<>();
    if (url != null) {
      config.put("url", url);
    } else if (urls != null) {
      config.put("urls", urls);
    } else {
      throw new IllegalArgumentException("Either url or urls must be provided");
    }
    if (volume != null) {
      config.put("volume", volume);
    }
    if (sayVoice != null) {
      config.put("say_voice", sayVoice);
    }
    if (sayLanguage != null) {
      config.put("say_language", sayLanguage);
    }
    if (sayGender != null) {
      config.put("say_gender", sayGender);
    }
    if (autoAnswer != null) {
      config.put("auto_answer", autoAnswer);
    }
    service.getDocument().addVerb("play", config);
    return this;
  }

  /**
   * Add a 'play' verb with a {@code say:} prefix for text-to-speech.
   *
   * @param text text to speak
   * @return this for chaining
   */
  public SWMLBuilder say(String text) {
    return say(text, null, null, null, null);
  }

  /**
   * Add a 'play' verb with a {@code say:} prefix for text-to-speech.
   *
   * @param text text to speak
   * @param voice voice for text-to-speech, or {@code null}
   * @param language language for text-to-speech, or {@code null}
   * @param gender gender for text-to-speech, or {@code null}
   * @param volume volume level (-40 to 40), or {@code null}
   * @return this for chaining
   */
  public SWMLBuilder say(String text, String voice, String language, String gender, Double volume) {
    return play("say:" + text, null, volume, voice, language, gender, null);
  }

  /**
   * Add a new section to the document.
   *
   * @param sectionName the section name
   * @return this for chaining
   */
  public SWMLBuilder addSection(String sectionName) {
    service.getDocument().addSection(sectionName);
    return this;
  }

  /**
   * Build and return the SWML document as a Map.
   *
   * @return the complete SWML document as a Map ({@code {version, sections}})
   */
  public Map<String, Object> build() {
    return service.getDocument().toMap();
  }

  /**
   * Build and render the SWML document as a JSON string.
   *
   * @return the complete SWML document as a compact JSON string
   */
  public String render() {
    return service.getDocument().render();
  }

  /**
   * Reset the document to an empty state.
   *
   * @return this for chaining
   */
  public SWMLBuilder reset() {
    service.getDocument().reset();
    return this;
  }

  // ------------------------------------------------------------------
  // Catch-all verb dispatch (Java analog of Python __getattr__).
  // ------------------------------------------------------------------

  /**
   * Add any schema verb to the document by name (e.g. {@code builder.verb("denoise", null)}, {@code
   * builder.verb("record_call", Map.of("stereo", true))}). Only valid schema verbs are accepted; an
   * unknown verb name is rejected with an exception.
   *
   * <p>For the {@code sleep} verb — which emits a bare integer, not a config object — use {@link
   * #sleepVerb(int)}.
   *
   * @param name the SWML verb name
   * @param config the verb config (may be {@code null}); {@code null} values are dropped
   * @return this for chaining
   * @throws IllegalArgumentException if {@code name} is not a valid schema verb
   */
  public SWMLBuilder verb(String name, Map<String, Object> config) {
    if (!Schema.getInstance().isValidVerb(name)) {
      throw new IllegalArgumentException("Unknown SWML verb: " + name);
    }
    if ("sleep".equals(name)) {
      throw new IllegalArgumentException("Use sleepVerb(int) for the 'sleep' verb");
    }
    Map<String, Object> clean = new LinkedHashMap<>();
    if (config != null) {
      for (Map.Entry<String, Object> e : config.entrySet()) {
        if (e.getValue() != null) {
          clean.put(e.getKey(), e.getValue());
        }
      }
    }
    service.getDocument().addVerb(name, clean);
    return this;
  }

  /**
   * Add the {@code sleep} verb, which emits a bare integer (milliseconds) rather than a config
   * object.
   *
   * @param milliseconds the amount of time to sleep, in milliseconds
   * @return this for chaining
   */
  public SWMLBuilder sleepVerb(int milliseconds) {
    service.getDocument().addVerb("sleep", milliseconds);
    return this;
  }
}
