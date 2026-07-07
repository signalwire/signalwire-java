package com.signalwire.sdk.swml;

/**
 * Audio container format for call recording, as a typed, compile-time-checked closed set.
 *
 * <p>The SWML {@code record_call} verb (and the SignalWire calling REST API) accept a fixed set of
 * recording formats — {@code mp3}, {@code wav}, and {@code mp4}. The methods that take a format
 * ({@link com.signalwire.sdk.swaig.FunctionResult#recordCall(String, boolean, RecordFormat,
 * String)} and the {@code recordFormat(RecordFormat)} builder setter) accept this enum <em>or</em>
 * a plain {@link String}. The enum gives editor autocompletion and makes a typo fail at compile
 * time (a bare string like {@code "mp33"} only fails at runtime, on the server). A plain {@link
 * String} is also accepted:
 *
 * <pre>{@code
 * result.recordCall(null, true, RecordFormat.MP3, "both");   // typed, autocompleted
 * result.recordCall(null, true, "mp3", "both");              // string still works
 * AgentBase.builder().recordFormat(RecordFormat.WAV);        // typed builder
 * AgentBase.builder().recordFormat("wav");                   // string still works
 * }</pre>
 *
 * <p>Each constant's {@link #getValue() value} is the canonical wire string, so routing a recording
 * through the enum is byte-for-byte identical to passing that string.
 */
public enum RecordFormat implements WireEnum {
  MP3("mp3"),
  WAV("wav"),
  MP4("mp4");

  private final String value;

  RecordFormat(String value) {
    this.value = value;
  }

  /**
   * The canonical wire string for this format ({@code "mp3"} / {@code "wav"} / {@code "mp4"}).
   * Equivalent to PHP's backed-enum {@code ->value}.
   *
   * @return the lower-case format name as it appears on the wire.
   */
  @Override
  public String getValue() {
    return value;
  }
}
