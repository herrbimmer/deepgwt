package br.com.opengw.ui.telas

import br.com.opengw.rede.ModoDoRoteador
import br.com.opengw.rede.MotivoDoCorte
import kotlin.math.roundToInt

/** Textos e contas da aba "Rede", sem Android, para teste. */
internal object RegrasDaRede {

    const val TETO_MAXIMO_MB = 20_480
    const val PASSO_DO_TETO_MB = 256

    /** Quantos passos intermediários o controle deslizante do teto tem (extremos não contam). */
    const val PASSOS_DO_TETO = TETO_MAXIMO_MB / PASSO_DO_TETO_MB - 1

    fun textoDoCorte(controleLigado: Boolean, motivo: MotivoDoCorte?): String = when {
        !controleLigado -> "Controle desligado: o 4G fica como a fábrica deixou."
        motivo == null -> "4G liberado agora."
        else -> "4G cortado agora (${motivo.rotulo})."
    }

    fun textoDoUso(usoMb: Long, tetoMb: Int, etiquetaDoCiclo: String): String =
        "Uso no ciclo $etiquetaDoCiclo: ${formatarMb(usoMb)} de ${formatarMb(tetoMb.toLong())}."

    /** 2048 MB vira "2,0 GB"; abaixo de 1 GB fica em MB. */
    fun formatarMb(mb: Long): String =
        if (mb < 1024) "$mb MB" else String.format(java.util.Locale("pt", "BR"), "%.1f GB", mb / 1024.0)

    fun teto(valor: Float): Int =
        ((valor / PASSO_DO_TETO_MB).roundToInt() * PASSO_DO_TETO_MB).coerceIn(0, TETO_MAXIMO_MB)

    fun textoDoRoteador(modo: ModoDoRoteador): String = when (modo) {
        ModoDoRoteador.DESLIGADO -> "Desligado."
        ModoDoRoteador.INICIANDO -> "Iniciando…"
        ModoDoRoteador.WLAN -> "Roteando pelo Wi-Fi externo."
        ModoDoRoteador.QUATRO_G -> "Sem internet no Wi-Fi externo: usando o 4G da fabricante."
        ModoDoRoteador.ERRO -> "Com erro: o roteador não responde. Desligue e ligue de novo."
    }

    fun textoDoHotspot(noAr: Boolean): String =
        if (noAr) "Hotspot do carro: no ar." else "Hotspot do carro: desligado."
}
