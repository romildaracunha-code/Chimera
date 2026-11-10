# NFC Lab 3.1 — operação e proteção de dados

Esta entrega evolui a V3 existente. Código em `/workspace/NFC-Lab`; destino de extração no Windows: `D:\NFC-Lab`. Application ID `lab.nfc`, versionCode 4, versão `3.1-hardened`. Não desinstale nem limpe dados para atualizar. A assinatura anterior foi preservada.

## Funcionalidades entregues

| Área | Implementação | Verificação executada | Limite |
| --- | --- | --- | --- |
| Guided Workflow | Inventário → plano → observação → anexos → revisão → relatório individual, seleção de execução e rascunho criptografado | Compilação; regras do ledger cobertas por testes JVM | Navegação/SAF e execução presencial ainda precisam de Android real |
| Persistência | Room 2.6.1, schema versionado, transações e chunks de 64 KiB; importação dos JSONs/SharedPreferences anteriores com conferência de leitura | SQLite/Room em Robolectric: migração, reabertura, rollback, chave incorreta e escritor obsoleto | Registros legados e ledger continuam snapshots JSON criptografados; ainda sem paginação para históricos muito grandes |
| Recuperação | Inicialização/migração fora da UI; sessões pendentes marcadas interrompidas após novo processo; HCE não é rearmado silenciosamente | Teste integrado de migração e reinício lógico | Confirmações físicas não são restauradas automaticamente; processo morto antes de salvar pode perder até 350 ms de edição do rascunho |
| Backup | Backup completo portátil com senha, autenticação antes da restauração, prévia, substituição Room atômica, ponto de recuperação local | AES-GCM/PBKDF2, senha incorreta/adulteração; rollback SQLite; interoperabilidade Kotlin/Python | Backup materializa dados em RAM, limite agregado de 70 MiB e envelope de 100 MiB; equipamentos com pouca memória exigem conjuntos menores |
| EMV | TVR, TSI, AIP, CVM Results/List e alertas contextuais; TTQ/CTQ explicitamente dependem do kernel | Testes de bits, comprimentos, CVM e inconsistências | Interpretação estrutural/declarada; sem validação criptográfica de pagamentos |
| Importação/comparação | JSON de sessões NFC Lab, PCSC e exchanges; separa sessões e origem simulada/importada; mantém tentativas inválidas, falhas, timestamps e latências declaradas; comparação de sequência/configuração/estrutura | Casos de regressão de PCSC, falha, lote misto e comparação | Até 400.000 caracteres por arquivo, 128 sessões, 128 tentativas por sessão; tempos de API não são tempos RF |
| Proteção | AES-256-GCM com chave Android Keystore; contexto do registro autenticado; temporário de ZIP criptografado; exportação em claro somente no destino escolhido | Criptografia testada no JVM; teste de Keystore Android compilado | Keystore e eventual proteção por hardware **não verificados neste ambiente**; sem garantia de apagamento físico em flash |
| Retenção | Prévia por dias e exclusão explícita de anexos/análises; remoção de anexo invalida revisão; journal e execuções mantidos | Testes de exclusão, revisão, retenção exata e integridade | Não há serviço periódico de retenção; cada exclusão é uma transação auditada, lote pode ser parcial se ocorrer falha |
| SDK | Contrato tipado `OfficialSdkAdapter`, perfil persistente de integração, resumo oficial por campos e adaptador não configurado com bloqueios explícitos | Testes do contrato e validação do resumo | Nenhum SDK proprietário instalado; não executa L2/L3 sem integração real |
| NFC existente | HCE proprietário, Reader Mode, ISO-DEP, histórico e exportação preservados | Regressão de protocolo/compatibilidade e compilação | Sem dispositivos ADB conectados; nenhum novo teste físico executado |

## Atualizar e operar

1. Instale o APK com `adb install -r NFC-Lab-3.1-hardened-debug.apk`, mantendo a versão anterior e os dados. Se houver erro de assinatura, não desinstale: confira o certificado e use o keystore original.
2. Na primeira abertura, aguarde a verificação/migração. JSONs legados são removidos **somente depois de armazenados e relidos corretamente no Room**. Em erro, o app bloqueia a abertura e preserva o conteúdo disponível; não limpa o banco automaticamente.
3. Em **Data Protection**, crie um backup com senha própria de pelo menos 12 caracteres e guarde-a separadamente. O Android Backup permanece desativado.
4. Cadastre o terminal em **Terminal Inventory**. Use **Guided Workflow** para criar/retomar um plano, registrar o observado, anexar arquivos autorizados/redigidos e pedir revisão distinta do operador.
5. Para resumo oficial de homologação, use **SDK Integrations** ou **Security Evidence**. PASS do caso e resultado de autenticação são campos separados. Uma declaração importada não recebe verificação criptográfica local.
6. Importe traces em **EMV Explorer**. Arquivos JSON NFC Lab/PCSC são detectados ao selecionar; a origem permanece importada ou simulada. A comparação apresenta sequência/SW/falhas, configuração e métricas declaradas quando presentes.
7. Revise nomes, notas, contexto e conteúdo dos anexos antes de marcar a confirmação em **Technical Reports**. Arquivos PDF/fotos não recebem DLP automático.
8. Para restaurar: informe a senha, abra o `.nfcb`, confira a prévia, encerre leitor/HCE e confirme a substituição. O app reabre e mantém as sessões anteriormente em andamento como interrompidas. A mesma tela permite revisar o ponto anterior de recuperação.

