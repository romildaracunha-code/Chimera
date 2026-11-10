# Roteiro físico — segundo marco

**Estado na entrega: não executado em dois celulares físicos.** Emulador Android e testes de motor APDU não validam rádio NFC.

## Preparação

- Celular A: Android 8+, NFC e HCE confirmados no Dashboard.
- Celular B: Android 8+, NFC e modo leitor; ISO-DEP interoperável com A.
- Instalar o mesmo APK de debug nos dois. Carregar baterias, desbloquear telas, ligar NFC e localizar as antenas. Remover capas que prejudiquem a aproximação.
- Registrar fabricante, modelo, identificação interna, versão Android/firmware conhecida e SDK/homologação disponível em Devices. Não presumir acesso a terminais proprietários.

## Fluxo válido

1. Em A, selecionar LAB-ALPHA em Emulator e iniciar sessão HCE. Permanecer com tela desbloqueada e NFC Lab em primeiro plano para preferência de roteamento.
2. Em B, abrir Reader, manter GET DATA `00CA000000` e ativar leitor.
3. Aproximar antenas, mantendo contato estável. Não ativar leitor em A.
4. Em B, abrir Inspector. Esperado: sessão `HARDWARE LEITOR`, tecnologias contendo IsoDep, SELECT e GET DATA com `9000`; resposta ASCII `NFC LAB | FICTITIOUS | LAB-ALPHA`.
5. Separar aparelhos. Em A, encerrar sessão HCE e abrir Inspector. Esperado: sessão `HARDWARE HCE` com as mesmas duas trocas. Os relógios independentes podem ter diferenças; durações do leitor incluem transporte, as do HCE medem processamento local.
6. Exportar JSON de ambos e guardar como evidência. Preencher resultado real, data, modelos, versões e erros; só aprovar após observar as trocas nos dispositivos reais.

## Casos negativos

- Desarmar A, reativar leitor B e aproximar. Pode ocorrer `6985`, indisponibilidade de rota ou erro NFC conforme OEM. Não registrar sucesso.
- Armar A novamente. Em B usar `00FF0000` após SELECT. Esperado `6D00` no segundo comando.
- Usar `00CA010000`: esperado `6A86`.
- Usar `00CA000001`: esperado `6C20` para LAB-ALPHA (32 bytes).
- Usar segundo comando SELECT com AID final `02`: `00A4040008F04E46434C41420200`; após SELECT inicial correto, esperado `6A82`.
- Separar aparelhos durante transceive: registrar perda de tag/erro, sem declarar APDUs não observados como sucesso.
- Desligar NFC e retornar ao app: Dashboard deve mostrar desligado e botões físicos indisponíveis.
- Aparelho sem HCE: Dashboard deve diferenciar NFC de HCE e bloquear início de emulação.

## Registro de execução

| Item | Valor a preencher no hardware |
| --- | --- |
| Data/hora | Pendente |
| Aparelho A / Android / firmware | Pendente |
| Aparelho B / Android / firmware | Pendente |
| SELECT / GET DATA observados em A e B | Pendente |
| Casos negativos | Pendente |
| Arquivos JSON/CSV de evidência | Pendente |
| Interferências / distância / alinhamento | Pendente |
| Resultado | Não executado |
