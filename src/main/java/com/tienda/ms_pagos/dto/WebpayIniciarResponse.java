package com.tienda.ms_pagos.dto;

/** El frontend hace un POST (formulario) a "url" con el campo token_ws = token. */
public record WebpayIniciarResponse(Long pagoId, String token, String url) {}