Roteiro físico de dois aparelhos e PCSC: [manual existente](../chimera/PHYSICAL_MANUAL.md). Casos financeiros somente por kit/SDK/credencial oficial e ambiente autorizado de homologação. Não interpretar SELECT/9000 como autenticação, autorização ou liquidação.

## Arquitetura e formatos

- `core/storage`: `SecureRepository`, Room (`VaultDatabase`, `VaultDao`, `VaultRow`), adaptador do ledger e `PortableBackup`. A chave do registro + índice identifica chunks; payload é autenticado antes de ser usado. Não usa `fallbackToDestructiveMigration`. O schema 1 está em `app/schemas/`; mudanças futuras exigem migração explícita.
- `core/security/SecureCodec`: envelope local `NFCLAB01 || nonce(12) || AES-GCM ciphertext+tag(16)`, AAD = chave lógica do registro. Chave AES-256 criada pelo Android Keystore, alias `nfclab.storage.v1`. Não exige biometria para permitir HCE em background. Dados podem aparecer em memória durante o uso; os nomes lógicos dos registros/IDs não são secretos no schema.
- Backup: `NFCPBK01 || salt(16) || envelope local`, PBKDF2-HMAC-SHA256/310.000 iterações/AES-256, AAD `portable-backup-v1`. Conteúdo é JSON versionado com registros Base64, hashes e ledger/anexos. A chave do Keystore não é exportada. A senha não é persistida. Um backup deve ser tratado como material sensível, mesmo criptografado.
- `core/emv`: `EmvSemantics` e `TraceImport`. A análise persiste resultados sanitizados, não a entrada EMV bruta. Capturas importadas nunca viram `REAL_HARDWARE` local.
- `core/integration/OfficialSdk.kt`: interface de fabricante, requisições e resultados tipados. O adaptador padrão retorna `Blocked`, não um sucesso simulado.
- Rascunho de fluxo salvo com debounce de 350 ms e sincronizado em encerramento normal. Exportações de texto pendentes são cifradas no Room; contexto de anexação e caminho de exportação são preservados em estado Android. Se uma importação textual for interrompida pela recriação da Activity, o app pede nova seleção, sem associar o arquivo a uma execução diferente.

A migração é idempotente por registro. Se houver divergência entre cópia antiga e Room, ambas são preservadas e a abertura é bloqueada. Inicialização Room e migração ocorrem fora da thread principal; algumas APIs legadas ainda aguardam gravações síncronas no worker, portanto históricos muito grandes podem provocar pausas de UI. Todas as gravações de snapshot legadas/ledger carregam geração do workspace, impedindo que referências anteriores à restauração sobrescrevam os dados restaurados.

## Compilar e verificar

Requer **JDK completo 17**, não apenas JRE; Android SDK API 35, Build Tools 34.0.0 e Gradle Wrapper 8.9. Android Studio pode configurar o SDK. `local.properties` e keystore não são distribuídos.

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
python -m unittest discover -s tools/physical/tests -v
python -m unittest discover -s tools/chimera/tests -v
python tools/chimera/verify_portable.py caminho/backup.nfcb
```

O verificador portátil requer `cryptography` (Apache-2.0/BSD), solicita a senha interativamente e não extrai arquivos. As outras ferramentas anteriores continuam disponíveis. Em ambiente com proxy, configure proxy/certificados também no processo dos testes Robolectric; não desative validação TLS. A primeira execução baixa o runtime Android de testes, mesmo com as dependências Gradle já em cache.

## Bloqueios e limites restantes

- Nenhum celular/terminal/leitor conectado ao executor. Compatibilidade real, RF, latências de hardware, tela/SAF, Android Keystore e atualização de uma instalação real continuam **não validados presencialmente**.
- Fabricante/modelo/firmware das aproximadamente 40 maquininhas e do celular ainda desconhecidos; cadastre presencialmente. Sem supor acesso administrativo, adquirente ou firmware.
- Integração real de SDK, planos L2/L3, credenciais oficiais, acesso e logs de host/terminal dependem do fornecedor/ambiente autorizado.
- Perda/invalidação da chave Keystore impede ler o banco antigo. O app falha fechado e não recria chave sobre dados existentes. A recuperação exige backup portátil com senha válida; mantenha-o antes de reinstalar/trocar aparelho. A tela de abertura em erro ainda não oferece restauração direta sobre um Keystore inválido; preserve os dados e use o backup validado numa instalação acessível. Não há recuperação criptográfica sem chave/senha.
- Exportações autorizadas no armazenamento escolhido saem em claro; para transporte confidencial use `.nfcb`. Anexos/texto livre exigem revisão humana. Exclusões não removem cópias exportadas ou backups antigos.
- Não há paginação de históricos nem testes de grande volume no celular. Não há teste visual automatizado Compose nesta sprint. Nenhuma comprovação de vulnerabilidade ou autorização bancária foi obtida.

Veja [resultados executados](VALIDATION.md) e os arquivos de evidência de software. Nenhuma fixture foi registrada como experimento real do usuário.
