package com.tienda.ms_pagos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;

/** Validaciones de tarjeta (logica pura). */
public final class ValidadorTarjeta {

    private ValidadorTarjeta() {}

    /** Tarjeta de prueba que el "emisor" simulado siempre rechaza (sirve para la demo de fraude). */
    public static final String TARJETA_RECHAZADA = "4000000000000002";

    public static String soloDigitos(String numero) {
        return numero == null ? "" : numero.replaceAll("\\D", "");
    }

    /** Algoritmo de Luhn (digito verificador de las tarjetas). */
    public static boolean luhn(String digitos) {
        if (digitos.length() < 13 || digitos.length() > 19) return false;
        int suma = 0;
        boolean doble = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (doble) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            doble = !doble;
        }
        return suma % 10 == 0;
    }

    /** "MM/AA" no vencida. */
    public static boolean vencimientoValido(String mmaa, YearMonth hoy) {
        if (mmaa == null || !mmaa.matches("(0[1-9]|1[0-2])/\\d{2}")) return false;
        YearMonth venc = YearMonth.of(2000 + Integer.parseInt(mmaa.substring(3)), Integer.parseInt(mmaa.substring(0, 2)));
        return !venc.isBefore(hoy);
    }

    public static boolean cvvValido(String cvv) {
        return cvv != null && cvv.matches("\\d{3,4}");
    }

    public static String mascara(String digitos) {
        return "**** " + digitos.substring(Math.max(0, digitos.length() - 4));
    }

    /** Huella irreversible de la tarjeta: permite detectar repeticiones sin guardar el numero. */
    public static String huella(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
