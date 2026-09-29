# Teste da câmera da Portaria com QRs difíceis

Pendência D-123. O scanner da Portaria usa o `@zxing/library` 0.23. Esse leitor não acha os padrões de localização de parte dos QRs corretos gerados pelo backend. Este teste mede o que acontece com a câmera de verdade, antes de decidir o que fazer (girar o quadro, `BarcodeDetector` ou só acompanhar).

## Números medidos (sem câmera, na imagem do PNG)

3.000 códigos aleatórios do alfabeto do convite, com os PNGs gerados por `QrCodes.png`, o mesmo código do backend:

| Leitor | Não lidos |
|---|---|
| zxing-js 0.23 (o da Portaria), com e sem TRY_HARDER | 72 (2,4%) |
| zxing-js 0.23 com a imagem girada 90°, 180° ou 270° | 1 dos 72 |
| ZXing Java 3.5.4 (o do backend) | 12 (0,4%), todos entre os 72 |

## Arquivos

Todos são PNGs de 512 px, iguais aos da API. O nome é `grupo-código.png`.

- **`a01` a `a12`:** não lidos pelo zxing-js nem pelo ZXing Java.
- **`b01` a `b08`:** não lidos pelo zxing-js, lidos pelo ZXing Java.
- **`c01` a `c04`:** controle, lidos pelos dois. Se um controle não for lido, o problema é o procedimento (brilho, distância, foco), não o QR.

Os códigos não existem no banco, então a Portaria responde "Código inválido". **"Código inválido" conta como leitura**: a câmera leu o QR e a validação rodou.

## Procedimento

No servidor de demonstração, por HTTPS (a câmera só abre em contexto seguro, D-093):

1. **Celular da Portaria:** entre com um usuário GATE e toque em [ESCANEAR QR CODE].
2. **Outro celular:** abra cada PNG em tela cheia, com o brilho no máximo e sem modo noturno.
3. **Em pé:** aponte a câmera da Portaria para o QR em pé, a uns 20 cm, por até 5 segundos.
4. **Girado:** se não ler, gire o celular do QR 90° e tente por mais 5 segundos.
5. **Anote:** "em pé", "girado" ou "não leu". Depois de cada leitura, toque em [NOVA VALIDAÇÃO].

Faça primeiro os quatro controles (`c01` a `c04`).

## Resultado

Celular da Portaria (modelo e navegador): ______________________

| Arquivo | Leu em pé | Leu girado | Não leu | Observação |
|---|---|---|---|---|
| c01-00803J52QK | | | | |
| c02-00898YQZMJ | | | | |
| c03-00H2227VZY | | | | |
| c04-01XX0395FA | | | | |
| a01-4BZA94Z7YW | | | | |
| a02-4YP2GYQQZT | | | | |
| a03-68M9AZ3JXP | | | | |
| a04-8C946ZRGES | | | | |
| a05-BJAVGWM3GP | | | | |
| a06-CSYVYZ47G5 | | | | |
| a07-FGNN0RPTEK | | | | |
| a08-JSWZ9WAQEM | | | | |
| a09-QZ1YHSVCNC | | | | |
| a10-TJ1VZDYPZS | | | | |
| a11-X2BV60CXNE | | | | |
| a12-Z4YCPEJ4YG | | | | |
| b01-170VP1QJRH | | | | |
| b02-1Q1WZD5QNV | | | | |
| b03-1XSZM7Q30H | | | | |
| b04-32XQJ1MSYS | | | | |
| b05-3K07E8H9GX | | | | |
| b06-4KQ9MS3NR8 | | | | |
| b07-5QQVE10XHT | | | | |
| b08-66WASZZZMQ | | | | |
