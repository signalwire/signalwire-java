package com.signalwire.sdk.swml;

/**
 * Marker for the typed, compile-time-checked closed-set enums whose constants
 * carry a canonical <em>wire string</em> ({@link RecordFormat}, {@link RecordDirection},
 * {@link TapDirection}, {@link Codec}).
 *
 * <p>Every such enum already exposes {@link #getValue()} returning the exact
 * string the SignalWire platform expects on the wire (e.g. {@code "mp3"},
 * {@code "listen"}, {@code "PCMU"}). This interface simply names that shared
 * shape so a single API can accept any of them generically — most importantly
 * {@link com.signalwire.sdk.swaig.ParameterSchema.Builder#enumOf(String, WireEnum[], String)},
 * which turns {@code RecordFormat.values()} into a JSON-schema {@code enum:[...]}
 * of wire strings without per-enum overloads.
 *
 * <p>Because Java arrays are covariant, {@code RecordFormat.values()} (typed
 * {@code RecordFormat[]}) is directly usable wherever a {@code WireEnum[]} is
 * expected. The interface adds <strong>no</strong> new method to the enums —
 * {@code getValue()} already existed — so it is purely a unifying type, and
 * routing a value through it is byte-for-byte identical to passing its
 * {@code getValue()} string.
 *
 * <p>This is the SWAIG/SWML "has a canonical wire value" set only. It is
 * deliberately NOT implemented by RELAY device codecs or other open-ended
 * string fields — see the per-enum Javadoc for the 3-vocabulary trap.
 */
public interface WireEnum {

    /**
     * The canonical wire string for this constant — the exact value the
     * SignalWire platform expects (e.g. {@code "mp3"} / {@code "listen"} /
     * {@code "PCMU"}). Equivalent to PHP's backed-enum {@code ->value}.
     *
     * @return the wire string as it appears on the wire.
     */
    String getValue();
}
