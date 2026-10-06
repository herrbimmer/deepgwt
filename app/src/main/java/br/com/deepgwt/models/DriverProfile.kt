// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.models

import kotlinx.serialization.Serializable

/**
 * Um motorista inteiro: como ele deixou o DeepGWT e como ele deixou o carro.
 *
 * COMPATIBILIDADE COM O QUE JÁ ESTÁ GRAVADO NO CARRO
 *
 * A primeira versão deste perfil guardava só `settings` — 16 chaves do veículo escolhidas a dedo
 * — e a memória de banco. Perfis assim já existem em carros que rodam o DeepGWT, gravados como
 * JSON no `deepgwt_prefs`. Por isso `settings` continua aqui, e continua sendo lido: os campos
 * novos nascem com valor padrão, o `Json { ignoreUnknownKeys = true }` do gerente dá conta do
 * resto, e um perfil antigo abre sem nenhuma migração — ele só tem menos coisa dentro.
 *
 * Quem lê deve usar [chavesDoVeiculo], nunca `settings` ou `veiculo` direto, porque é essa
 * função que sabe qual dos dois campos está preenchido nesta versão do arquivo.
 *
 * O QUE CADA MAPA GUARDA
 *
 *   - [deepGwt]:  chave do SharedPreferences -> valor com o tipo embutido (ver ValorDePreferencia).
 *                É o retrato de TUDO que o usuário configurou no app, tirado por varredura e não
 *                por lista (ver EscopoDoPerfil), então cresce sozinho quando o app cresce.
 *   - [veiculo]: chave do barramento -> valor cru, como o carro respondeu.
 *   - [seatMemorySlot]: a memória de banco do próprio carro. A posição do banco não viaja como
 *                dado; o que viaja é QUAL das memórias do carro é a deste motorista.
 */
@Serializable
data class DriverProfile(
    val name: String,
    val seatMemorySlot: Int? = null,
    /** Formato 1 (legado): chaves do veículo. Ainda lido; não é mais escrito. */
    val settings: Map<String, String> = emptyMap(),
    /** Formato 2: preferências do DeepGWT, com tipo. */
    val deepGwt: Map<String, String> = emptyMap(),
    /** Formato 2: chaves de configuração do veículo. */
    val veiculo: Map<String, String> = emptyMap(),
    val criadoEm: Long = 0L,
    val atualizadoEm: Long = 0L
) {
    /** As chaves do carro deste perfil, venha ele do formato novo ou do antigo. */
    fun chavesDoVeiculo(): Map<String, String> = if (veiculo.isNotEmpty()) veiculo else settings

    /** Um perfil do formato 1 não tem nada do DeepGWT dentro — a tela avisa quem o abrir. */
    val ehLegado: Boolean
        get() = deepGwt.isEmpty() && settings.isNotEmpty()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
