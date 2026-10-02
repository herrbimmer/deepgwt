# Roteiro do primeiro carro

O OPEN GW (`br.com.opengw`) é um app novo e **não conversa com nenhuma versão anterior**: não lê as
configurações dela, não a desativa, não a remove. O que só o carro confirma está na seção 3.

## 0. Antes

1. Se o carro tem uma versão anterior instalada, **remover antes**, pelo instalador dela. Duas
   versões não coexistem: os dois Shizuku declaram a mesma permissão e o segundo não instala
   (o instalador mostra `INSTALL_FAILED_DUPLICATE_PERMISSION` e avisa).
2. Compilar o APK e o instalador (`./sincronizar-apk.sh` gera o `opengw.apk` e o instalador o leva
   embutido; `shizuku.apk` e `shizuku-starter` estão em `instalador/src-tauri/resources/`, fonte e
   licença em `third_party/open-gw-acesso/`).
3. Um carro só, de preferência o do dono. Anotar a versão do carro (Informações → versão).
4. Wi-Fi do carro (o instalador só fala com `192.168.33.x`).

## 1. Instalar (o instalador faz tudo sozinho)

| Passo | O que acontece | Conferir |
|---|---|---|
| Shizuku | instala `br.com.opengw.shizuku` ("OPEN GW Acesso") e sobe o servidor pelo telnet de root | `pidof shizuku_server` existe; o app "OPEN GW Acesso" abre e diz "em execução" |
| App | instala `br.com.opengw` | ícone "OPEN GW" na tela inicial |
| Primeira partida | concede a permissão do Shizuku, o acesso a notificações, a acessibilidade e a sobreposição | tudo ligado sem pedir nada à mão |

O app começa limpo: tema do painel, barra, brilho, luz, perfis e atalhos se configuram do zero.

## 2. Voltar atrás

Desinstalar pelo próprio instalador (Remover): para o app, devolve os coringas do volante à função de
fábrica, remove o app e o Shizuku.

## 3. O que só o carro confirma (marcar na ordem)

1. **Motor** (canal com a central): as 1014 chaves chegam, os valores variam, reconecta sozinho se a central reinicia.
2. **Painel** (tela 3 real): cartão 0/1/3, temas, avisos, display 1, `ClusterMsgData`, teclas do volante no menu.
3. **Barra inferior**: as 4 fatias, menus, gaveta, painel expandido, volume, mídia do AA e do CarPlay.
4. **Lançador**: Android Auto e CarPlay nos displays 0 e 3, guardas, vigias, recuperação de imagem do CarPlay,
   "tela inicial 3D".
5. **Luz ambiente** (BLE/fita) e **brilho automático** (nível, alarmes de nascer/pôr do sol).
6. **Volante**: teclas, coringas, mãos livres reagindo a "apertou" (o serviço de teclas só entrega "soltou"? confirmar).
7. **Rede** (aba): ligar o controle de dados móveis e uma regra, conferir "4G cortado agora (...)" e o consumo; ligar o
   roteador do hotspot com o carro no Wi-Fi do celular/Starlink e conferir "Roteando pelo Wi-Fi externo"; firewall liberado
   (o instalador no PC alcança a porta 8766 pelo hotspot).
8. **Leitor VHAL** (Diagnóstico → propriedades da central) e **DCM** (aparelhos conectados no lançador).
9. **Interfaces da fabricante**: métodos `oneway` novos (IVehicle/IDvr) sem travar.
10. **Reinício do carro**: o Shizuku sobe sozinho depois de um ciclo de energia (rede de segurança da partida fria).

## 4. Depois de tudo conferido

Anotar o que passou; o que falhar volta para a lista de defeitos, com o log.
