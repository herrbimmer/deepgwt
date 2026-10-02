package br.com.opengw.app

import android.content.Context

/**
 * O Shizuku visto pelo serviço principal (módulo 3.9b, `telas-e-servico-servico.md` §1). A
 * implementação do carro é de outro módulo (`br.com.opengw.nucleo.shizuku`); a sala suja liga.
 *
 * Nenhum método bloqueia; os avisos ao [Ouvinte] podem vir de qualquer thread (o serviço os reposta
 * na linha de fundo dele).
 */
interface PartidaDoShizuku {

    enum class Estado { PARADO, SUBINDO, ACEITO }

    interface Ouvinte {
        /** Binder de pé e permissão dada: o serviço segue com a partida. Uma vez por [iniciar]. */
        fun aceito()

        /** O serviço deve se reiniciar (o módulo desistiu de esperar, ou o binder morreu depois de aceito). */
        fun pedirReinicio(motivo: String)

        /** Esta instalação não pode subir o Shizuku sozinha: parar o serviço sem reiniciar. */
        fun desistir(motivo: String)
    }

    val estado: Estado

    /** 1ª partida do serviço no processo. */
    fun iniciar(contexto: Context, ouvinte: Ouvinte)

    /** Pedido repetido com o serviço vivo: conferir se o aceite ainda vale e recuperar se não. */
    fun conferir()

    /** Destruição do serviço: soltar ouvintes e cancelar esperas. Não derruba o servidor. */
    fun encerrar()

    companion object {
        /** Para testes e para o simulador: aceita na hora, na própria chamada de [iniciar]. */
        @JvmField
        val SEMPRE_ACEITO: PartidaDoShizuku = SempreAceito()
    }
}

/** [PartidaDoShizuku.SEMPRE_ACEITO]. Uma instância por processo; guarda só o estado. */
private class SempreAceito : PartidaDoShizuku {

    @Volatile private var atual = PartidaDoShizuku.Estado.PARADO

    override val estado: PartidaDoShizuku.Estado get() = atual

    override fun iniciar(contexto: Context, ouvinte: PartidaDoShizuku.Ouvinte) {
        atual = PartidaDoShizuku.Estado.ACEITO
        ouvinte.aceito()
    }

    override fun conferir() {}

    override fun encerrar() {
        atual = PartidaDoShizuku.Estado.PARADO
    }
}
