package com.tienda.ms_pagos.service;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorTarjetaTest {

    @Test
    void luhn() {
        assertTrue(ValidadorTarjeta.luhn("4051885600446623"));   // tarjeta de prueba Webpay
        assertTrue(ValidadorTarjeta.luhn("4000000000000002"));
        assertFalse(ValidadorTarjeta.luhn("4051885600446624"));
        assertFalse(ValidadorTarjeta.luhn("123"));
    }

    @Test
    void vencimientoYCvv() {
        YearMonth hoy = YearMonth.of(2026, 11);
        assertTrue(ValidadorTarjeta.vencimientoValido("12/28", hoy));
        assertTrue(ValidadorTarjeta.vencimientoValido("11/26", hoy));
        assertFalse(ValidadorTarjeta.vencimientoValido("10/26", hoy));
        assertFalse(ValidadorTarjeta.vencimientoValido("13/30", hoy));
        assertTrue(ValidadorTarjeta.cvvValido("123"));
        assertFalse(ValidadorTarjeta.cvvValido("12a"));
    }

    @Test
    void huellaYMascara() {
        assertEquals(ValidadorTarjeta.huella("4051885600446623"), ValidadorTarjeta.huella("4051885600446623"));
        assertNotEquals(ValidadorTarjeta.huella("4051885600446623"), ValidadorTarjeta.huella("4000000000000002"));
        assertEquals("**** 6623", ValidadorTarjeta.mascara("4051885600446623"));
    }
}
