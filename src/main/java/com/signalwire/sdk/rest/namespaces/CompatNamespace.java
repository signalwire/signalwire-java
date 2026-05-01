/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * REST namespace for compatibility (CXML/Twilio-compatible) resources.
 *
 * <p>The Twilio-compatible LAML API is mounted under
 * {@code /api/laml/2010-04-01/Accounts/{AccountSid}}. The trailing
 * {@code /api} prefix is added by {@link HttpClient}, so namespace base
 * paths here begin with {@code /laml/2010-04-01/Accounts/...}.
 */
public class CompatNamespace {

    private final HttpClient httpClient;
    private final String accountSid;
    private final String accountBase;

    private final CompatCalls calls;
    private final CompatMessages messages;
    private final CompatFaxes faxes;
    private final CompatPhoneNumbers phoneNumbers;
    private final CompatRecordings recordings;
    private final CompatTranscriptions transcriptions;
    private final CrudResource accounts;
    private final CrudResource queues;
    private final CrudResource conferences;
    private final CrudResource applications;
    private final CrudResource sipDomains;
    private final CrudResource sipCredentialLists;
    private final CrudResource sipIpAccessControlLists;

    public CompatNamespace(HttpClient httpClient, String accountSid) {
        this.httpClient = httpClient;
        this.accountSid = accountSid;
        this.accountBase = "/laml/2010-04-01/Accounts/" + accountSid;

        this.calls = new CompatCalls(httpClient, accountBase + "/Calls");
        this.messages = new CompatMessages(httpClient, accountBase + "/Messages");
        this.faxes = new CompatFaxes(httpClient, accountBase + "/Faxes");
        this.phoneNumbers = new CompatPhoneNumbers(httpClient, accountBase + "/IncomingPhoneNumbers");
        this.recordings = new CompatRecordings(httpClient, accountBase + "/Recordings");
        this.transcriptions = new CompatTranscriptions(httpClient, accountBase + "/Transcriptions");
        this.accounts = new CrudResource(httpClient, accountBase);
        this.queues = new CrudResource(httpClient, accountBase + "/Queues");
        this.conferences = new CrudResource(httpClient, accountBase + "/Conferences");
        this.applications = new CrudResource(httpClient, accountBase + "/Applications");
        this.sipDomains = new CrudResource(httpClient, accountBase + "/SIP/Domains");
        this.sipCredentialLists = new CrudResource(httpClient, accountBase + "/SIP/CredentialLists");
        this.sipIpAccessControlLists = new CrudResource(httpClient, accountBase + "/SIP/IpAccessControlLists");
    }

    public CompatCalls calls() { return calls; }
    public CompatMessages messages() { return messages; }
    public CompatFaxes faxes() { return faxes; }
    public CompatPhoneNumbers phoneNumbers() { return phoneNumbers; }
    public CompatRecordings recordings() { return recordings; }
    public CompatTranscriptions transcriptions() { return transcriptions; }
    public CrudResource accounts() { return accounts; }
    public CrudResource queues() { return queues; }
    public CrudResource conferences() { return conferences; }
    public CrudResource applications() { return applications; }
    public CrudResource sipDomains() { return sipDomains; }
    public CrudResource sipCredentialLists() { return sipCredentialLists; }
    public CrudResource sipIpAccessControlLists() { return sipIpAccessControlLists; }

    // ────────────────────────────────────────────────────────────────────
    // Compat sub-resource classes
    // ────────────────────────────────────────────────────────────────────

    /**
     * Compat call management with recording and stream sub-resources.
     */
    public static class CompatCalls extends CrudResource {

        public CompatCalls(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        public Map<String, Object> update(String sid, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + sid, body);
        }

        public Map<String, Object> startRecording(String callSid, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + callSid + "/Recordings", body);
        }

        public Map<String, Object> updateRecording(
                String callSid, String recordingSid, Map<String, Object> body) {
            return getHttpClient().post(
                    getBasePath() + "/" + callSid + "/Recordings/" + recordingSid, body);
        }

