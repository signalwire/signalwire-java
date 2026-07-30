/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package com.signalwire.sdk.tools;

import com.signalwire.sdk.security.SessionManager;

/**
 * TokenInteropMint — the TOKEN-INTEROP mint fixture.
 *
 * <p>The contract being proven is property 3 of the SWAIG tool-token contract: a token this SDK
 * MINTS must validate under an independent decoder. The other two properties (that a token is
 * minted at all; that the HMAC is keyed with the {@code secret_key} STRING's bytes) already had
 * coverage — this one did not, and an implementation can satisfy both and still emit a token no
 * other implementation accepts, in which case every secure tool call fails authentication in
 * production.
 *
 * <p>Protocol: read the FIXED mint inputs from the environment (the checker owns them, so this
 * fixture cannot drift from the values it is verified against), construct a {@link SessionManager}
 * with that secret key, mint ONE token, and print JUST the token on stdout. Anything else belongs
 * on stderr.
 *
 * <p>Run from the signalwire-java repo root:
 *
 * <pre>{@code ./gradlew --console=plain -q tokenInteropMint}</pre>
 */
// Package-private, like every other fixture in this package (SecureDefaultDump, WireDump, …):
// these are gradle-task entry points, not API. A `public` class here is enumerated as public
// SDK surface and fails SURFACE-FRESH/SURFACE-DIFF as a symbol the Python reference lacks.
final class TokenInteropMint {

  private TokenInteropMint() {}

  /** Read a required fixed mint input from the environment, or fail loud. */
  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
      System.err.println(
          name
              + " is not set — the TOKEN-INTEROP checker supplies the fixed mint inputs in the"
              + " environment; run this via diff_port_token_interop.py --mint-cmd.");
      System.exit(1);
    }
    return value;
  }

  /** Mint one token with the checker's fixed inputs and print it. */
  public static void main(String[] args) {
    String secretKey = required("SW_TOKEN_INTEROP_SECRET_KEY");
    String callId = required("SW_TOKEN_INTEROP_CALL_ID");
    String functionName = required("SW_TOKEN_INTEROP_FUNCTION_NAME");

    // Default expiry — the token must carry a FUTURE expiry, which the checker verifies. The
    // (int, String) constructor takes the reference's secret_key STRING, whose UTF-8 bytes key
    // the HMAC (NOT 32 raw bytes decoded from it).
    SessionManager manager = new SessionManager(900, secretKey);
    System.out.println(manager.generateToken(functionName, callId));
  }
}
