package br.com.deepgwt.tela3d.cartoes

/** Quando o cartão de navegação mostra a manobra e qual vetor a representa (AP3.11). */
object RegrasDaNavegacao {
    private val ESTADOS_SEM_MANOBRA = setOf("", "idle", "unavailable")
    private val ESTADOS_COM_VETOR = setOf("turn_right", "turn_left", "straight", "uturn", "roundabout", "fork", "merge")

    /** Existe o vetor `ic_tbt_<estado>` para o estado? */
    fun temVetor(estado: String): Boolean = estado in ESTADOS_COM_VETOR

    fun mostraManobra(estado: String, glifo: String): Boolean {
        if (estado in ESTADOS_SEM_MANOBRA) return false
        return estado == "destination" || estado == "exit" || temVetor(estado) || glifo.isNotEmpty()
    }
}
