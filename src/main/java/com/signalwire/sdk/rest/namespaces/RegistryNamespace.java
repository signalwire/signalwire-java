/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.HttpClient;

import java.util.Map;

/**
 * 10DLC Campaign Registry namespace — brands, campaigns, orders, numbers.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.registry.RegistryNamespace}.
 * All endpoints sit under {@code /api/relay/rest/registry/beta}.
 */
public class RegistryNamespace {

    private static final String BASE = "/relay/rest/registry/beta";

    private final RegistryBrands brands;
    private final RegistryCampaigns campaigns;
    private final RegistryOrders orders;
    private final RegistryNumbers numbers;

    public RegistryNamespace(HttpClient httpClient) {
        this.brands = new RegistryBrands(httpClient, BASE + "/brands");
        this.campaigns = new RegistryCampaigns(httpClient, BASE + "/campaigns");
        this.orders = new RegistryOrders(httpClient, BASE + "/orders");
        this.numbers = new RegistryNumbers(httpClient, BASE + "/numbers");
    }

    public RegistryBrands brands() { return brands; }
    public RegistryCampaigns campaigns() { return campaigns; }
    public RegistryOrders orders() { return orders; }
    public RegistryNumbers numbers() { return numbers; }

    // ────────────────────────────────────────────────────────────────────
    // Sub-resources
    // ────────────────────────────────────────────────────────────────────

    /**
     * 10DLC brand management — list / create / get plus brand-scoped
     * campaign sub-resources.
     */
    public static class RegistryBrands {

        private final HttpClient httpClient;
        private final String basePath;

        public RegistryBrands(HttpClient httpClient, String basePath) {
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

        public Map<String, Object> create(Map<String, Object> body) {
            return httpClient.post(basePath, body);
        }

        public Map<String, Object> get(String brandId) {
            return httpClient.get(basePath + "/" + brandId);
        }

        public Map<String, Object> listCampaigns(String brandId) {
            return httpClient.get(basePath + "/" + brandId + "/campaigns");
        }

        public Map<String, Object> listCampaigns(String brandId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + brandId + "/campaigns", queryParams);
        }

        public Map<String, Object> createCampaign(String brandId, Map<String, Object> body) {
            return httpClient.post(basePath + "/" + brandId + "/campaigns", body);
        }
    }

    /**
     * 10DLC campaign management — get / update (PUT) plus number / order
     * sub-resources.
     */
    public static class RegistryCampaigns {

        private final HttpClient httpClient;
        private final String basePath;

        public RegistryCampaigns(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> get(String campaignId) {
            return httpClient.get(basePath + "/" + campaignId);
        }

        public Map<String, Object> update(String campaignId, Map<String, Object> body) {
            return httpClient.put(basePath + "/" + campaignId, body);
        }

        public Map<String, Object> listNumbers(String campaignId) {
            return httpClient.get(basePath + "/" + campaignId + "/numbers");
        }

        public Map<String, Object> listNumbers(String campaignId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + campaignId + "/numbers", queryParams);
        }

        public Map<String, Object> listOrders(String campaignId) {
            return httpClient.get(basePath + "/" + campaignId + "/orders");
        }

        public Map<String, Object> listOrders(String campaignId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + campaignId + "/orders", queryParams);
        }

        public Map<String, Object> createOrder(String campaignId, Map<String, Object> body) {
            return httpClient.post(basePath + "/" + campaignId + "/orders", body);
        }
    }

    /**
     * 10DLC assignment-order management — read-only get.
     */
    public static class RegistryOrders {

        private final HttpClient httpClient;
        private final String basePath;

        public RegistryOrders(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> get(String orderId) {
            return httpClient.get(basePath + "/" + orderId);
        }
    }

    /**
     * 10DLC number-assignment management — release a number.
     */
    public static class RegistryNumbers {

        private final HttpClient httpClient;
        private final String basePath;

        public RegistryNumbers(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> delete(String numberId) {
            return httpClient.delete(basePath + "/" + numberId);
        }
    }
}
