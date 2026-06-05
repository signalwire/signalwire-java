package com.signalwire.sdk.swml;

/**
 * Media codec for the SWML {@code tap} verb, as a typed, compile-time-checked
 * closed set.
 *
 * <p>The Python reference {@code FunctionResult.tap} validates {@code codec}
 * against {@code ["PCMU", "PCMA"]} and raises {@code ValueError} on anything
 * else. The methods that take a tap codec
 * ({@link com.signalwire.sdk.swaig.FunctionResult#tap(String, String, String, Codec)})
 * accept this enum <em>or</em> a plain {@link String}. The enum gives editor
 * autocompletion and makes a typo fail at compile time (a bare string like
 * {@code "PCMX"} only fails at runtime, on the server). Strings keep parity
 * with the Python reference (which uses a bare {@code str}):
 *
 * <pre>{@code
 * result.tap("wss://x", "t1", "both", Codec.PCMA);   // typed, autocompleted
 * result.tap("wss://x", "t1", "both", "PCMA");       // string still works (parity)
 * }</pre>
 *
 * <p><strong>This is the SWAIG {@code tap} codec set only — do NOT reuse it for
 * RELAY {@code stream}/{@code connect}.</strong> RELAY device codecs are a much
 * larger superset (e.g. {@code PCMU,PCMA,OPUS,G729,G722,VP8,H264}, comma-joined);
 * the 2-value SWAIG tap codec set is deliberately distinct and never shared.
 *
 * <p>The wire strings are upper-case ({@code "PCMU"} / {@code "PCMA"}), matching
 * the reference's literal list exactly. Each constant's {@link #getValue() value}
 * is the canonical wire string, so routing a tap through the enum is
 * byte-for-byte identical to passing that string.
 */
public enum Codec {

    PCMU("PCMU"),
    PCMA("PCMA");

    private final String value;

    Codec(String value) {
        this.value = value;
    }

    /**
     * The canonical (upper-case) wire string for this codec ({@code "PCMU"} /
     * {@code "PCMA"}). Equivalent to PHP's backed-enum {@code ->value}.
     *
     * @return the upper-case codec name as it appears on the wire.
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a wire string into a {@link Codec}, or return {@code null} if it is
     * not a recognised codec. Matching is exact (case-sensitive), mirroring the
     * reference's literal {@code in ["PCMU", "PCMA"]} check (so {@code "pcmu"}
     * returns {@code null}). The Java analog of Rust's {@code from_str}.
     *
     * @param wire the candidate wire string (case-sensitive).
     * @return the matching constant, or {@code null} if none matches.
     */
    public static Codec fromWire(String wire) {
        for (Codec c : values()) {
            if (c.value.equals(wire)) {
                return c;
            }
        }
        return null;
    }
}
