/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A typed view of the RELAY {@code {type, params}} device object that recurs as a raw {@code
 * Map<String, Object>} across {@link Call#connect}, {@link Call#refer}, {@link RelayClient#dial}
 * and {@link Call#tap}.
 *
 * <p>The wire shape is fixed (extracted from switchblade's {@code PublicCallConnectParams} / {@code
 * PublicCallDialParams} / {@code PublicCallReferParams} / {@code PublicCallTapParams}, mirrored in
 * {@code porting-sdk/relay-protocol/calling.*.params.json}): every device is an object with a
 * required string {@code type} discriminant and a {@code params} sub-object whose shape depends on
 * the type (a {@code phone} carries {@code to_number}/{@code from_number}, a {@code sip} carries
 * {@code to}/ {@code headers}, etc.). This class types the <em>shape</em> — the {@code {type,
 * params}} envelope — so callers stop hand-rolling the two-key map and get an explicit constructor
 * + accessors.
 *
 * <p><strong>{@code type} stays a {@link String}.</strong> The discriminant is NOT
 * schema-enumerated: the wire schema constrains it only to {@code "type": {"type": "string"}}, and
 * the platform accepts device types the SDK may not know about. Typing it as an enum would reject
 * valid values, so it is deliberately left open (the convention names {@code phone}/{@code sip}/
 * {@code webrtc} — see {@link Constants#DEVICE_TYPE_PHONE} etc. — but those are conveniences, not a
 * closed set). {@code params} is left an arbitrary {@code Map<String, Object>} for the same reason:
 * it is type-dependent and not a single fixed schema.
 *
 * <p><strong>Additive — the raw-map path stays.</strong> Every method that takes a device still
 * accepts a raw {@code Map<String, Object>}; this class is a convenience on top. {@link #toMap()}
 * produces a map that is <em>byte-for-byte identical</em> to the hand-written {@code {"type": …,
 * "params": …}} (insertion order {@code type} then {@code params}, matching every existing call
 * site), so routing a device through {@code Device} serialises exactly as the raw map does.
 *
 * <pre>{@code
 * // typed:
 * Device phone = Device.of("phone", Map.of(
 *     "to_number", "+15551112222", "from_number", "+15553334444"));
 * call.connect(List.of(List.of(phone.toMap())), null);
 *
 * // raw map still works (parity):
 * call.connect(List.of(List.of(Map.of(
 *     "type", "phone",
 *     "params", Map.of("to_number", "+15551112222",
 *                      "from_number", "+15553334444")))), null);
 * }</pre>
 *
 * <p>Python uses a bare {@code dict} for this object, so there is no reference equivalent — this is
 * a documented Java addition.
 */
public final class Device {

  private final String type;
  private final Map<String, Object> params;

  /**
   * Construct a device from its {@code type} discriminant and {@code params} sub-object.
   *
   * @param type the device type discriminant (e.g. {@code "phone"}, {@code "sip"}, {@code
   *     "webrtc"}); must not be {@code null}.
   * @param params the type-dependent params; {@code null} is treated as an empty map.
   */
  public Device(String type, Map<String, Object> params) {
    if (type == null) {
      throw new IllegalArgumentException("device type must not be null");
    }
    this.type = type;
    // Defensive copy preserving insertion order; null -> empty.
    this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
  }

  /**
   * Factory mirroring the constructor, for fluent call sites ({@code Device.of("phone", …)}).
   *
   * @param type the device type discriminant.
   * @param params the type-dependent params (may be {@code null}).
   * @return a new {@link Device}.
   */
  public static Device of(String type, Map<String, Object> params) {
    return new Device(type, params);
  }

  /**
   * Convenience factory for the most common case — a {@code phone} device. Builds {@code
   * {type:"phone", params:{to_number, from_number}}}.
   *
   * @param toNumber the destination E.164 number.
   * @param fromNumber the caller-ID E.164 number.
   * @return a new {@code phone} {@link Device}.
   */
  public static Device phone(String toNumber, String fromNumber) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("to_number", toNumber);
    p.put("from_number", fromNumber);
    return new Device(Constants.DEVICE_TYPE_PHONE, p);
  }

  /**
   * Convenience factory for a {@code sip} device. Builds {@code {type:"sip", params:{to, from?}}}
   * (only non-null values).
   *
   * @param to the SIP destination URI.
   * @param from the SIP from URI, or {@code null} to omit.
   * @return a new {@code sip} {@link Device}.
   */
  public static Device sip(String to, String from) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("to", to);
    if (from != null) {
      p.put("from", from);
    }
    return new Device(Constants.DEVICE_TYPE_SIP, p);
  }

  /** The device type discriminant ({@code "phone"}/{@code "sip"}/…). */
  public String getType() {
    return type;
  }

  /**
   * The type-dependent params sub-object (an unmodifiable view).
   *
   * @return the params map; never {@code null} (empty if none were supplied).
   */
  public Map<String, Object> getParams() {
    return java.util.Collections.unmodifiableMap(params);
  }

  /**
   * Serialise to the wire shape: a two-key map {@code {"type": …, "params": …}} with {@code type}
   * inserted first, identical to the hand-written map every existing call site builds. The returned
   * map is fresh and mutable (callers routinely nest it inside a {@code devices} list), and {@code
   * params} is a defensive copy so later mutation of the returned map cannot corrupt this {@code
   * Device}.
   *
   * @return a new {@code {type, params}} map ready to pass to {@code connect}/{@code refer}/{@code
   *     dial}/{@code tap}.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("params", new LinkedHashMap<>(params));
    return m;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Device)) {
      return false;
    }
    Device other = (Device) o;
    return type.equals(other.type) && params.equals(other.params);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(type, params);
  }

  @Override
  public String toString() {
    return "Device{type=" + type + ", params=" + params + "}";
  }
}
