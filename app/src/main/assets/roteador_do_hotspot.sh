#!/system/bin/sh
# Roteador do hotspot do OPEN GW (daemon).
#
# Faz os aparelhos ligados ao hotspot do carro (wlan2) navegarem pelo Wi-Fi externo (wlan0)
# enquanto ele tiver internet; sem internet, tira a regra de roteamento e o tráfego volta para o
# 4G da fabricante pelo tethering normal do Android.
#
# Uso: roteador_do_hotspot.sh [start|stop|status]
#   start   (padrão) laço de 5 s; termina daemons antigos antes de entrar
#   stop    termina o daemon, desfaz tudo e imprime STOPPED (ou STOP_FAILED, saída 1)
#   status  imprime ALIVE|<pid> (saída 0) ou DEAD (saída 1)
#
# POSIX: roda no sh do Android (mksh), no dash e no sh do macOS. Precisa de root para ip,
# iptables e ip_forward; sem root registra o erro e o estado fica em 4G.
#
# Instalado em /data/local/tmp/hotrouter.sh pelo app (os nomes dos arquivos do aparelho são
# contrato). Tudo abaixo pode ser trocado por variável de ambiente, para teste.

BASE="${HOTROUTER_BASE:-/data/local/tmp}"
WLAN_IF="${HOTROUTER_WLAN_IF:-wlan0}"
WLAN_TABLE="$WLAN_IF"
PRIO=17999
HOST1="${HOTROUTER_HOST1:-8.8.8.8}"
HOST2="${HOTROUTER_HOST2:-1.1.1.1}"
INTERVAL="${HOTROUTER_INTERVAL:-5}"
IPFWD="${HOTROUTER_IPFORWARD:-/proc/sys/net/ipv4/ip_forward}"
ONCE="${HOTROUTER_ONCE:-0}"
PROC="${HOTROUTER_PROC:-/proc}"
SYSNET="${HOTROUTER_SYSNET:-/sys/class/net}"

PID_FILE="$BASE/hotrouter.pid"
STATE_FILE="$BASE/hotrouter.state"
FWD_ORIG="$BASE/hotrouter.ip_forward.original"
LOG_FILE="$BASE/hotrouter.log"
LOG_MAX=400

