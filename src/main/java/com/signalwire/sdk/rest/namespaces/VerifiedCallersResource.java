/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;
import java.util.Map;

/**
 * Verified Caller IDs namespace — CRUD plus the verification flow.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.verified_callers.VerifiedCallersResource}: standard
 * CRUD against {@code /api/relay/rest/verified_caller_ids} (update uses PUT — the {@link
 * CrudResource} public constructor already defaults to PUT), plus two verification operations on
 * the {@code {id}/verification} sub-path:
 *
 * <ul>
 *   <li>{@link #redialVerification(String)} → {@code POST .../{id}/verification}
 *   <li>{@link #submitVerification(String, Map)} → {@code PUT .../{id}/verification}
 * </ul>
 *
 * <p>Exposed as {@code client.verifiedCallers()} (Python: {@code client.verified_callers}).
 */
public class VerifiedCallersResource extends CrudResource {

  public VerifiedCallersResource(HttpClient httpClient) {
    super(httpClient, "/relay/rest/verified_caller_ids");
  }

  /** Re-trigger the verification call for a verified caller ID ({@code POST .../verification}). */
  public Map<String, Object> redialVerification(String callerId) {
    return getHttpClient().post(getBasePath() + "/" + callerId + "/verification", null);
  }

  /** Submit a verification code for a verified caller ID ({@code PUT .../verification}). */
  public Map<String, Object> submitVerification(String callerId, Map<String, Object> body) {
    return getHttpClient().put(getBasePath() + "/" + callerId + "/verification", body);
  }
}
