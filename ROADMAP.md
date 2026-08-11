# Roadmap — Priore

Este arquivo concentra as próximas evoluções do Priore para evitar que melhorias discutidas durante os testes se percam entre versões.

## v0.5 — rodada atual

- [x] **Lifecycle persistente de setup DEMO executado**
  - Depois de `BUY_SETUP`/`SELL_SETUP`, uma operação Priore executada em DEMO permanece acompanhada até o desfecho.
  - Persistir direção, entrada indicada, entrada efetiva, stop, alvo, R:R, horário, positionId/orderId, volume e preço atual.
  - Impedir que o candle seguinte substitua uma operação Priore ainda aberta por um novo setup.

- [x] **Resultado da operação DEMO pela própria cTrader**
  - Receber eventos de execução/fechamento.
  - Registrar `WIN`, `LOSS` ou `ERROR` no histórico local.
  - Guardar preço de saída e P/L bruto quando fornecidos pela cTrader.
  - Recuperar posições `PRIORE_DEMO` após reconexão usando reconcile.

- [x] **Autoexecução opcional e rigidamente limitada a DEMO**
  - Botão explícito para ativar/desativar.
  - Uma operação Priore por vez.
  - Ordem a mercado no menor volume permitido pelo XAUUSD da corretora.
  - SL/TP enviados junto à ordem como proteção no servidor da cTrader.
  - Qualquer ambiente `live` bloqueia o caminho de execução no código e desativa o toggle.
  - Token precisa ter permissão `trading`; token somente leitura continua suficiente para monitoramento.

- [x] **Contador regressivo M5/M15**
  - Exibir `M5 fecha em mm:ss`, horário do próximo fechamento e contador secundário M15.
  - Implementação inicial usa limites temporais do relógio do aparelho.

- [x] **Distância até decisão**
  - Em `WATCH`, mostrar distância atual até confirmação e invalidação.

- [x] **Distância para suporte/resistência**
  - Mostrar distância absoluta em dólares e distância normalizada pelo ATR.

- [x] **Card de setup/operação ativo**
  - Exibir entrada indicada/real, preço atual, P/L em pontos, SL, TP, R:R, distância para SL/TP e idade do setup.
  - Trocar `PARA CONFIRMAR` por `STATUS DO SETUP` quando o sinal já estiver confirmado.

- [x] **Resumo inicial de histórico DEMO**
  - Total, WIN/LOSS, taxa de acerto simples e últimos resultados.

## P0 — próxima rodada de calibração

- [ ] **Expiração e preço perseguido**
  - Definir quando uma entrada deixa de ser executável por distância excessiva do preço original.
  - Exibir `setup tecnicamente válido, mas entrada original passou — não perseguir preço`.
  - Não gerar setup idêntico enquanto o anterior estiver ativo, salvo nova estrutura objetiva.

- [ ] **Memória de transição entre estados analíticos**
  - Preservar `WATCH_BUY → WAIT` como tentativa compradora fracassada/rejeição.
  - Preservar `WATCH_SELL → WAIT` como tentativa vendedora fracassada/rejeição.
  - Considerar cooldown de 1–2 candles antes de repetir exatamente o mesmo setup, sujeito a calibração em demo.

- [ ] **Sincronizar contador com timestamp da cTrader**
  - Usar o relógio/timestamp do feed como referência principal em vez do relógio local.
  - Nos últimos 30–45 segundos, destacar discretamente que o Priore aguarda fechamento.

- [ ] **Lifecycle analítico mesmo sem autoexecução**
  - Acompanhar um setup confirmado mesmo quando o toggle de execução DEMO estiver desligado.
  - Permitir resultado virtual separado de resultado executado.

- [ ] **Resultado/fechamento mais granular**
  - Distinguir fechamento por TP, SL, manual e outros eventos da cTrader.
  - Marcar `INVALIDADO`/`EXPIRADO` quando aplicável sem confundir com LOSS.

## Estratégia e calibração