        public Map<String, Object> startStream(String callSid, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + callSid + "/Streams", body);
        }

        public Map<String, Object> stopStream(
                String callSid, String streamSid, Map<String, Object> body) {
            return getHttpClient().post(
                    getBasePath() + "/" + callSid + "/Streams/" + streamSid, body);
        }
    }

    /**
     * Compat message management with media sub-resources.
     */
    public static class CompatMessages extends CrudResource {

        public CompatMessages(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        public Map<String, Object> update(String sid, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + sid, body);
        }

        public Map<String, Object> listMedia(String messageSid) {
            return getHttpClient().get(getBasePath() + "/" + messageSid + "/Media");
        }

        public Map<String, Object> listMedia(String messageSid, Map<String, String> queryParams) {
            return getHttpClient().get(getBasePath() + "/" + messageSid + "/Media", queryParams);
        }

        public Map<String, Object> getMedia(String messageSid, String mediaSid) {
            return getHttpClient().get(getBasePath() + "/" + messageSid + "/Media/" + mediaSid);
        }

        public Map<String, Object> deleteMedia(String messageSid, String mediaSid) {
            return getHttpClient().delete(getBasePath() + "/" + messageSid + "/Media/" + mediaSid);
        }
    }

    /**
     * Compat fax management with media sub-resources.
     */
    public static class CompatFaxes extends CrudResource {

        public CompatFaxes(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        public Map<String, Object> update(String sid, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + sid, body);
        }

        public Map<String, Object> listMedia(String faxSid) {
            return getHttpClient().get(getBasePath() + "/" + faxSid + "/Media");
        }

        public Map<String, Object> listMedia(String faxSid, Map<String, String> queryParams) {
            return getHttpClient().get(getBasePath() + "/" + faxSid + "/Media", queryParams);
        }

        public Map<String, Object> getMedia(String faxSid, String mediaSid) {
            return getHttpClient().get(getBasePath() + "/" + faxSid + "/Media/" + mediaSid);
        }

        public Map<String, Object> deleteMedia(String faxSid, String mediaSid) {
            return getHttpClient().delete(getBasePath() + "/" + faxSid + "/Media/" + mediaSid);
        }
    }

    /**
     * Compat IncomingPhoneNumbers + AvailablePhoneNumbers management.
     */
    public static class CompatPhoneNumbers {

        private final HttpClient httpClient;
        private final String basePath;
        private final String availableBase;
        private final String importedBase;

        public CompatPhoneNumbers(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
            this.availableBase = basePath.replace(
                    "/IncomingPhoneNumbers", "/AvailablePhoneNumbers");
            this.importedBase = basePath.replace(
                    "/IncomingPhoneNumbers", "/ImportedPhoneNumbers");
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String sid) {
            return httpClient.get(basePath + "/" + sid);
        }

        public Map<String, Object> update(String sid, Map<String, Object> body) {
            return httpClient.post(basePath + "/" + sid, body);
        }

        public Map<String, Object> delete(String sid) {
            return httpClient.delete(basePath + "/" + sid);
        }

        public Map<String, Object> purchase(Map<String, Object> body) {
            return httpClient.post(basePath, body);
        }

        public Map<String, Object> importNumber(Map<String, Object> body) {
            return httpClient.post(importedBase, body);
        }

        public Map<String, Object> listAvailableCountries() {
            return httpClient.get(availableBase);
        }

        public Map<String, Object> listAvailableCountries(Map<String, String> queryParams) {
            return httpClient.get(availableBase, queryParams);
        }

        public Map<String, Object> searchLocal(String country) {
            return httpClient.get(availableBase + "/" + country + "/Local");
        }

        public Map<String, Object> searchLocal(String country, Map<String, String> queryParams) {
            return httpClient.get(availableBase + "/" + country + "/Local", queryParams);
        }

        public Map<String, Object> searchTollFree(String country) {
            return httpClient.get(availableBase + "/" + country + "/TollFree");
        }

        public Map<String, Object> searchTollFree(String country, Map<String, String> queryParams) {
            return httpClient.get(availableBase + "/" + country + "/TollFree", queryParams);
        }
    }

    /**
     * Compat recording management.
     */
    public static class CompatRecordings {

        private final HttpClient httpClient;
        private final String basePath;

        public CompatRecordings(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String sid) {
            return httpClient.get(basePath + "/" + sid);
        }

        public Map<String, Object> delete(String sid) {
            return httpClient.delete(basePath + "/" + sid);
        }
    }

    /**
     * Compat transcription management.
     */
    public static class CompatTranscriptions {

        private final HttpClient httpClient;
        private final String basePath;

        public CompatTranscriptions(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String sid) {
            return httpClient.get(basePath + "/" + sid);
        }

        public Map<String, Object> delete(String sid) {
            return httpClient.delete(basePath + "/" + sid);
        }
    }
}
