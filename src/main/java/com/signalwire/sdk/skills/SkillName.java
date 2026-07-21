package com.signalwire.sdk.skills;

/**
 * Built-in skill names as a typed, compile-time-checked closed set.
 *
 * <p>{@link com.signalwire.sdk.agent.AgentBase#addSkill(SkillName, java.util.Map)} (and the
 * matching {@code removeSkill} / {@code hasSkill} overloads) accept this enum <em>or</em> a plain
 * {@link String}. The enum gives editor autocompletion and makes a typo fail at compile time (a
 * bare string like {@code "datetiem"} only fails at runtime, on the server). A plain {@link String}
 * is also accepted, which still allows custom / third-party skills that aren't built in:
 *
 * <pre>{@code
 * agent.addSkill(SkillName.DATETIME);          // typed, autocompleted
 * agent.addSkill("datetime");                  // string still works
 * agent.addSkill("my_custom_skill");           // open set: custom skills ok
 * }</pre>
 *
 * <p>Each constant's {@link #getValue() value} is the canonical wire string (the key the skill
 * registers under in {@link SkillRegistry}), so routing a call through the enum is byte-for-byte
 * identical to passing that string.
 */
public enum SkillName {
  API_NINJAS_TRIVIA("api_ninjas_trivia"),
  CLAUDE_SKILLS("claude_skills"),
  CUSTOM_SKILLS("custom_skills"),
  DATASPHERE("datasphere"),
  DATASPHERE_SERVERLESS("datasphere_serverless"),
  DATETIME("datetime"),
  GOOGLE_MAPS("google_maps"),
  INFO_GATHERER("info_gatherer"),
  JOKE("joke"),
  MATH("math"),
  MCP_GATEWAY("mcp_gateway"),
  NATIVE_VECTOR_SEARCH("native_vector_search"),
  PLAY_BACKGROUND_FILE("play_background_file"),
  SPIDER("spider"),
  SWML_TRANSFER("swml_transfer"),
  WEATHER_API("weather_api"),
  WEB_SEARCH("web_search"),
  WIKIPEDIA_SEARCH("wikipedia_search");

  private final String value;

  SkillName(String value) {
    this.value = value;
  }

  /**
   * The canonical wire string for this skill — the name it registers under in {@link
   * SkillRegistry}. Equivalent to PHP's backed-enum {@code ->value}.
   *
   * @return the lower-case-with-underscores skill name.
   */
  public String getValue() {
    return value;
  }
}
