package com.signalwire.sdk.swml;

/**
 * Audio direction for the SWML {@code tap} verb, as a typed, compile-time-checked closed set.
 *
 * <p>The tap {@code direction} is one of {@code "speak"}, {@code "hear"}, or {@code "both"};
 * anything else is rejected. The methods that take a tap direction ({@link
 * com.signalwire.sdk.swaig.FunctionResult#tap(String, String, TapDirection, String)}) accept this
 * enum <em>or</em> a plain {@link String}. The enum gives editor autocompletion and makes a typo
 * fail at compile time (a bare string like {@code "haer"} only fails at runtime, on the server). A
 * plain {@link String} is also accepted:
 *
 * <pre>{@code
 * result.tap("wss://x", "t1", TapDirection.HEAR, "PCMU");   // typed, autocompleted
 * result.tap("wss://x", "t1", "hear", "PCMU");              // string still works
 * }</pre>
 *
 * <p><strong>This is a DIFFERENT set from {@link RecordDirection}.</strong> {@code tap} uses {@code
 * hear} where {@code record_call} uses {@code listen}, mirroring the reference's two separate
 * validation lists — the two sets are modelled as two distinct enums and are never shared (see the
 * 3-vocabulary trap: SWML {@code record_call} {@code {speak,listen,both}} vs SWML {@code tap}
 * {@code {speak,hear,both}} vs RELAY {@code {listen,speak,both}}).
 *
 * <p>Each constant's {@link #getValue() value} is the canonical wire string, so routing a tap
 * through the enum is byte-for-byte identical to passing that string.
 */
public enum TapDirection implements WireEnum {
  SPEAK("speak"),
  HEAR("hear"),
  BOTH("both");

  private final String value;

  TapDirection(String value) {
    this.value = value;
  }

  /**
   * The canonical wire string for this direction ({@code "speak"} / {@code "hear"} / {@code
   * "both"}). Equivalent to PHP's backed-enum {@code ->value}.
   *
   * @return the lower-case direction name as it appears on the wire.
   */
  @Override
  public String getValue() {
    return value;
  }

  /**
   * Parse a wire string into a {@link TapDirection}, or return {@code null} if it is not a
   * recognised direction (the same strings the Python reference would reject with {@code
   * ValueError}). Note {@code "listen"} is valid for {@link RecordDirection} but NOT for {@code
   * tap}, so it returns {@code null} here. The Java analog of Rust's {@code from_str}.
   *
   * @param wire the candidate wire string (case-sensitive).
   * @return the matching constant, or {@code null} if none matches.
   */
  public static TapDirection fromWire(String wire) {
    for (TapDirection d : values()) {
      if (d.value.equals(wire)) {
        return d;
      }
    }
    return null;
  }
}
