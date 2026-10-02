package br.com.opengw.licenca

import br.com.opengw.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * `GET /licenca` do GwLink: o nível do carro, a tabela inteira de recursos (Alpha 8.0) e, desde a
 * 8.1, a licença — saldo de horas por nível, licenças aplicadas, VIN mascarado e o aviso do
 * momento. `POST /licenca/ativar` aplica um código (instalador e página do celular).
 *
 * Desde a 8.4, `avaliacao` (o teste grátis do Max) e `POST /licenca/avaliacao` para começar.
 *
 * O instalador desenha os cadeados a partir daqui, então uma linha mudada em [Recurso] chega ao
 * computador sem atualizar o instalador. Os campos do formato 1 não mudaram de sentido.
 */
object LicencaRemota {
    const val VERSAO_DO_FORMATO = 2

    fun json(): JSONObject {
        val estado = Portao.estado.value
        val niveis = JSONArray()
        Nivel.entries.forEach {
            niveis.put(JSONObject().put("id", it.id).put("titulo", it.titulo).put("resumo", it.resumo))
        }
        val recursos = JSONArray()
        Recurso.entries.forEach {
            recursos.put(
                JSONObject()
                    .put("id", it.id)
                    .put("titulo", it.titulo)
                    .put("nivel", it.nivel.id)
                    .put("liberado", estado.nivel.alcanca(it.nivel))
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("formato", VERSAO_DO_FORMATO)
            .put("app", BuildConfig.VERSION_NAME)
            .put("nivel", estado.nivel.id)
            .put("nivelTitulo", estado.nivel.titulo)
            .put("origem", estado.origem.id)
            .put("origemDescricao", estado.origem.descricao)
            .put("caiPara", estado.caiPara?.id ?: JSONObject.NULL)
            .put("cobranca", Portao.cobrancaAtiva)
            .put("testeLiberado", Portao.testeLiberado)
            .put("nivelDeTeste", Portao.nivelDeTesteGravado()?.id ?: JSONObject.NULL)
            .put("cobrancaDeTeste", Licencas.cobrancaDeTeste())
            .put(
                "integridade",
                JSONObject()
                    .put("apk", Integridade.estado.id)
                    .put("sentinela", Sentinela.situacao.id)
                    .put("bloqueando", Sentinela.bloqueando)
            )
            .put("niveis", niveis)
            .put("recursos", recursos)
            .put("licenca", licencaJson())
            .put("avaliacao", avaliacaoJson())
    }

    private fun avaliacaoJson(): JSONObject {
        val a = Licencas.estadoDaAvaliacao()
        return JSONObject()
            .put("situacao", a.situacao.id)
            .put("totalSeg", a.totalSeg)
            .put("restanteSeg", a.restanteSeg)
            .put("nivel", Carteira.NIVEL_DA_AVALIACAO.id)
            .put("motivo", a.motivo ?: JSONObject.NULL)
    }

    /**
     * `POST /licenca/avaliacao` (corpo vazio): começa o teste grátis. Sucesso: o [json] de agora mais
     * `mensagem`. Recusa: o mesmo formato de [recusaDeAtivacao].
     */
    fun iniciarAvaliacao(): Result<JSONObject> {
        val de = Portao.nivel()
        return Licencas.iniciarAvaliacao().map { json().put("mensagem", Avaliacao.mensagemDeIniciada(de)) }
    }

    private fun licencaJson(): JSONObject {
        val r = Licencas.resumo.value
        val saldos = JSONArray()
        r.carteira.saldos().forEach { s ->
            saldos.put(
                JSONObject()
                    .put("nivel", s.nivel.id)
                    .put("titulo", s.nivel.titulo)
                    .put("vitalicia", s.vitalicia)
                    .put("totalSeg", s.totalSeg)
                    .put("usadoSeg", s.usadoSeg)
                    .put("restanteSeg", if (s.vitalicia) JSONObject.NULL else s.restanteSeg)
                    .put("fracaoRestante", s.fracaoRestante)
                    .put("temLicenca", r.carteira.aplicadas.any { it.licenca.nivel == s.nivel })
            )
        }
        val aplicadas = JSONArray()
        r.carteira.aplicadas.sortedBy { it.aplicadaEmMs }.forEach { a ->
            val l = a.licenca
            aplicadas.put(
                JSONObject()
                    .put("serie", l.serie)
                    .put("nivel", l.nivel.id)
                    .put("horas", l.horas)
                    .put("vitalicia", l.vitalicia)
                    .put("aplicadaEm", a.aplicadaEmMs)
                    .put("usadoSeg", r.carteira.usoDe(l.serie).coerceAtMost(l.segundos))
                    .put("teste", l.chave == ChavesPublicas.ID_DE_TESTE)
            )
        }
        return JSONObject()
            .put("vin", r.vin?.let { Licenca.vinMascarado(it) } ?: JSONObject.NULL)
            .put("vinLido", r.vinLidoAgora)
            .put("ativo", r.nivelAtivo?.id ?: JSONObject.NULL)
            .put("saldos", saldos)
            .put("aplicadas", aplicadas)
            .put("deOutroCarro", r.deOutroCarro)
            .put("aviso", r.aviso?.let { JSONObject().put("tipo", it.tipo.id).put("texto", it.texto) } ?: JSONObject.NULL)
            .put("comoObter", ComoObterLicenca.TEXTO)
            .put("paginaNoCelular", "/ativar")
    }

    /**
     * `POST /licenca/ativar` com `{"codigo": "…"}`. Sucesso: o [json] de agora mais `ativada` e
     * `mensagem`. Recusa: `{ok:false, motivo, error, linha?}` — o instalador e a página mostram o
     * `error` como está.
     */
    fun ativar(corpo: JSONObject?): Result<JSONObject> {
        val codigo = corpo?.optString("codigo").orEmpty()
        return Licencas.ativar(codigo).map { a ->
            json()
                .put("ativada", JSONObject().put("serie", a.licenca.serie).put("nivel", a.licenca.nivel.id))
                .put("mensagem", mensagemDeAtivada(a.licenca))
        }
    }

    fun mensagemDeAtivada(l: Licenca): String =
        "Licença ${l.nivel.titulo} " + (if (l.vitalicia) "vitalícia" else "de ${l.horas} h") + " ativada."

    fun recusaDeAtivacao(e: Throwable): JSONObject {
        val recusa = e as? Licencas.Recusa
        return JSONObject()
            .put("ok", false)
            .put("motivo", recusa?.motivo?.id ?: "erro")
            .put("error", e.message ?: "Não deu para concluir.")
            .put("linha", recusa?.linha ?: JSONObject.NULL)
    }

    /**
     * `POST /licenca/nivel-de-teste` com `{"nivel": "drive"}` (ou `null` para voltar ao normal) e,
     * opcional, `"cobranca": true`. Só em build de teste (`-Popengw.nivelDeTeste=true` ou
     * simulador): no APK do instalador responde recusado.
     */
    fun definirNivelDeTeste(corpo: JSONObject?): Result<JSONObject> {
        if (corpo != null && corpo.has("cobranca")) {
            Portao.definirCobrancaDeTeste(corpo.optBoolean("cobranca")).onFailure { return Result.failure(it) }
            if (!corpo.has("nivel")) return Result.success(json())
        }
        val bruto = corpo?.opt("nivel")
        val nivel = if (bruto == null || bruto == JSONObject.NULL || bruto.toString().isBlank()) null
        else Nivel.deId(bruto.toString()) ?: return Result.failure(IllegalArgumentException("nível desconhecido: $bruto"))
        return Portao.definirNivelDeTeste(nivel).map { json() }
    }

    /**
     * `POST /licenca/teste` (só build de teste): `{"vin": "…"|"", "ready": "on"|"off"|"",
     * "cobranca": bool, "consumirMinutos": n, "fingirNivel": "signature"|"", "sentinela": true}` —
     * cada campo é opcional. `fingirNivel` imita um APK com as licenças editadas; `sentinela` roda
     * uma ronda da [Sentinela] sem esperar a confirmação. Teste grátis (8.4): `"velocidade": 40`
     * (km/h fingidos; `""` volta à real), `"zerarAvaliacao": true` (apaga o teste das três cópias).
     * Coringas (8.5): `"tecla": "517:1"` entrega keycode:ação à cadeia inteira do volante como se
     * viesse do `inputservice`, que o emulador não tem.
     */
    fun teste(corpo: JSONObject?): Result<JSONObject> {
        val c = corpo ?: JSONObject()
        if (c.has("tecla")) {
            if (!Portao.testeLiberado) return Result.failure(SecurityException("só no build de teste"))
            val partes = c.optString("tecla").split(':')
            val codigo = partes.getOrNull(0)?.trim()?.toIntOrNull() ?: return Result.failure(IllegalArgumentException("tecla"))
            val acao = partes.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            val consumiu = br.com.opengw.veiculo.volante.Volante.injetar(codigo, acao)
            return Result.success(JSONObject().put("ok", true).put("consumiu", consumiu))
        }
        return Licencas.definirTeste(
            vin = if (c.has("vin")) c.optString("vin") else null,
            ready = if (c.has("ready")) c.optString("ready") else null,
            cobranca = if (c.has("cobranca")) c.optBoolean("cobranca") else null,
            consumirMinutos = if (c.has("consumirMinutos")) c.optInt("consumirMinutos") else null,
            fingirNivel = if (c.has("fingirNivel")) c.optString("fingirNivel") else null,
            rondaDaSentinela = c.optBoolean("sentinela", false),
            velocidade = if (c.has("velocidade") && c.optString("velocidade").isNotBlank()) c.optDouble("velocidade").toFloat() else null,
            limparVelocidade = c.has("velocidade") && c.optString("velocidade").isBlank(),
            zerarAvaliacao = c.optBoolean("zerarAvaliacao", false)
        ).map { json() }
    }

    /** O corpo da recusa de uma rota travada (403). */
    fun recusa(recurso: Recurso): JSONObject =
        JSONObject()
            .put("ok", false)
            .put("bloqueado", true)
            .put("recurso", recurso.id)
            .put("nivelExigido", recurso.nivel.id)
            .put("nivel", Portao.nivel().id)
            .put("error", "Disponível no ${recurso.nivel.titulo}: ${recurso.titulo}.")
}

/**
 * "Como obter uma licença" — o texto de contato. A CONFIRMAR com o dono do projeto: por enquanto
 * genérico. Aparece em Informações → Licença, no instalador e na página do celular.
 */
object ComoObterLicenca {
    const val TEXTO =
        "Para obter uma licença, fale com quem instalou o OPEN GW no seu carro e informe o VIN " +
            "(o número de chassi, que aparece aqui). A licença vem como um código de 3 linhas, que " +
            "vale só para este carro. O jeito mais fácil de ativar: no computador, abra o OPEN GW " +
            "conectado ao carro, clique em Ativar licença e cole a mensagem que você recebeu."
}