- [ ] Registrar histórico completo das transições `AGUARDAR`, `OBSERVAR COMPRA`, `OBSERVAR VENDA`, `POSSÍVEL COMPRA` e `POSSÍVEL VENDA`.
- [x] Registrar níveis usados na decisão atual: suporte, resistência, ATR, confirmação e invalidação.
- [x] Começar a registrar resultado real de operações da estratégia na conta demo.
- [ ] Medir MAE/MFE, drawdown por operação, payoff, expectativa matemática e qualidade do R:R.
- [ ] Calibrar thresholds somente após acumular amostra real suficiente em conta demo.
- [ ] Adicionar filtro de spread antes de qualquer sinal acionável.
- [ ] Adicionar filtro por sessão/horário de mercado.
- [ ] Adicionar bloqueio ou aviso para notícias macroeconômicas de alto impacto relevantes ao ouro.
- [ ] Avaliar múltiplos alvos (TP1/TP2) e gestão parcial somente depois da validação da estratégia base.

## UX e notificações

- [ ] **Preservar posição de rolagem durante atualização de preço**
  - Hoje a tela pode voltar ao topo quando o preço atualiza enquanto o usuário está lendo o rodapé.
  - Evitar reconstruir o `ScrollView` inteiro a cada atualização de preço; preferir atualizar apenas os campos que mudaram.
  - Como solução transitória, preservar/restaurar `scrollY` quando houver re-render completo.
  - A atualização de preço não deve interromper a leitura nem mudar a posição visual do usuário.

- [ ] Melhorar hierarquia visual do card técnico para leitura em poucos segundos.
- [ ] Criar zona de decisão com cenário comprador e vendedor lado a lado quando estiver em `AGUARDAR`.
- [ ] Diferenciar visualmente `WATCH` de `SETUP` sem transformar a interface em árvore de Natal.
- [x] Pré-alerta somente quando houver mudança efetiva de estado, evitando spam.
- [x] Exibir horário local da última análise e idade da leitura.
- [ ] Criar tela dedicada de histórico; v0.5 mostra apenas resumo na tela principal.

## Atualizações do aplicativo

- [x] Consulta manual de versão pelo GitHub.
- [x] Manter **atualização manual** enquanto o Priore estiver em fase de calibração.
- [ ] Migrar para assinatura Android estável antes de usar atualização instalada por cima da versão anterior.
- [ ] Só considerar checagem automática de versão quando o fluxo de releases estiver maduro e previsível.

## Segurança e operação

- [x] Credenciais cTrader fora do código-fonte e do GitHub; armazenadas localmente no aparelho.
- [x] Armazenamento local protegido com Android Keystore.
- [x] Execução automática **somente em DEMO**, opt-in e hard-blocked em `live`.
- [x] Uma operação Priore DEMO por vez.
- [x] Menor volume permitido pelo símbolo durante a fase de teste.
- [ ] Adicionar diagnóstico de conectividade/reconexão mais detalhado na interface.
- [ ] Avaliar watchdog local para detectar monitoramento interrompido pelo Android/fabricante.

## Futuro — somente após validação do MVP

- [ ] Dashboard estatístico de desempenho da estratégia.
- [ ] Backtesting/replay com histórico do XAUUSD.
- [ ] Outros ativos e estratégias somente depois de o XAUUSD estar estável.
- [ ] Avaliar backend em nuvem apenas se o requisito real passar a ser monitoramento 24/7 independente do celular.
- [ ] **Não habilitar execução em LIVE durante a calibração.** Qualquer discussão futura sobre operação real exige nova revisão de risco, controles e decisão explícita do usuário.

## Princípios do Priore

1. **Vela fechada antes de sinal.**
2. **Explicar a decisão, não apenas exibir BUY/SELL.**
3. **Não inventar precisão estatística sem dados.**
4. **Preservar risco/retorno e contexto M15.**
5. **Menos alertas, mais qualidade.**
6. **DEMO e validação antes de qualquer discussão sobre LIVE.**
7. **Execução de teste deve ser explicitamente opt-in, pequena e rastreável.**
