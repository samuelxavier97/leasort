-- Chegadas do dia (§16.6, D-095): a lista filtra os acessos liberados pelo dia de entry_at em APP_TIMEZONE.
CREATE INDEX access_records_entry_at_ix ON access_records (entry_at) WHERE result = 'AUTHORIZED';
