# Ferramentas EMV, cartões e compatibilidade a confirmar

Consulta pública em 2026-11-09. Fontes e respostas HTTP preservadas em `sources/`. Algumas URLs antigas redirecionaram ou retornaram 404; abaixo são usados os endereços atuais que responderam 200. Consulta de catálogo não é teste físico, compra, licença ou confirmação de compatibilidade.

## Fontes oficiais

1. [EMVCo — EMV Contactless Chip](https://www.emvco.com/emv-technologies/emv-contactless-chip/): contexto dos kernels e aprovações contactless.
2. [EMVCo — Level 3 Testing](https://www.emvco.com/emv-technologies/emv-level-3-testing/): framework de L3; casos de teste são fornecidos pelos Participant Systems.
3. [EMVCo — explicação de L1/L2/L3](https://www.emvco.com/news-publications/what-is-level-3-terminal-integration-testing/): L3 valida integração com infraestrutura de aceitação, distinta de L1/L2.
4. [Diretório de ferramentas/provedores L3](https://www.emvco.com/service-providers/?search_bar_keywords=L3) e [processo de qualificação](https://www.emvco.com/processes/l3-test-tool-qualification-process/). Verificar versão, escopo, participante e Letter of Qualification da ferramenta escolhida; não presumir qualificação vigente pelo nome comercial.
5. [Android — HCE](https://developer.android.com/develop/connectivity/nfc/hce): serviço por AID e condições do Android. HCE é capacidade de transporte/emulação, não credencial EMV certificada.

## Candidatos encontrados em catálogo do fabricante

| Ferramenta/kit | Papel declarado pelo fornecedor | Condição antes de usar |
| --- | --- | --- |
| [Fime Eval+](https://www.fime.com/eval/) | Testes de conformidade L2 de terminal/kernel para diferentes sistemas | Confirmar kernel, interfaces de teste e versões. Um terminal comercial fechado pode não expor a interface necessária |
| [Fime BTT — Brand Test Tool](https://www.fime.com/brand-test-tool-btt/) | Testes de integração; página cita processos M-TIP e Visa L3 | Confirmar plano da bandeira/participante, versão, adquirente, host, licença e qualificação aplicável; não é substituto de acesso ao host |
| [Fime Test Cards](https://www.fime.com/fime-test-cards/) | Cartões físicos de aceitação/adquirência; fornecedor cita registro de transações | Solicitar kit **contactless** específico para o participante, região e plano oficial, com casos positivos/negativos documentados. Não assumir que qualquer cartão de teste cobre todos os casos |
| Diretório EMVCo | Opções de ferramentas e provedores qualificados | Avaliar a versão e escopo atuais por combinação de terminal/kernel/plano, junto à adquirente |

São opções para consulta, **não equipamentos declarados compatíveis com as 40 maquininhas**. Sem fabricante/modelo/app/kernel/adquirente e confirmação do provedor, nenhum kit foi selecionado nem validado. Não há fornecimento de cartões, licenças ou acesso dentro deste pacote.

## O que solicitar ao fornecedor/adquirente

- Matriz de modelos/variantes, firmware, aplicativo e kernel suportados; identificadores de aprovação relevantes e validade/escopo.
- SDK e documentação oficiais, tipo de assinatura/whitelist e acesso por função. Acesso a API de venda/impressão pode não dar trace/APDU bruto ou interface L2.
- Identificação do host/tenant de homologação e forma permitida de comprovar que a operação não está em produção.
- Nome/versão do plano L3, participante/bandeira, kit oficial e IDs dos casos, inclusive negativos de autenticação/integridade.
- Ferramenta e versão licenciadas, LoQ/escopo aplicável, componentes de trace e interfaces autorizadas.
- Acesso a logs locais e ao pedido/resposta do processador, correlação não secreta e política de redação/retensão.
- Se inexistente: registrar indisponibilidade, não tentar obter acesso administrativo por outros meios.

## L1, L2, L3 e o leitor do laboratório

- **L1:** aspectos elétricos/RF/protocolo de baixo nível, normalmente bancada/instrumentação própria. Leitor USB e medida de tempo da API não certificam L1.
- **L2:** lógica do kernel e conformidade do protocolo de pagamento. É executada com suite/interface suportadas, não com SELECT/GET DATA do NFC Lab.
- **L3:** integração terminal/aplicação/infraestrutura de aceitação, segundo plano do participante e adquirente. Pode incluir host de teste oficial. Não certifica liquidação financeira real.
- **Leitor PC/SC ISO-DEP:** verifica a troca física do protocolo proprietário com o Android. Não é ferramenta de certificação EMV e não intercepta transações entre maquininha e cartão.

Interposer/analisador contactless autorizado pode ser necessário para visibilidade RF/APDU se o SDK não disponibilizar trace. Confirmar isso com o laboratório/fornecedor; o aplicativo não oferece captura passiva. Não adquirir equipamentos com base em compatibilidade presumida.