# Caminho completo deste script: é assim que se reconhece o daemon (a linha de comando dele tem
# este caminho seguido de " start").
SELF="$0"
case "$SELF" in
  /*) ;;
  *) SELF="$PWD/${SELF#./}" ;;
esac

# ---------------------------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------------------------

agora() {
  date +%s
}

# log NIVEL MENSAGEM
log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') $1 $2" >> "$LOG_FILE" 2>/dev/null
}

# Erro que se repete a cada iteração só vai para o log quando muda.
ULTIMO_ERRO=""
log_erro_unico() {
  if [ "$1" != "$ULTIMO_ERRO" ]; then
    ULTIMO_ERRO="$1"
    log ERRO "$1"
  fi
}

aparar_log() {
  [ -f "$LOG_FILE" ] || return 0
  n=$(wc -l 2>/dev/null < "$LOG_FILE" | tr -d ' ')
  if [ "${n:-0}" -gt "$LOG_MAX" ]; then
    tail -n "$LOG_MAX" "$LOG_FILE" > "$LOG_FILE.tmp" 2>/dev/null && mv "$LOG_FILE.tmp" "$LOG_FILE"
  fi
}

# gravar_estado MODO: "MODO|época", por arquivo temporário para o app nunca ler pela metade.
gravar_estado() {
  echo "$1|$(agora)" > "$STATE_FILE.tmp" 2>/dev/null && mv "$STATE_FILE.tmp" "$STATE_FILE" 2>/dev/null
}

resolver_hotspot() {
  if [ -n "$HOTROUTER_HOTSPOT_IF" ]; then
    echo "$HOTROUTER_HOTSPOT_IF"
    return 0
  fi
  h=$(getprop sys.wifi.softap_interface_name 2>/dev/null)
  if [ -n "$h" ]; then
    echo "$h"
  else
    echo wlan2
  fi
}

# O hotspot está no ar quando a interface dele tem portadora.
hotspot_no_ar() {
  v=$(tr -d ' \t\r\n' 2>/dev/null < "$SYSNET/$1/carrier")
  [ "$v" = 1 ]
}

ler_ipfwd() {
  tr -d ' \t\r\n' 2>/dev/null < "$IPFWD"
}

# ---------------------------------------------------------------------------------------------
# Reconhecer e terminar o daemon
# ---------------------------------------------------------------------------------------------

# eh_daemon PID: o processo existe, não é este shell, e a linha de comando dele tem o caminho
# completo deste script seguido de " start". (Um processo qualquer com " start" na linha, como
# "am start", não é o daemon.)
eh_daemon() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
  esac
  [ "$1" -gt 1 ] || return 1
  [ "$1" != "$$" ] || return 1
  [ -r "$PROC/$1/cmdline" ] || return 1
  tr '\0' ' ' 2>/dev/null < "$PROC/$1/cmdline" | grep -qF -- " $SELF start"
}

# Termina os daemons (pelo pid gravado e por varredura): TERM, espera até 3 s, depois KILL.
terminar_daemons() {
  pids=""
  if [ -f "$PID_FILE" ]; then
    p=""
    read -r p 2>/dev/null < "$PID_FILE"
    eh_daemon "$p" && pids="$p"
  fi
  for d in "$PROC"/[0-9]*; do
    [ -d "$d" ] || continue
    p=${d##*/}
    eh_daemon "$p" && pids="$pids $p"
  done
  if [ -z "$pids" ]; then
    rm -f "$PID_FILE"
    return 0
  fi
  log INFO "terminando daemon(s):$pids"
  # shellcheck disable=SC2086
  kill -TERM $pids 2>/dev/null
  i=0
  vivos="$pids"
  while [ "$i" -lt 30 ]; do
    vivos=""
    for p in $pids; do
      kill -0 "$p" 2>/dev/null && vivos="$vivos $p"
    done
    [ -z "$vivos" ] && break
    sleep 0.1
    i=$((i + 1))
  done
  if [ -n "$vivos" ]; then
    log AVISO "não saiu com TERM; KILL em:$vivos"
    # shellcheck disable=SC2086
    kill -KILL $vivos 2>/dev/null
  fi
  rm -f "$PID_FILE"
}

# ---------------------------------------------------------------------------------------------
# Regra de roteamento (ip rule)
# ---------------------------------------------------------------------------------------------

# contar_regras HOTSPOT: quantas cópias de "from all iif HOTSPOT lookup wlan0 priority 17999".
contar_regras() {
  ip rule show 2>/dev/null | awk -v p="$PRIO:" -v i="$1" -v t="$WLAN_TABLE" '
    $1 == p {
      a = 0; b = 0
      for (k = 2; k < NF; k++) {
        if ($k == "iif" && $(k + 1) == i) a = 1
        if ($k == "lookup" && $(k + 1) == t) b = 1
      }
      if (a && b) n++
    }
    END { print n + 0 }'
}

# Remove todas as cópias.
remover_regras() {
  k=0
  n=$(contar_regras "$1")
  while [ "${n:-0}" -gt 0 ] && [ "$k" -lt 20 ]; do
    ip rule del from all iif "$1" lookup "$WLAN_TABLE" priority "$PRIO" >/dev/null 2>&1 || break
    MUDOU=1
    k=$((k + 1))
    n=$(contar_regras "$1")
  done
}

