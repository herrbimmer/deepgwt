// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A rede do carro.
//
// Alpha 9.4: o instalador só fala com o carro pelo Wi-Fi do próprio carro — o hotspot da central
// multimídia, na faixa 192.168.33.x. Até a 9.3 dava também para alcançar o app do carro pela rede
// de casa (carro como cliente do mesmo Wi-Fi) e digitar um IP de qualquer faixa; isso saiu.
//
// A trava de verdade está no Rust (`exigir_wifi_do_carro` / `exigir_ip_do_carro` em lib.rs), que
// recusa toda chamada com o computador fora do hotspot. Este arquivo só evita que a interface
// aceite e guarde um endereço que o backend vai recusar, e dá a mesma frase às telas.

export const PREFIXO_DO_CARRO = '192.168.33.'

/** O IP é da faixa do hotspot do carro (192.168.33.1–254)? */
export function ehIpDoCarro(ip: string | null | undefined): boolean {
  const m = /^192\.168\.33\.(\d{1,3})$/.exec((ip ?? '').trim())
  if (!m) return false
  const host = Number(m[1])
  return host >= 1 && host <= 254
}

export const AVISO_IP_FORA_DO_CARRO =
  'Esse endereço não é da rede do carro. No Wi-Fi do carro a central fica em 192.168.33.x — use "Detectar automaticamente".'
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
