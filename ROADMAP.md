# Roadmap — Priore

Este arquivo concentra as próximas evoluções do Priore durante a calibração.

## v0.6 — paper trading local

- [x] **Simulação automática de BUY/SELL confirmados**
  - `BUY_SETUP`/`SELL_SETUP` abre uma simulação local automaticamente.
  - Nenhuma ordem é enviada à cTrader.
  - Registra horário, direção, entrada, stop, alvo, R:R, M15, suporte, resistência, ATR e motivo do sinal.

- [x] **Lifecycle persistente da simulação**
  - Uma simulação permanece ativa até atingir TP ou SL.
  - O preço atual e o P/L teórico em pontos são acompanhados continuamente.
  - Quando TP é atingido: `WIN`.
  - Quando SL é atingido: `LOSS`.
  - Entrada e saída ficam registradas localmente.

- [x] **Tela separada de Simulações**
  - Simulação em andamento.
  - Histórico completo.
  - WIN/LOSS, taxa de acerto e saldo teórico em pontos.
  - Contexto técnico de cada entrada.

- [x] **Correção de rolagem**
  - Preservar/restaurar a posição do `ScrollView` quando atualizações de preço provocarem re-render.
  - A tela não deve voltar ao topo enquanto o usuário estiver lendo o rodapé.
  - Futuramente substituir o re-render completo por atualização granular dos campos.

- [x] **Contadores M5/M15**
  - `M5 fecha em mm:ss`, próximo horário e contador secundário M15.

- [x] **Distância até decisão e níveis**
  - Distância para confirmação/invalidação.
  - Distância para suporte/resistência em pontos e ATR.

## P0 — próxima rodada de calibração

- [ ] **Expiração / preço perseguido**
  - Definir quando um setup deixa de ser uma entrada válida porque o preço já se afastou demais.
  - Registrar o setup, mas marcar `ENTRADA PERDIDA` em vez de iniciar uma simulação tardia.

- [ ] **Memória de transição entre estados**
  - `WATCH_BUY → WAIT` como tentativa compradora fracassada.
  - `WATCH_SELL → WAIT` como tentativa vendedora fracassada.
  - Avaliar cooldown de 1–2 candles antes de repetir a mesma tese.

- [ ] **Sincronizar contador com timestamp da cTrader**
  - Usar o timestamp do feed como referência principal.
  - Destacar discretamente os últimos 30–45 segundos do candle.

- [ ] **Resultado mais granular**
  - Registrar se o encerramento ocorreu exatamente por TP/SL ou por salto de preço além do nível.
  - Registrar duração da operação e R realizado.

- [ ] **MAE/MFE**
  - Maior excursão favorável e adversa durante cada simulação.
  - Drawdown por trade.
  - Payoff e expectativa matemática.

## Estratégia e calibração

- [ ] Histórico completo das transições `AGUARDAR`, `OBSERVAR COMPRA`, `OBSERVAR VENDA`, `POSSÍVEL COMPRA` e `POSSÍVEL VENDA`.
- [x] Registrar suporte, resistência, ATR, confirmação e invalidação.
- [x] Registrar resultados simulados automaticamente.
- [ ] Calibrar thresholds somente após amostra suficiente.
- [ ] Filtro de spread.
- [ ] Filtro por sessão/horário.
- [ ] Aviso/bloqueio em notícias macroeconômicas de alto impacto para ouro.
- [ ] TP1/TP2 e gestão parcial somente depois da validação da estratégia base.

## UX

- [x] Preservar posição da rolagem durante atualização de preço.
- [x] Tela separada de simulações/histórico.
- [ ] Atualizar apenas componentes alterados em vez de reconstruir toda a tela.
- [ ] Melhorar hierarquia visual do card técnico.
- [ ] Zona de decisão comprador/vendedor em `AGUARDAR`.
- [ ] Diferenciar visualmente `WATCH` e `SETUP` sem excesso visual.

## Atualizações

- [x] Consulta manual pelo GitHub.
- [x] Manter atualização manual durante calibração.
- [ ] Assinatura Android estável para updates instaláveis por cima.

## Segurança e operação

- [x] Credenciais fora do código/GitHub e protegidas localmente.
- [x] Paper trading não envia ordens, independentemente do scope do token.
- [ ] Diagnóstico de reconexão mais detalhado.
- [ ] Watchdog para encerramento do foreground service pelo Android/fabricante.

## Futuro

- [ ] Dashboard estatístico completo.
- [ ] Backtesting/replay do XAUUSD.
- [ ] Outros ativos somente depois de estabilizar XAUUSD.
- [ ] Backend em nuvem apenas se surgir requisito real de 24/7 independente do celular.
- [ ] Qualquer discussão futura de execução real exige revisão específica de risco e decisão explícita.

## Princípios

1. **Vela fechada antes de sinal.**
2. **Explicar a decisão, não só exibir BUY/SELL.**
3. **Não inventar precisão estatística sem dados.**
4. **Preservar risco/retorno e contexto M15.**
5. **Menos alertas, mais qualidade.**
6. **Paper trading antes de qualquer automação operacional.**
