package com.signalwire.sdk.swml;

/**
 * Audio direction for the SWML {@code record_call} verb, as a typed, compile-time-checked closed
 * set.
 *
 * <p>The Python reference {@code FunctionResult.record_call} validates {@code direction} against
 * {@code ["speak", "listen", "both"]} and raises {@code ValueError} on anything else. The methods
 * that take a record direction ({@link com.signalwire.sdk.swaig.FunctionResult#recordCall(String,
 * boolean, String, RecordDirection)}) accept this enum <em>or</em> a plain {@link String}. The enum
 * gives editor autocompletion and makes a typo fail at compile time (a bare string like {@code
 * "listenn"} only fails at runtime, on the server). Strings keep parity with the Python reference
 * (which uses a bare {@code str}):
 *
 * <pre>{@code
 * result.recordCall("rec1", true, "mp3", RecordDirection.LISTEN);   // typed, autocompleted
 * result.recordCall("rec1", true, "mp3", "listen");                 // string still works (parity)
 * }</pre>
 *
 * <p><strong>This is a DIFFERENT set from {@link TapDirection}.</strong> {@code record_call} uses
 * {@code listen} where {@code tap} uses {@code hear}, mirroring the reference's two separate
 * validation lists — the two sets are modelled as two distinct enums and are never shared (see the
 * 3-vocabulary trap: SWML {@code record_call} {@code {speak,listen,both}} vs SWML {@code tap}
 * {@code {speak,hear,both}} vs RELAY {@code {listen,speak,both}}).
 *
 * <p>Each constant's {@link #getValue() value} is the canonical wire string, so routing a recording
 * through the enum is byte-for-byte identical to passing that string.
 */
public enum RecordDirection implements WireEnum {
  SPEAK("speak"),
  LISTEN("listen"),
  BOTH("both");

  private final String value;

  RecordDirection(String value) {
    this.value = value;
  }

  /**
   * The canonical wire string for this direction ({@code "speak"} / {@code "listen"} / {@code
   * "both"}). Equivalent to PHP's backed-enum {@code ->value}.
   *
   * @return the lower-case direction name as it appears on the wire.
   */
  @Override
  public String getValue() {
    return value;
  }

  /**
   * Parse a wire string into a {@link RecordDirection}, or return {@code null} if it is not a
   * recognised direction (the same strings the Python reference would reject with {@code
   * ValueError}). Note {@code "hear"} is valid for {@link TapDirection} but NOT for {@code
   * record_call}, so it returns {@code null} here. The Java analog of Rust's {@code from_str}.
   *
   * @param wire the candidate wire string (case-sensitive).
   * @return the matching constant, or {@code null} if none matches.
   */
  public static RecordDirection fromWire(String wire) {
    for (RecordDirection d : values()) {
      if (d.value.equals(wire)) {
        return d;
      }
    }
    return null;
  }
}
