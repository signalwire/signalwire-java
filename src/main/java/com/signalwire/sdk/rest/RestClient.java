/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.signalwire.sdk.rest.namespaces.*;

import java.util.Objects;

/**
 * SignalWire REST API client with all 21 namespaces.
 * <p>
 * Uses {@code java.net.http.HttpClient} with Basic Auth. Each namespace
 * provides typed access to a group of API resources.
 *
 * <pre>{@code
 * var client = RestClient.builder()
 *     .project("project-id")
 *     .token("api-token")
 *     .space("example.signalwire.com")
 *     .build();
 *
 * var numbers = client.phoneNumbers().list();
 * var docs = client.datasphere().documents().list();
 * }</pre>
 */
public class RestClient {

    private final String project;
    private final String space;
    private final HttpClient httpClient;

    // Lazy-initialized namespaces
    private volatile FabricNamespace fabricNs;
    private volatile CallingNamespace callingNs;
    private volatile PhoneNumbersNamespace phoneNumbersNs;
    private volatile DatasphereNamespace datasphereNs;
    private volatile VideoNamespace videoNs;
    private volatile CompatNamespace compatNs;
    private volatile MessagingNamespace messagingNs;
    private volatile SipNamespace sipNs;
    private volatile FaxNamespace faxNs;
    private volatile ChatNamespace chatNs;
    private volatile PubSubNamespace pubSubNs;
    private volatile SwmlNamespace swmlNs;
    private volatile CampaignNamespace campaignNs;
    private volatile ComplianceNamespace complianceNs;
    private volatile BillingNamespace billingNs;
    private volatile ProjectNamespace projectNs;
    private volatile StreamNamespace streamNs;
    private volatile NumberLookupNamespace numberLookupNs;
    private volatile ConferenceNamespace conferenceNs;
    private volatile QueueNamespace queueNs;
    private volatile RecordingNamespace recordingNs;
    private volatile TranscriptionNamespace transcriptionNs;
    // Python parity namespaces (closing the small-namespaces gap).
    private volatile AddressesNamespace addressesNs;
    private volatile ShortCodesNamespace shortCodesNs;
    private volatile ImportedNumbersNamespace importedNumbersNs;
    private volatile MfaNamespace mfaNs;
    private volatile SipProfileNamespace sipProfileNs;
    private volatile NumberGroupsNamespace numberGroupsNs;
    private volatile RegistryNamespace registryNs;
    private volatile LogsNamespace logsNs;

    private RestClient(Builder builder) {
        this.project = builder.project;
        this.space = builder.space;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : new HttpClient(builder.space, builder.project, builder.token);
    }

    /**
     * Build a {@link RestClient} pointed at an explicit base URL — typically
     * a loopback fixture used by the porting-sdk's REST-transport audit. The
     * returned client signs requests with the given {@code project}/{@code token}
     * pair via Basic Auth and routes every namespace's HTTP through the
     * fixture instead of the live SignalWire space.
     *
     * @param baseUrl fully qualified base URL (e.g. {@code "http://127.0.0.1:NNNN/api"});
     *                {@code "/api"} is appended if not already present
     * @param project project ID used as the Basic Auth username
     * @param token   API token used as the Basic Auth password
     */
    public static RestClient withBaseUrl(String baseUrl, String project, String token) {
        Objects.requireNonNull(baseUrl, "baseUrl is required");
        Objects.requireNonNull(project, "project is required");
        Objects.requireNonNull(token, "token is required");
        String normalized = baseUrl.endsWith("/api") || baseUrl.endsWith("/api/")
                ? baseUrl.replaceAll("/$", "")
                : baseUrl.replaceAll("/$", "") + "/api";
        return builder()
                .project(project)
                .token(token)
                .space(baseUrl)
                .httpClient(HttpClient.withBaseUrl(normalized, project, token))
                .build();
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String project;
        private String token;
        private String space;
        private HttpClient httpClient;

        public Builder project(String project) { this.project = project; return this; }
        public Builder token(String token) { this.token = token; return this; }
        public Builder space(String space) { this.space = space; return this; }

        /**
         * Use a pre-built {@link HttpClient}. Useful when pointing the client
         * at an explicit base URL (e.g. via {@link HttpClient#withBaseUrl}).
         */
        Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }

