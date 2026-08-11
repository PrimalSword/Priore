# Priore

Priore é um **assistente Android local para XAUUSD**. Nesta fase não existe servidor, nuvem ou Firebase: o próprio celular conecta diretamente à cTrader Open API, acompanha M5/M15, analisa fechamentos de vela e gera notificações locais quando aparece um setup técnico.

O MVP é deliberadamente **read-only**: não cria, modifica nem encerra ordens.

## Arquitetura atual

```text
cTrader Open API
        |
        | JSON / WebSocket :5036
        v
Priore Android
├── autenticação cTrader
├── descoberta automática de XAUUSD
├── histórico M5/M15
├── spot + live trendbars
├── StrategyEngine
│   ├── WAIT
│   ├── BUY_SETUP
│   └── SELL_SETUP
├── renovação de access token
├── armazenamento local protegido
└── notificações nativas Android
```

Enquanto o usuário ativa o monitoramento, o Priore roda como **foreground service** e mantém uma notificação persistente. O serviço não inicia sozinho no boot e pode ser interrompido a qualquer momento pelo aplicativo ou pela ação `Parar` da própria notificação.

## Segurança

Nenhuma credencial cTrader é incluída no código, APK ou GitHub.

Na primeira execução, o usuário informa no próprio aparelho:

- Client ID;
- Client Secret;
- Access Token;
- Refresh Token;
- ambiente `demo` ou `live`.

Os valores sensíveis são cifrados localmente com uma chave AES/GCM mantida no **Android Keystore**. O backup do aplicativo está desativado. O Client Secret continua necessariamente disponível em tempo de execução no aparelho, portanto um dispositivo comprometido/rootado não deve ser considerado um cofre inviolável.

O repositório é público: **nunca coloque tokens, secrets, keystores ou credenciais em arquivos versionados.**

## Fluxo cTrader

O cliente Android usa o endpoint JSON/WebSocket da cTrader:

- demo: `wss://demo.ctraderapi.com:5036/`;
- live: `wss://live.ctraderapi.com:5036/`.

O fluxo implementado é:

1. autenticação da aplicação;
2. descoberta das contas autorizadas pelo access token;
3. autenticação da conta do ambiente selecionado;
4. lista e resolução automática de `XAUUSD`;
5. carregamento de 300 candles históricos M5 e M15;
6. assinatura do spot;
7. assinatura das trendbars M5 e M15;
8. heartbeat a cada 10 segundos;
9. reconexão automática em caso de queda;
10. renovação do access token usando o refresh token quando a API indicar expiração.

Nenhuma mensagem de execução de ordem faz parte do cliente.

## Estratégia v0.2

A estratégia atual é uma baseline para validação em demo:

- M15: filtro de regime com EMA20/EMA50;
- M5: ATR(14);
- M5: suporte/resistência dos 12 candles anteriores;
- `SELL_SETUP`: M15 baixista + rejeição de resistência ou rompimento vendedor confirmado;
- `BUY_SETUP`: M15 altista + rejeição de suporte ou rompimento comprador confirmado;
- relação risco/retorno mínima: 1,8;
- decisão somente após fechamento M5 confirmado.

Quando não há confirmação suficiente, o resultado é `WAIT` e não existe notificação de sinal.

## Android

Requisitos do projeto:

- Android Studio atual;
- JDK 17;
- Android SDK 36;
- AGP 9.3.1;
- Gradle 9.5.0.

Abra a pasta `android/` no Android Studio e sincronize o projeto.

O APK de debug também é compilado automaticamente pelo GitHub Actions a cada push. O workflow executa o teste unitário da estratégia antes do build e publica `app-debug.apk` como artifact do workflow.

## Primeiro uso

1. Instale o APK em um aparelho Android.
2. Abra **Configurar cTrader**.
3. Informe as credenciais já emitidas pela cTrader Open API.
4. Mantenha `demo` durante a validação.
5. Salve.
6. Toque em **Iniciar monitoramento**.
7. Conceda a permissão de notificações quando solicitada.
8. Confirme no painel que a sequência de autenticação chegou a `Priore ativo · XAUUSD M5/M15`.

A notificação persistente indica que o monitoramento está ativo. Quando um fechamento M5 produzir `BUY_SETUP` ou `SELL_SETUP`, o próprio aparelho gera uma notificação com leitura, entrada indicativa, stop técnico e alvo.

## Limites do modo Android-only

Este desenho é propositalmente simples para validar o produto antes de criar infraestrutura. Ele não equivale a um monitor 24/7 em servidor.

O Priore deixa de monitorar se, por exemplo:

- o aparelho estiver desligado;
- ficar sem internet;
- o usuário parar o serviço;
- o aplicativo for forçado a parar;
- o fabricante/sistema encerrar o processo apesar do foreground service.

Por isso, esta arquitetura é adequada ao MVP e à validação da estratégia, não a uma promessa de disponibilidade contínua.

## Estrutura do repositório

```text
android/
├── app/
│   └── src/
│       ├── main/java/com/primalsword/priore/
│       │   ├── CTraderWebSocketClient.kt
│       │   ├── CredentialStore.kt
│       │   ├── MainActivity.kt
│       │   ├── MarketModels.kt
│       │   ├── PrioreApp.kt
│       │   ├── PrioreMonitorService.kt
│       │   ├── PrioreNotifications.kt
│       │   ├── SignalStore.kt
│       │   ├── StrategyEngine.kt
│       │   └── TokenRefresher.kt
│       └── test/
└── build.gradle.kts
```

## Próximas etapas

1. instalar o APK no aparelho físico;
2. validar autenticação com a conta cTrader demo autorizada;
3. conferir XAUUSD e precisão de preços da corretora;
4. comparar candles M5/M15 do Priore com o cTrader lado a lado;
5. registrar histórico de sinais e resultado posterior;
6. adicionar pre-alerta de aproximação de nível;
7. adicionar filtro de spread, sessão e notícias de alto impacto;
8. calibrar a estratégia com dados reais antes de qualquer discussão sobre execução automática.

## Aviso

Priore é um assistente analítico. XAUUSD é altamente volátil e operações alavancadas podem gerar perdas rápidas. Nenhuma regra de análise garante lucro.
