# Roadmap — Priore

Este arquivo concentra as próximas evoluções do Priore para evitar que melhorias discutidas durante os testes se percam entre versões.

## Próxima rodada — prioridade alta

- [ ] **Contador regressivo da vela M5**
  - Exibir `Fecha em mm:ss` no card de monitoramento.
  - Exibir também o horário do próximo fechamento, por exemplo `Próximo fechamento: 12:05`.
  - Preferir sincronização com a referência temporal da cTrader; usar o relógio local apenas como apoio.
  - Nos últimos 30–45 segundos, destacar discretamente que o Priore está aguardando confirmação de fechamento.

- [ ] **Contador da vela M15**
  - Exibir de forma secundária, sem competir visualmente com o M5.

- [ ] **Distância até a decisão**
  - Mostrar quanto falta, em dólares, para o threshold de confirmação do cenário atual.
  - Mostrar quanto falta para a invalidação.
  - Exemplo: `Falta +0,48 para confirmar`.

- [ ] **Distância para suporte/resistência**
  - Mostrar distância absoluta em dólares.
  - Mostrar distância normalizada pelo ATR.
  - Exemplo: `Suporte 4381,22 · +3,16 · 0,42 ATR`.

- [ ] **Zona de decisão mais visual**
  - Em `AGUARDAR`, explicar cenário comprador, cenário vendedor e qual evento faria o Priore mudar de estado.
  - Preservar a regra de decisão somente com vela fechada.

## Estratégia e calibração

- [ ] Registrar histórico local dos estados `AGUARDAR`, `OBSERVAR COMPRA`, `OBSERVAR VENDA`, `POSSÍVEL COMPRA` e `POSSÍVEL VENDA`.
- [ ] Registrar níveis usados em cada decisão: suporte, resistência, ATR, confirmação e invalidação.
- [ ] Medir o comportamento posterior de cada setup para avaliar assertividade, drawdown e qualidade do R:R.
- [ ] Calibrar thresholds somente após acumular amostra real suficiente em conta demo.
- [ ] Adicionar filtro de spread antes de qualquer sinal acionável.
- [ ] Adicionar filtro por sessão/horário de mercado.
- [ ] Adicionar bloqueio ou aviso para notícias macroeconômicas de alto impacto relevantes ao ouro.
- [ ] Avaliar múltiplos alvos (TP1/TP2) e gestão parcial somente depois da validação da estratégia base.

## UX e notificações

- [ ] Melhorar hierarquia visual do card técnico para leitura em poucos segundos.
- [ ] Diferenciar visualmente `WATCH` de `SETUP` sem transformar a interface em árvore de Natal.
- [ ] Pré-alerta apenas quando houver mudança efetiva de estado, evitando spam.
- [ ] Exibir horário local da última análise e idade da leitura.
- [ ] Criar tela simples de histórico de sinais e mudanças de estado.

## Atualizações do aplicativo

- [x] Consulta manual de versão pelo GitHub.
- [ ] Manter **atualização manual** enquanto o Priore estiver em fase de calibração.
- [ ] Migrar para assinatura Android estável antes de usar atualização instalada por cima da versão anterior.
- [ ] Só considerar checagem automática de versão quando o fluxo de releases estiver maduro e previsível.

## Segurança e operação

- [x] Credenciais cTrader fora do APK e do GitHub.
- [x] Armazenamento local protegido com Android Keystore.
- [x] Operação read-only: o Priore não envia ordens.
- [ ] Adicionar diagnóstico de conectividade/reconexão mais detalhado na interface.
- [ ] Avaliar watchdog local para detectar monitoramento interrompido pelo Android/fabricante.

## Futuro — somente após validação do MVP

- [ ] Dashboard estatístico de desempenho da estratégia.
- [ ] Backtesting/replay com histórico do XAUUSD.
- [ ] Outros ativos e estratégias somente depois de o XAUUSD estar estável.
- [ ] Avaliar backend em nuvem apenas se o requisito real passar a ser monitoramento 24/7 independente do celular.
- [ ] Discutir execução automatizada apenas após validação estatística robusta, controles de risco e decisão explícita de produto.

## Princípios do Priore

1. **Vela fechada antes de sinal.**
2. **Explicar a decisão, não apenas exibir BUY/SELL.**
3. **Não inventar precisão estatística sem dados.**
4. **Preservar risco/retorno e contexto M15.**
5. **Menos alertas, mais qualidade.**
6. **Demo e validação antes de qualquer automação operacional.**
