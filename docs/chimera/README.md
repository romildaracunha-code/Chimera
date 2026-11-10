# NFC Lab V3 — Operação Chimera

Evolução do NFC Lab existente, sem reconstrução do aplicativo. Cloud: `/workspace/NFC-Lab`. Destino Windows: extrair o pacote em `D:\NFC-Lab`; essa unidade não está montada neste executor Linux.

**Nenhum experimento físico ou vulnerabilidade foi comprovado neste executor.** O Android e os aproximadamente 40 terminais informados pelo usuário não estão conectados. Não há cartões, SDKs, chaves ou host oficial de homologação disponíveis. O aplicativo e as ferramentas não implementam relay, falsificação de cartão, geração de criptograma EMV nem bypass de autorização.

## O que mudou

- Parsing ISO 7816 de comandos curtos e estendidos, preservando a API curta antiga.
- Gerenciador de conexão ISO-DEP com estado, limite do transporte, seleção do namespace Lab e encerramento em falhas. Diferencia comando rejeitado antes do envio, tentativa sem resposta e resposta recebida com framing inválido.
- Três AIDs proprietários pré-registrados: `F04E46434C414201`, `...02`, `...03`. AID/perfil/cenário são congelados por sessão. HCE continua fictício, com SELECT/GET DATA e caso negativo de GET DATA `6985`; não é aplicação EMV de pagamento.
- **EMV Explorer:** BER-TLV, dicionário, APDU, DOL, AFL/AIP, CID, estruturas FCI/records, timeline de traces importados e comparação sanitizada. Origem pública/importada/sintética é explícita. Não valida chaves, assinaturas, criptogramas ou autorização.
- **Vulnerability Research:** quatro referências pesquisadas, com classe da evidência, configurações históricas, mitigação publicada, escopo atual desconhecido e dependências de homologação.
- **Physical Test Lab / Terminal Inventory / Test Case Manager:** planos por equipamento e equipe, snapshots das versões/casos, ligação de caso oficial, resultados e bloqueios. As interfaces antigas Physical e Devices continuam disponíveis, com seus dados intactos.
- **Security Evidence:** anexos reais por SAF, cópia privada, SHA256, journal, revisão por operador distinto, etapas e invalidação da revisão após alteração. Resumo do SDK e decisão do processador são declarações externas, não verificação criptográfica local.
- **Red/Blue Comparison / Technical Reports:** contexto antes/depois, divergências terminal/processador, JSON/CSV/Markdown e ZIP com anexos/manifesto. Nenhuma conclusão automática de correção ou aprovação financeira.

## Documentação

- [Auditoria inicial e baseline](AUDIT.md)
- [Arquitetura, schemas e limites](ARCHITECTURE.md)
- [Manual físico e casos autorizados](PHYSICAL_MANUAL.md)
- [Analisador EMV e corpus público](EMV.md)
- [Catálogo de pesquisas](RESEARCH.md)
- [Matriz de compatibilidade](compatibility.csv)
- [Validação real, testes e bloqueios](VALIDATION.md)
- [Ferramenta independente de verificação](../../tools/chimera/README.md)

## Uso do aplicativo

1. Instalar o APK V3 com `adb install -r` no aparelho autorizado. O mesmo applicationId/keystore é preservado na entrega; não desinstalar nem limpar dados para atualizar. Reconstruções precisam da mesma chave de debug para atualizar uma instalação existente.
2. Na aba **Terminal Inventory**, identificar o equipamento por etiqueta/menu normal/documentação autorizada. Não presumir acesso administrativo. Campos desconhecidos devem continuar desconhecidos.
3. Na aba **Test Case Manager**, escolher um caso Lab ou vincular o ID/versão real de um caso oficial. Referência digitada não comprova que acesso/licença foi concedido. Um caso EMV sem referência oficial é bloqueado.
4. Em **Physical Test Lab**, selecionar terminal, caso, operador, celular e ambiente. Red cria baseline; Blue exige um baseline Red. O plano nasce `UNVERIFIED` e não executa pagamento.
5. Executar a experiência presencial pelo protocolo Lab ou suite oficial autorizada, conforme o manual. Registrar todas as falhas e condições, sem substituir por simulação.
6. Em **Security Evidence**, selecionar a execução, registrar etapa/resultado, anexar os arquivos já redigidos e associar resumo do SDK/host quando aplicável. Somente o revisor distinto, com referências e artefatos suficientes, pode registrar revisão documental.
7. Em **EMV Explorer**, carregar fixture pública, importar hexadecimal autorizado ou um trace JSON. Uma fixture editada deixa de ser pública verificada. Resultados persistidos são sanitizados; dados brutos de análise não são salvos.
8. Exportar relatórios e ZIP em **Technical Reports**. Verificar o ZIP na estação com `tools/chimera/verify_bundle.py`. A consistência de hashes não comprova autenticidade física.

As telas são utilitárias e preservam o tema escuro atual. A navegação visual e o transporte desta versão ainda requerem verificação em Android físico. Os módulos novos têm testes de lógica/persistência/exportação; não são apenas protótipos de interface.

## Compilar e verificar

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
python -m unittest discover -s tools/physical/tests -v
python -m unittest discover -s tools/chimera/tests -v
```

Na entrega Cloud o SDK está em `/workspace/toolchains/android-sdk`, Gradle 8.9 e o keystore original permanece fora do repositório em `ANDROID_USER_HOME`. Nenhuma chave é incluída no ZIP/Git. No Windows, abrir a pasta no Android Studio e configurar SDK/JDK 17 ou 21; `local.properties` não é portátil e não é empacotado. Não modificar outros projetos da unidade D:.

## Critério de comprovação

Compilação, fixtures, casos sintéticos, importação e revisão documental não comprovam vulnerabilidade em hardware. Prova de vulnerabilidade exige experiência física autorizada, identificação/versões, artefatos de ambos os lados e avaliação técnica independente. Não há esse resultado nesta entrega.
