package com.tienda.ms_pagos.dto;

/** returnOrigin = origen del frontend (https://IP) al que se vuelve despues de pagar en Webpay. */
public record WebpayIniciarRequest(Double monto, String returnOrigin) {}