# Garante exatamente uma: só acrescenta se faltar, só remove se houver duplicatas.
garantir_regra() {
  n=$(contar_regras "$1")
  if [ "${n:-0}" -eq 0 ]; then
    ip rule add from all iif "$1" lookup "$WLAN_TABLE" priority "$PRIO" >/dev/null 2>&1 || return 1
    MUDOU=1
    return 0
  fi
  k=0
  while [ "$n" -gt 1 ] && [ "$k" -lt 20 ]; do
    ip rule del from all iif "$1" lookup "$WLAN_TABLE" priority "$PRIO" >/dev/null 2>&1 || return 1
    MUDOU=1
    k=$((k + 1))
    n=$(contar_regras "$1")
  done
  return 0
}

# ---------------------------------------------------------------------------------------------
# Regras de iptables (cadeias do netd)
# ---------------------------------------------------------------------------------------------

# definir_regra N HOTSPOT: preenche RT (tabela), RC (cadeia), RP (posição) e RS (regra).
definir_regra() {
  w="$WLAN_IF"
  h="$2"
  case "$1" in
    1) RT="-t nat"; RC=tetherctrl_nat_POSTROUTING; RP=1; RS="-o $w -j MASQUERADE" ;;
    2) RT=""; RC=tetherctrl_FORWARD; RP=1
       RS="-i $w -o $h -m state --state RELATED,ESTABLISHED -g tetherctrl_counters" ;;
    3) RT=""; RC=tetherctrl_FORWARD; RP=2; RS="-i $h -o $w -m state --state INVALID -j DROP" ;;
    4) RT=""; RC=tetherctrl_FORWARD; RP=3; RS="-i $h -o $w -g tetherctrl_counters" ;;
    5) RT=""; RC=tetherctrl_counters; RP=1; RS="-i $h -o $w -j RETURN" ;;
    6) RT=""; RC=tetherctrl_counters; RP=2; RS="-i $w -o $h -j RETURN" ;;
  esac
}

# As variáveis RT e RS levam várias palavras; ficam sem aspas de propósito.
regra_existe() {
  # shellcheck disable=SC2086
  iptables $RT -C "$RC" $RS >/dev/null 2>&1
}

inserir_regra() {
  # shellcheck disable=SC2086
  iptables $RT -I "$RC" "$RP" $RS >/dev/null 2>&1
}

apagar_regra() {
  # shellcheck disable=SC2086
  iptables $RT -D "$RC" $RS >/dev/null 2>&1
}

# As três cadeias que o netd cria quando o tethering está de pé.
cadeias_do_netd_existem() {
  iptables -t nat -S tetherctrl_nat_POSTROUTING >/dev/null 2>&1 || return 1
  iptables -S tetherctrl_FORWARD >/dev/null 2>&1 || return 1
  iptables -S tetherctrl_counters >/dev/null 2>&1 || return 1
  return 0
}

# Insere as seis regras que faltam, cada uma na posição indicada. Falha se alguma não entrou.
garantir_regras_do_iptables() {
  rc=0
  for n in 1 2 3 4 5 6; do
    definir_regra "$n" "$1"
    if ! regra_existe; then
      if inserir_regra; then
        MUDOU=1
      else
        log_erro_unico "iptables: não consegui inserir a regra $n em $RC"
        rc=1
      fi
    fi
  done
  return $rc
}

# Remove todas as cópias das seis regras.
remover_regras_do_iptables() {
  for n in 1 2 3 4 5 6; do
    definir_regra "$n" "$1"
    k=0
    while regra_existe && [ "$k" -lt 20 ]; do
      apagar_regra || break
      k=$((k + 1))
    done
  done
}

# Sobrou alguma das seis?
sobrou_regra_do_iptables() {
  for n in 1 2 3 4 5 6; do
    definir_regra "$n" "$1"
    regra_existe && return 0
  done
  return 1
}

# ---------------------------------------------------------------------------------------------
# ip_forward
# ---------------------------------------------------------------------------------------------

garantir_ipfwd() {
  [ "$(ler_ipfwd)" = 1 ] && return 0
  echo 1 > "$IPFWD" 2>/dev/null
  [ "$(ler_ipfwd)" = 1 ]
}

