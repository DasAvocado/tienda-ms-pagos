package com.tienda.ms_pagos.service;

import com.tienda.ms_pagos.dto.PagoTarjetaRequest;
import com.tienda.ms_pagos.messaging.PagoPublisher;
import com.tienda.ms_pagos.model.AlertaFraude;
import com.tienda.ms_pagos.model.EstadoPago;
import com.tienda.ms_pagos.model.Pago;
import com.tienda.ms_pagos.repository.AlertaFraudeRepository;
import com.tienda.ms_pagos.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PagoServiceTest {

    private PagoRepository pagos;
    private AlertaFraudeRepository alertas;
    private PagoPublisher publisher;
    private PagoService service;

    @BeforeEach
    void preparar() {
        pagos = mock(PagoRepository.class);
        alertas = mock(AlertaFraudeRepository.class);
        publisher = mock(PagoPublisher.class);
        AtomicLong ids = new AtomicLong();
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) p.setId(ids.incrementAndGet());
            return p;
        });
        when(alertas.save(any(AlertaFraude.class))).thenAnswer(inv -> inv.getArgument(0));
        FraudeService fraude = new FraudeService(pagos, alertas, publisher, 3, 10, Clock.systemUTC());
        service = new PagoService(pagos, fraude, publisher, mock(WebpayClient.class),
                "http://localhost:8087/api/pagos/webpay/retorno", List.of("*"));
    }

    private static PagoTarjetaRequest tarjeta(String numero) {
        return new PagoTarjetaRequest(numero, "12/30", "123", "Ana", 30990.0);
    }

    @Test
    void apruebaTarjetaValida() {
        when(pagos.countByTarjetaHuellaAndIntentoAfter(anyString(), any(Instant.class))).thenReturn(0L);

        Pago p = service.pagarConTarjeta("u1", "a@b.cl", "Ana", tarjeta("4051 8856 0044 6623"));

        assertEquals(EstadoPago.APROBADO, p.getEstado());
        assertEquals("**** 6623", p.getTarjetaMascara());
        verify(publisher).resultado(p);
    }

    @Test
    void rechazaTarjetaDePruebaDelEmisor() {
        when(pagos.countByTarjetaHuellaAndIntentoAfter(anyString(), any(Instant.class))).thenReturn(0L);

        Pago p = service.pagarConTarjeta("u1", "a@b.cl", "Ana", tarjeta("4000000000000002"));

        assertEquals(EstadoPago.RECHAZADO, p.getEstado());
    }

    @Test
    void cuartoIntentoEnDiezSegundosSeBloqueaYGeneraAlerta() {
        // ya hubo 3 intentos con la misma tarjeta en la ventana -> el 4.o (mas de 3) se bloquea
        when(pagos.countByTarjetaHuellaAndIntentoAfter(anyString(), any(Instant.class))).thenReturn(3L);

        Pago p = service.pagarConTarjeta("u1", "a@b.cl", "Ana", tarjeta("4051885600446623"));

        assertEquals(EstadoPago.BLOQUEADO_FRAUDE, p.getEstado());
        verify(alertas).save(any(AlertaFraude.class));
        verify(publisher).alertaFraude(any(), anyString());
    }

    @Test
    void tercerIntentoAunNoEsFraude() {
        when(pagos.countByTarjetaHuellaAndIntentoAfter(anyString(), any(Instant.class))).thenReturn(2L);

        assertEquals(EstadoPago.APROBADO, service.pagarConTarjeta("u1", "a@b.cl", "Ana", tarjeta("4051885600446623")).getEstado());
        verify(alertas, never()).save(any());
    }

    @Test
    void validaDatosAntesDeContarIntentos() {
        assertThrows(ResponseStatusException.class,
                () -> service.pagarConTarjeta("u1", "a@b.cl", "Ana", tarjeta("4051885600446624")));
        verify(pagos, never()).save(any());
    }

    @Test
    void origenDeRetornoSoloHttpSinRuta() {
        assertEquals("https://3.80.1.2", service.origenValido("https://3.80.1.2"));
        assertEquals("http://localhost:4200", service.origenValido("javascript:alert(1)"));
        assertEquals("http://localhost:4200", service.origenValido("https://evil.com/robar?x=1"));
    }
}
