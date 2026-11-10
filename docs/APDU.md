# Protocolo NFC Lab v1

AID proprietário de laboratório: **F04E46434C414201** (8 bytes, categoria Android `other`). Sem AIDs de pagamento. Todos os perfis retornam texto explícito `FICTITIOUS`.

APDU curto: CLA INS P1 P2 [Lc Data] [Le]. Lc: 1–255; Le: 1–256, com byte 00 representando 256. Cabeçalho de 4 bytes sem corpo também é aceito. Formato estendido é rejeitado. Cada sessão lógica começa não selecionada.

| Comando | Hexadecimal | Resposta |
| --- | --- | --- |
| SELECT AID | `00 A4 04 00 08 F0 4E 46 43 4C 41 42 01 00` | `90 00` |
| GET DATA | `00 CA 00 00 00` | ASCII `NFC LAB \| FICTITIOUS \| <perfil>` + `90 00` |

SELECT exige P1=04 e P2=00; GET DATA exige P1=P2=00 e nenhum Data. GET DATA só funciona após SELECT válido. O SELECT sem Le também é aceito. O serviço retorna `6985` se não houver sessão HCE armada. A desativação do link ou troca de sessão limpa a seleção. Um SELECT inválido também a limpa. O perfil da sessão não muda quando outro perfil é escolhido na UI.

| Status | Significado |
| --- | --- |
| 9000 | Sucesso |
| 6700 | Comprimento/codificação de APDU inválido |
| 6985 | Condições não satisfeitas: sem seleção ou sessão desarmada |
| 6A82 | AID inexistente |
| 6A86 | P1/P2 incorretos |
| 6D00 | Instrução não suportada |
| 6E00 | Classe não suportada |
| 6Cxx | Le insuficiente; xx é o comprimento esperado |

Respostas são dados seguidos por SW1/SW2. Para LAB-ALPHA, GET DATA retorna `4E4643204C4142207C20464943544954494F5553207C204C41422D414C5048419000`.

No Reader, comandos personalizados são limitados ao fluxo após SELECT bem-sucedido do AID NFC Lab. Sem extração de segredos, tentativa de autenticação de terceiros, EMV ou transações financeiras.