# Volta ao valor guardado. Com o hotspot no ar não baixa para 0: o netd só liga o
# encaminhamento ao subir o tethering, e um hotspot ligado depois do daemon ficaria sem internet.
restaurar_ipfwd() {
  [ -f "$FWD_ORIG" ] || return 0
  orig=$(tr -d ' \t\r\n' 2>/dev/null < "$FWD_ORIG")
  case "$orig" in
    0|1) ;;
    *) orig="" ;;
  esac
  if [ "$orig" = 0 ] && hotspot_no_ar "$1"; then
    orig=1
    log INFO "hotspot no ar: ip_forward fica em 1"
  fi
  if [ -n "$orig" ]; then
    echo "$orig" > "$IPFWD" 2>/dev/null || log ERRO "não consegui restaurar o ip_forward"
  fi
  rm -f "$FWD_ORIG"
}

# ---------------------------------------------------------------------------------------------
# Uplink e estado desejado
# ---------------------------------------------------------------------------------------------

# O Wi-Fi externo tem internet: as duas interfaces existem, a tabela wlan0 tem rota padrão e um
# ping pela wlan0 chega a um dos dois hosts.
uplink_com_internet() {
  ip link show "$WLAN_IF" >/dev/null 2>&1 || return 1
  ip link show "$1" >/dev/null 2>&1 || return 1
  ip route show table "$WLAN_TABLE" 2>/dev/null | grep -q '^default' || return 1
  ping -I "$WLAN_IF" -c 1 -W 2 "$HOST1" >/dev/null 2>&1 && return 0
  ping -I "$WLAN_IF" -c 1 -W 2 "$HOST2" >/dev/null 2>&1 && return 0
  return 1
}

esvaziar_cache() {
  ip route flush cache >/dev/null 2>&1
}

# Com internet: ip_forward, uma regra de roteamento e as seis regras do iptables. Se algo não
# deu (cadeias do netd ausentes, inserção falhou), desfaz o roteamento e devolve 1.
aplicar_com_internet() {
  FALHA=0
  garantir_ipfwd || { log_erro_unico "ip_forward não ficou em 1"; FALHA=1; }
  garantir_regra "$1" || { log_erro_unico "ip rule: não consegui garantir a regra de roteamento"; FALHA=1; }
  if cadeias_do_netd_existem; then
    garantir_regras_do_iptables "$1" || FALHA=1
  else
    log_erro_unico "cadeias do netd ausentes (tethering fora do ar?)"
    FALHA=1
  fi
  if [ "$FALHA" = 1 ]; then
    remover_regras "$1"
    [ "$MUDOU" = 1 ] && esvaziar_cache
    return 1
  fi
  [ "$MUDOU" = 1 ] && esvaziar_cache
  return 0
}

# Uma iteração do laço.
iteracao() {
  MUDOU=0
  h=$(resolver_hotspot)
  if [ -n "$HOTSPOT_ATUAL" ] && [ "$h" != "$HOTSPOT_ATUAL" ]; then
    log AVISO "interface do hotspot mudou: $HOTSPOT_ATUAL -> $h"
    remover_regras "$HOTSPOT_ATUAL"
    remover_regras_do_iptables "$HOTSPOT_ATUAL"
  fi
  HOTSPOT_ATUAL="$h"

  modo=4G
  if uplink_com_internet "$h"; then
    if aplicar_com_internet "$h"; then
      modo=WLAN
      ULTIMO_ERRO=""
    fi
  else
    remover_regras "$h"
    [ "$MUDOU" = 1 ] && esvaziar_cache
  fi
  gravar_estado "$modo"

  if [ "$modo" != "$MODO_ANTERIOR" ]; then
    log INFO "modo: ${MODO_ANTERIOR:-nenhum} -> $modo (hotspot $h, uplink $WLAN_IF)"
    MODO_ANTERIOR="$modo"
  fi
  if [ $((ITERACAO % 24)) -eq 23 ]; then
    log INFO "batimento: modo $modo"
  fi
}