        public RestClient build() {
            Objects.requireNonNull(project, "project is required");
            Objects.requireNonNull(token, "token is required");
            Objects.requireNonNull(space, "space is required");
            return new RestClient(this);
        }
    }

    // ── Accessors ────────────────────────────────────────────────────

    public String getProject() { return project; }
    public String getSpace() { return space; }
    public HttpClient getHttpClient() { return httpClient; }

    // ── Namespaces (lazy init, thread-safe via double-checked locking) ──

    public FabricNamespace fabric() {
        if (fabricNs == null) { synchronized (this) { if (fabricNs == null) fabricNs = new FabricNamespace(httpClient); } }
        return fabricNs;
    }

    public CallingNamespace calling() {
        if (callingNs == null) { synchronized (this) { if (callingNs == null) callingNs = new CallingNamespace(httpClient); } }
        return callingNs;
    }

    public PhoneNumbersNamespace phoneNumbers() {
        if (phoneNumbersNs == null) { synchronized (this) { if (phoneNumbersNs == null) phoneNumbersNs = new PhoneNumbersNamespace(httpClient); } }
        return phoneNumbersNs;
    }

    public DatasphereNamespace datasphere() {
        if (datasphereNs == null) { synchronized (this) { if (datasphereNs == null) datasphereNs = new DatasphereNamespace(httpClient); } }
        return datasphereNs;
    }

    public VideoNamespace video() {
        if (videoNs == null) { synchronized (this) { if (videoNs == null) videoNs = new VideoNamespace(httpClient); } }
        return videoNs;
    }

    public CompatNamespace compat() {
        if (compatNs == null) { synchronized (this) { if (compatNs == null) compatNs = new CompatNamespace(httpClient, project); } }
        return compatNs;
    }

    public MessagingNamespace messaging() {
        if (messagingNs == null) { synchronized (this) { if (messagingNs == null) messagingNs = new MessagingNamespace(httpClient); } }
        return messagingNs;
    }

    public SipNamespace sip() {
        if (sipNs == null) { synchronized (this) { if (sipNs == null) sipNs = new SipNamespace(httpClient); } }
        return sipNs;
    }

    public FaxNamespace fax() {
        if (faxNs == null) { synchronized (this) { if (faxNs == null) faxNs = new FaxNamespace(httpClient); } }
        return faxNs;
    }

    public ChatNamespace chat() {
        if (chatNs == null) { synchronized (this) { if (chatNs == null) chatNs = new ChatNamespace(httpClient); } }
        return chatNs;
    }

    public PubSubNamespace pubSub() {
        if (pubSubNs == null) { synchronized (this) { if (pubSubNs == null) pubSubNs = new PubSubNamespace(httpClient); } }
        return pubSubNs;
    }

    public SwmlNamespace swml() {
        if (swmlNs == null) { synchronized (this) { if (swmlNs == null) swmlNs = new SwmlNamespace(httpClient); } }
        return swmlNs;
    }

    public CampaignNamespace campaign() {
        if (campaignNs == null) { synchronized (this) { if (campaignNs == null) campaignNs = new CampaignNamespace(httpClient); } }
        return campaignNs;
    }

    public ComplianceNamespace compliance() {
        if (complianceNs == null) { synchronized (this) { if (complianceNs == null) complianceNs = new ComplianceNamespace(httpClient); } }
        return complianceNs;
    }

    public BillingNamespace billing() {
        if (billingNs == null) { synchronized (this) { if (billingNs == null) billingNs = new BillingNamespace(httpClient); } }
        return billingNs;
    }

    public ProjectNamespace project() {
        if (projectNs == null) { synchronized (this) { if (projectNs == null) projectNs = new ProjectNamespace(httpClient); } }
        return projectNs;
    }

    public StreamNamespace streams() {
        if (streamNs == null) { synchronized (this) { if (streamNs == null) streamNs = new StreamNamespace(httpClient); } }
        return streamNs;
    }

    public NumberLookupNamespace numberLookup() {
        if (numberLookupNs == null) { synchronized (this) { if (numberLookupNs == null) numberLookupNs = new NumberLookupNamespace(httpClient); } }
        return numberLookupNs;
    }

    public ConferenceNamespace conferences() {
        if (conferenceNs == null) { synchronized (this) { if (conferenceNs == null) conferenceNs = new ConferenceNamespace(httpClient); } }
        return conferenceNs;
    }

    public QueueNamespace queues() {
        if (queueNs == null) { synchronized (this) { if (queueNs == null) queueNs = new QueueNamespace(httpClient); } }
        return queueNs;
    }

    public RecordingNamespace recordings() {
        if (recordingNs == null) { synchronized (this) { if (recordingNs == null) recordingNs = new RecordingNamespace(httpClient); } }
        return recordingNs;
    }

    public TranscriptionNamespace transcriptions() {
        if (transcriptionNs == null) { synchronized (this) { if (transcriptionNs == null) transcriptionNs = new TranscriptionNamespace(httpClient); } }
        return transcriptionNs;
    }

    // ── Python-parity namespaces ─────────────────────────────────────────

    public AddressesNamespace addresses() {
        if (addressesNs == null) { synchronized (this) { if (addressesNs == null) addressesNs = new AddressesNamespace(httpClient); } }
        return addressesNs;
    }

    public ShortCodesNamespace shortCodes() {
        if (shortCodesNs == null) { synchronized (this) { if (shortCodesNs == null) shortCodesNs = new ShortCodesNamespace(httpClient); } }
        return shortCodesNs;
    }

    public ImportedNumbersNamespace importedNumbers() {
        if (importedNumbersNs == null) { synchronized (this) { if (importedNumbersNs == null) importedNumbersNs = new ImportedNumbersNamespace(httpClient); } }
        return importedNumbersNs;
    }

    public MfaNamespace mfa() {
        if (mfaNs == null) { synchronized (this) { if (mfaNs == null) mfaNs = new MfaNamespace(httpClient); } }
        return mfaNs;
    }

    public SipProfileNamespace sipProfile() {
        if (sipProfileNs == null) { synchronized (this) { if (sipProfileNs == null) sipProfileNs = new SipProfileNamespace(httpClient); } }
        return sipProfileNs;
    }

    public NumberGroupsNamespace numberGroups() {
        if (numberGroupsNs == null) { synchronized (this) { if (numberGroupsNs == null) numberGroupsNs = new NumberGroupsNamespace(httpClient); } }
        return numberGroupsNs;
    }

    public RegistryNamespace registry() {
        if (registryNs == null) { synchronized (this) { if (registryNs == null) registryNs = new RegistryNamespace(httpClient); } }
        return registryNs;
    }

    public LogsNamespace logs() {
        if (logsNs == null) { synchronized (this) { if (logsNs == null) logsNs = new LogsNamespace(httpClient); } }
        return logsNs;
    }
}
