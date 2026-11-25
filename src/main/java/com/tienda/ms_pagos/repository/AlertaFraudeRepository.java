package com.tienda.ms_pagos.repository;

import com.tienda.ms_pagos.model.AlertaFraude;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaFraudeRepository extends JpaRepository<AlertaFraude, Long> {

    List<AlertaFraude> findTop100ByOrderByFechaDesc();
}