# ---------------------------------------------------------------------------------------------
# Limpeza
# ---------------------------------------------------------------------------------------------

# Remove tudo o que o daemon pôs, restaura o ip_forward e grava OFF.
limpar_tudo() {
  remover_regras "$1"
  remover_regras_do_iptables "$1"
  restaurar_ipfwd "$1"
  esvaziar_cache
  gravar_estado OFF
  log INFO "limpeza concluída"
}

LIMPO=0
# Chamada pelo trap de EXIT.
# shellcheck disable=SC2329
sair_limpando() {
  [ "$LIMPO" = 1 ] && return 0
  LIMPO=1
  [ -n "$DORMINDO" ] && kill "$DORMINDO" 2>/dev/null
  alvo="${HOTSPOT_ATUAL:-$(resolver_hotspot)}"
  log INFO "saindo: limpando"
  limpar_tudo "$alvo"
  rm -f "$PID_FILE"
}

# ---------------------------------------------------------------------------------------------
# Subcomandos
# ---------------------------------------------------------------------------------------------

cmd_status() {
  p=""
  [ -f "$PID_FILE" ] && read -r p 2>/dev/null < "$PID_FILE"
  if eh_daemon "$p"; then
    echo "ALIVE|$p"
    exit 0
  fi
  rm -f "$PID_FILE"
  echo DEAD
  exit 1
}

cmd_stop() {
  MUDOU=0
  terminar_daemons
  h=$(resolver_hotspot)
  limpar_tudo "$h"
  rm -f "$PID_FILE"
  sobrou=0
  [ "$(contar_regras "$h")" -gt 0 ] && sobrou=1
  sobrou_regra_do_iptables "$h" && sobrou=1
  [ -f "$FWD_ORIG" ] && sobrou=1
  if [ "$sobrou" = 1 ]; then
    log ERRO "stop: sobrou regra ou arquivo do ip_forward"
    echo STOP_FAILED
    exit 1
  fi
  echo STOPPED
  exit 0
}

cmd_start() {
  terminar_daemons
  echo "$$" > "$PID_FILE"

  # Valor de ip_forward de antes de nós, guardado uma vez só (um daemon morto por KILL deixa o
  # arquivo, e é ele que vale).
  if [ ! -f "$FWD_ORIG" ]; then
    v=$(ler_ipfwd)
    case "$v" in
      0|1) echo "$v" > "$FWD_ORIG" ;;
      *)
        log ERRO "ip_forward ilegível ('$v'); não inicio"
        rm -f "$PID_FILE"
        exit 1
        ;;
    esac
  fi
  echo 1 > "$IPFWD" 2>/dev/null || log ERRO "não consegui pôr 1 em $IPFWD (sem root?)"
  log INFO "daemon iniciado (pid $$, uplink $WLAN_IF, intervalo ${INTERVAL}s)"

  if [ "$ONCE" != 1 ]; then
    trap 'exit 0' INT TERM
    trap 'sair_limpando' EXIT
  fi

  ITERACAO=0
  MODO_ANTERIOR=""
  HOTSPOT_ATUAL=""
  while :; do
    aparar_log
    iteracao
    ITERACAO=$((ITERACAO + 1))
    [ "$ONCE" = 1 ] && break
    # O sleep em segundo plano com wait deixa o TERM agir na hora, sem esperar o intervalo.
    sleep "$INTERVAL" &
    DORMINDO=$!
    wait "$DORMINDO" 2>/dev/null
  done
  # Só no modo de teste: uma iteração e sai, sem desfazer nada.
  rm -f "$PID_FILE"
  exit 0
}

case "${1:-start}" in
  start) cmd_start ;;
  stop) cmd_stop ;;
  status) cmd_status ;;
  *)
    echo "uso: $0 [start|stop|status]"
    exit 1
    ;;
esac
