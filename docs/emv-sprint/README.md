# Sprint de segurança EMV em hardware real — preparação presencial

**Estado: preparação entregue; zero experimentos físicos executados neste executor.** O usuário informa aproximadamente 40 terminais e um Android NFC, mas não conhece os modelos/acessos. Em 2026-11-09, `adb devices -l` retornou lista vazia; nenhum leitor físico foi disponibilizado. Suporte HCE do celular, SDKs, homologação, cartões oficiais e interoperabilidade permanecem **não confirmados**.

O objetivo físico/EMV **não está concluído**. APK compilado e testes de software não são experimentos EMV físicos. As simulações antigas foram preservadas, sem novos simuladores de pagamento; não entram nas evidências desta sprint.

## Entregas

- APK `deliverables/NFC-Lab-1.1-physical-debug.apk`, mesmo applicationId `lab.nfc`, versionCode 2. Instalação com `adb install -r` preserva os dados, se a assinatura da instalação anterior coincidir.
- Código-fonte e testes; backup da entrega 1.0 em `backups/` com SHA256.
- Aba **Physical**: diagnóstico do Android, registro de observações por camada, caso oficial e referências de evidência. Registros são declarações do operador, não confirmação automática.
- Snapshot de marca/modelo/versões no experimento para preservar contexto quando o inventário mudar.
- **Devices** ampliado: adquirente, versão do aplicativo de pagamento, kernel, SDK, homologação, fonte e contato/ticket. Campos desconhecidos continuam explícitos. Atualização pelo mesmo ID interno.
- Sessões APDU com nanosegundos, origem da captura e erros de transporte. HCE grava assincronamente para reduzir bloqueio do caminho de resposta; eventos em fila podem se perder se o processo morrer antes de persistir.
- Ferramentas locais [tools/physical/README.md](../../tools/physical/README.md): diagnóstico ADB, troca física PC/SC exclusivamente com o AID de laboratório, índice SHA256, roteiro de evidências e geração de relatório.
- [40 fichas e inventário](inventory/): **slots não identificados**, não 40 terminais descobertos/testados. Nenhum resultado é preenchido como sucesso.
- [Roteiro presencial](RUNBOOK.md), [ferramentas e cartões](TOOLS.md), [matriz planejada](test-plan.csv), [equipamentos/acessos](REQUIREMENTS.md), [validação real do software](VALIDATION.md).

## O que cada resultado significa

| Camada | Evidência necessária | O que não demonstra |
| --- | --- | --- |
| NFC / ISO-DEP | Trace do leitor ou ferramenta oficial, contexto físico e identificação dos aparelhos | Autenticação EMV, autorização ou liquidação |
| Reconhecimento/processamento EMV | Trace de kernel/caso oficial, versão, interface contactless e resultado do plano do provedor | Aceite pelo host ou movimento financeiro |
| Autorização pelo host | Pedido/resposta correlacionados no processador correto; referência não secreta, data e ambiente | Liquidação; mensagem isolada “aprovado” não basta |
| Liquidação financeira real | Reconciliação independente no ambiente real, vínculo com operação legítima autorizada | Não é comprovada por sandbox/homologação ou pela tela do terminal |

Decisão offline do kernel pertence ao resultado EMV. Não é classificada automaticamente como autorização do host. Reconciliação de teste é resultado de homologação, não liquidação financeira real.

## Limite do NFC Lab perante maquininhas

O serviço existente anuncia somente `F04E46434C414201`, categoria `other`. Implementa SELECT e GET DATA fictícios. **Não implementa PPSE, kernel EMV, pagamento, credenciais bancárias ou criptogramas.** Não há alteração para imitar um cartão de banco.

Terminais de pagamento normalmente seguem sua seleção de aplicações de pagamento. Sem modo de desenvolvimento que selecione o AID NFC Lab, eles podem não rotear nenhum APDU para nosso serviço. Zero APDUs no Android não significa ausência de campo RF, incompatibilidade comprovada ou rejeição de autenticação. APDUs destinadas a AIDs não registrados não são capturadas pelo aplicativo. Não há interceptação passiva de cartão legítimo e maquininha.

O controle físico inicial usa leitor ISO-DEP com seleção explícita desse AID (PC/SC, segundo Android ou SDK oficial autorizado). Já o ensaio EMV usa credenciais e ferramentas oficiais, com logs obtidos no ambiente do provedor. São trilhas separadas, vinculadas apenas por IDs de experimento e evidências.

## Status e rastreabilidade

Resultados possíveis incluem `UNKNOWN`, `NOT_APPLICABLE` e `NOT_REQUESTED`; não se força uma conclusão. Uma referência de arquivo digitada no APK não prova que ele existe. O coletor local verifica existência e SHA256 dos artefatos anexados; mesmo hashes corretos resultam apenas em `ARTIFACTS_PRESENT_MANUAL_REVIEW_REQUIRED`. Autenticidade, execução física, ambiente e interpretação exigem revisão presencial.

Não armazenar PAN completo, trilhas, PIN/PIN block, CVV, chaves ou outros segredos nos formulários/relatórios. Traces EMV oficiais podem conter dados sensíveis: mantenha originais no repositório autorizado do provedor; anexe versões redigidas e registre a referência. Hash de versão redigida e de original, se permitido, são distintos. Não compartilhe logs brutos de produção.
