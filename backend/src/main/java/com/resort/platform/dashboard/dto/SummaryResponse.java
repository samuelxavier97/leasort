package com.resort.platform.dashboard.dto;

/** Resumo do dashboard (§16.7): a versão do ADMIN ou a do PROSPECTOR. */
public sealed interface SummaryResponse permits AdminSummary, ProspectorSummary {}
