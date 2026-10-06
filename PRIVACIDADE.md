# DeepGWT — Política de Privacidade

Versão 1.0 — vigência 2026-10-02
Controlador: Miguel Vespermann Araújo. Encarregado/contato (LGPD): [E-MAIL DE CONTATO — preencher antes de distribuir].

> **Rascunho técnico, não é parecer jurídico.** Descreve o comportamento do código na data acima. Revise com advogado e **atualize sempre que o app passar a coletar ou enviar algo novo**.

## 1. Resumo
- O DeepGWT funciona **dentro do seu carro**. **Não há conta, cadastro, anúncio, rastreador de uso nem envio de telemetria para servidores do DeepGWT.**
- Os dados do veículo, a localização, o áudio e a licença ficam **no aparelho** (central do carro, e no seu computador/celular quando você usa o instalador).
- Alguns recursos se conectam a **serviços de terceiros** (item 4), e só quando você os usa.

## 2. Dados que o app trata, e para quê
| Dado | Origem | Finalidade | Sai do aparelho? |
|---|---|---|---|
| Sinais do veículo (velocidade, marcha, portas, janelas, luzes, bateria, pneus, consumo etc.) | Sistema do carro | Painéis, temas, launcher 3D, alertas | Não |
| **VIN** (número do chassi) | Sistema do carro | Vincular a chave de licença ao veículo; validar offline | Não. Você informa o VIN/pedido ao Licenciante apenas se quiser comprar uma chave |
| Localização (GPS/rede) | Sistema do carro | Mapa, clima, nome da rua, navegação | Coordenadas vão ao serviço de endereço (item 4) quando você usa o recurso |
| Áudio / microfone (`RECORD_AUDIO`) | Microfone/saída de áudio | Efeitos visuais de áudio e controles de mídia; **não grava nem envia** | Não |
| Bluetooth | Sistema | Pareamento e mídia | Não |
| Metadados de mídia tocando (título, artista) | Apps de música | Exibir na tela | Não |
| Lista de apps instalados e uso de apps | Sistema | Atalhos, dock, limpeza de memória | Não |
| Arquivos e temas que você escolhe | Armazenamento | Instalar temas, planos de fundo | Não |
| Configurações, nível de licença, registros de diagnóstico | Gerados no app | Funcionamento e suporte | Só se **você** exportar e enviar |
| Dados do instalador (IP do carro na rede, arquivos enviados) | Seu computador/celular | Instalar e configurar o app pela rede do carro | Não vão a terceiros; o instalador fala só com o carro |

## 3. Bases legais (LGPD, art. 7º)
Execução do contrato/atendimento ao que você pediu (funcionamento do app); legítimo interesse (segurança e prevenção a fraude de licença, sem perfilamento); consentimento nas permissões do Android (localização, microfone, Bluetooth), que você pode revogar a qualquer momento nas configurações do sistema.

## 4. Serviços de terceiros que o app pode contatar
Ao usar estes recursos, o provedor recebe dados técnicos (como seu endereço IP) e tem sua própria política:

| Serviço | Quando | O que é enviado |
|---|---|---|
| OpenStreetMap / Nominatim (endereço) e tiles de mapa | Mostrar rua/mapa | Coordenadas do pedido |
| Bing (imagem do dia), Wallhaven | Papéis de parede online | Termo de busca/região |
| Polyhaven (iluminação HDR), YouTube (miniaturas e busca), outros endereços de mídia | Cenas 3D, vídeos | Pedido do conteúdo |
| Loja/serviço de apps que você abrir | Instalar apps | Conforme o serviço |

Quando você desativa esses recursos ou fica sem internet, o app continua funcionando com o que tem no aparelho. O Licenciante **não controla** o que esses serviços fazem com os dados.

## 5. Compartilhamento
Não vendemos nem compartilhamos dados pessoais. Podemos divulgar informações se uma lei ou ordem judicial exigir.

## 6. Retenção e exclusão
Os dados ficam no aparelho até você apagar o app ou limpar os dados dele (Configurações do Android → Apps → DeepGWT → Armazenamento). Desinstalar remove os dados do app. Backups do Android, se ativos, podem conter configurações; desative-os se preferir.

## 7. Segurança
Chaves de licença são assinadas e verificadas localmente. A comunicação do instalador com o carro ocorre na rede local do carro. Nenhum sistema é perfeito; proteja a rede Wi-Fi do carro e o acesso ao aparelho.

## 8. Seus direitos (LGPD, art. 18)
Confirmação de tratamento, acesso, correção, anonimização/eliminação, portabilidade, informação sobre compartilhamento e revogação de consentimento. Como o Licenciante não guarda seus dados, a maioria dos direitos se exerce apagando-os no próprio aparelho. Para pedidos sobre dados que você nos enviou (por exemplo, VIN em uma compra), escreva ao contato acima.

## 9. Menores
O Software é para motoristas e proprietários de veículos, não se destina a menores de 18 anos.

## 10. Mudanças
Se esta política mudar, a versão nova aparece no app (Informações) com a data de vigência.

## 11. Contato
[E-MAIL DE CONTATO — preencher]. Autoridade: ANPD (gov.br/anpd).
