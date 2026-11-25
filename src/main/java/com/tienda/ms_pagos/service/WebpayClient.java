package com.tienda.ms_pagos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Cliente REST de Transbank Webpay Plus (API v1.2). En integracion usa el comercio de prueba 597055555532.
 *   crear   POST  /transactions                 {buy_order, session_id, amount, return_url} -> {token, url}
 *   confirm PUT   /transactions/{token}          -> {status, response_code, amount, card_detail{card_number}, authorization_code...}
 *   anular  POST  /transactions/{token}/refunds  {amount}
 */
@Component
public class WebpayClient {

    private static final Logger log = LoggerFactory.getLogger(WebpayClient.class);

    private final RestClient http;

    public WebpayClient(@Value("${app.webpay.url}") String url,
                        @Value("${app.webpay.commerce-code}") String commerceCode,
                        @Value("${app.webpay.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeader("Tbk-Api-Key-Id", commerceCode)
                .defaultHeader("Tbk-Api-Key-Secret", apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> crear(String buyOrder, String sessionId, long monto, String returnUrl) {
        Map<String, Object> cuerpo = Map.of("buy_order", buyOrder, "session_id", sessionId,
                "amount", monto, "return_url", returnUrl);
        Map<String, Object> r = http.post().uri("/transactions").contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo).retrieve().body(Map.class);
        log.info("[Webpay] transaccion creada buyOrder={} token={}", buyOrder, r == null ? null : r.get("token"));
        return r;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> confirmar(String token) {
        Map<String, Object> r = http.put().uri("/transactions/{token}", token).contentType(MediaType.APPLICATION_JSON)
                .retrieve().body(Map.class);
        log.info("[Webpay] commit token={} -> status={} response_code={}", token,
                r == null ? null : r.get("status"), r == null ? null : r.get("response_code"));
        return r;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> anular(String token, long monto) {
        Map<String, Object> r = http.post().uri("/transactions/{token}/refunds", token).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", monto)).retrieve().body(Map.class);
        log.warn("[Webpay] reembolso token={} -> {}", token, r);
        return r;
    }
}
