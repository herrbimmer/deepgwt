package br.com.opengw.ajustes

import br.com.opengw.licenca.Recurso

/**
 * O vocabulário do catálogo de ajustes: o que um ajuste É, sem nada de Android.
 *
 * POR QUE O CATÁLOGO MORA NO CARRO
 *
 * Quem sabe quais opções existem é o APK instalado. Se a lista morasse no instalador, cada
 * opção nova do app exigiria um instalador novo — e um instalador velho, conversando com um app
 * novo, mostraria a lista de ontem sem avisar. Morando aqui, o instalador só desenha o que o
 * carro descreve: a Alpha 6.5 ganha um interruptor e o computador já mostra, sem atualização.
 *
 * Tudo neste arquivo é Kotlin puro — sem Context, sem SharedPreferences, sem org.json — para
 * que a integridade do catálogo e a validação dos valores rodem no teste de JVM.
 */

/**
 * Seções na ordem em que o instalador as mostra. Espelham as telas do app no carro.
 *
 * [recurso] é o nível que a seção pede (Alpha 8.0); um ajuste pode pedir outro ([Ajuste.recurso]).
 */
enum class SecaoDeAjustes(val id: String, val titulo: String, val descricao: String, val recurso: Recurso) {
    BARRA(
            "barra",
            "Barra inferior",
            "A barra do OPEN GW por cima das telas: altura, botões e o cartão de volume.",
            Recurso.BARRA
    ),
    CARRO(
            "carro",
            "Carro e segurança",
            "Alertas, monitoramento do motorista, vidros e conectividade ao desligar.",
            Recurso.AJUSTES_RAPIDOS
    ),
    GESTOS(
            "gestos",
            "Gestos, voz e macros",
            "Barra lateral do carro, gestos com vários dedos, escuta do \"Olá Haval\" e macros.",
            Recurso.GESTOS
    ),
    ASD("asd", "Som do motor (ASD)", "O som de motor simulado pelos alto-falantes.", Recurso.ASD),
    AUDIO(
            "audio",
            "Áudio",
            "Equalizador e processamento do amplificador do carro, e o reverb de software.",
            Recurso.AUDIO
    ),
    PAINEL(
            "painel",
            "Painel de instrumentos",
            "O que o OPEN GW mostra atrás do volante: tema, informações extras e aparência.",
            Recurso.TEMAS_EMBUTIDOS
    ),
    HUD("hud", "Head-up display", "Velocidade, placa de limite e alertas no para-brisa.", Recurso.HUD),
    LUZ("luz", "Luz ambiente", "Fitas de LED externas (LEDCAR/LEDDMX) por Bluetooth ou DMX.", Recurso.LUZ_AMBIENTE),
    BRILHO(
            "brilho",
            "Brilho automático",
            "Brilho da central que acompanha o dia e a noite, por horário ou pelo sol.",
            Recurso.BRILHO
    ),
    AUTOMACOES(
            "automacoes",
            "Automações do veículo",
            "O que o OPEN GW faz sozinho ao ligar, desligar, trancar ou andar.",
            Recurso.AUTOMACOES
    ),
    SISTEMA(
            "sistema",
            "Sistema e desempenho",
            "Apps de fábrica desativados e o indicador de CPU e RAM.",
            Recurso.SISTEMA
    ),
    LAUNCHER("launcher", "Launcher 3D", "O que a tela inicial 3D pode fazer no carro.", Recurso.LAUNCHER_3D),
    INTERFACE(
            "interface",
            "Interface do OPEN GW",
            "O visual das telas do app do OPEN GW na central: estilo, tamanho da interface e das letras.",
            Recurso.INTERFACE
    )
}

enum class TipoDeAjuste(val id: String) {
    INTERRUPTOR("interruptor"),
    NUMERO("numero"),
    OPCOES("opcoes"),
    TEXTO("texto"),
    COR("cor"),
    /** Horário `HH:MM`. No disco são duas chaves inteiras (hora e minuto), como a tela antiga gravava. */
    HORA("hora")
}

/** Como o valor está gravado no `opengw_prefs`. O tipo tem de bater — ver [ValorDePreferencia]. */
enum class Formato {
    BOOLEANO,
    INTEIRO,
    DECIMAL,
    TEXTO,
    /** `Int` ARGB no disco, `#RRGGBB` na conversa com o instalador. Sempre opaco. */
    COR_ARGB,
    /** A HORA de um `HH:MM`, como `Int`. O minuto vai numa chave própria, por [Espelho]. */
    HORA
}

/** Uma das três escolhas do painel (ver `PainelEscolhido`). Não são chaves: são um modo. */
enum class ParteDoPainel { TEMA_CUSTOMIZADO, INFORMACOES, CIRCULO }

/** Onde o valor mora. */
sealed interface Guardado {
    /** Uma chave do `opengw_prefs`. */
    data class Preferencia(val chave: String, val formato: Formato) : Guardado

    /**
     * Uma chave do barramento do carro (`sys.settings.audio.*`), lida e escrita pelo
     * ControlService. [booleano] = o carro fala "1"/"0". [chaveDaFaixa] = chave que diz a faixa
     * real deste amplificador ("-7,7", "0~14"); quando responde, vale mais que o min/max do catálogo.
     */
    data class Veiculo(val chave: String, val booleano: Boolean, val chaveDaFaixa: String? = null) :
            Guardado

    data class Painel(val parte: ParteDoPainel) : Guardado

    /**
     * O tema do painel: a pasta ativa em `files/themes` (vazia = o embutido no APK). As opções são
     * os temas instalados, lidos na hora — um tema enviado do computador aparece sem atualizar o
     * catálogo. Quem grava é `managers.TemaDoPainel`, o mesmo caminho da tela Telas.
     */
    data object TemaAtivo : Guardado
}

/**
 * O que precisa acontecer DEPOIS de gravar para o carro obedecer na hora.
 *
 * Cada um é chamado pelo mesmo caminho que a tela do carro usa quando o usuário mexe naquele
 * ajuste — nenhum caminho novo. [NENHUM] = quem usa a preferência lê na hora de usar.
 */
enum class Efeito(val nome: String) {
    NENHUM("lido na hora"),
    BARRA("barra inferior"),
    BARRA_SERVICO("serviço da barra inferior"),
    ASD("som do motor (ASD)"),
    HUD("camada do HUD"),
    LUZ_AMBIENTE("luz ambiente"),
    GESTOS("gestos rápidos"),
    PAINEL_LATERAL("barra lateral do carro"),
    BRILHO("brilho automático"),
    APPS_DO_SISTEMA("apps do sistema desativados"),
    AVAS("AVAS"),
    MONITORAMENTO("monitoramento de distração e fadiga"),
    VIDRO_AO_TRANCAR("vidros ao trancar"),
    ESCUTA_DE_VOZ("escuta do \"Olá Haval\""),
    PAINEL("modo do painel"),
    PAINEL_ESCALA("tamanho do texto do painel"),
    PAINEL_COR_NOTURNA("cor do modo noturno"),
    PAINEL_FONTE_NOTURNA("fonte do modo noturno"),
    PAINEL_TEMPERATURA("tamanho da temperatura do painel de fábrica"),
    AUDIO_SOFTWARE("reverb de software"),
    ACUSTICA("acústica do amplificador"),
    HEV("bateria do HEV"),
    MACROS("macros"),
    TELA_INICIAL("tela inicial do carro")
}

data class Opcao(val id: String, val rotulo: String)

/**
 * O ajuste só aparece quando [id] vale um de [valores] (comparados como texto: `"true"`,
 * `"RPM"`, `"custom"`). Se o ajuste de que ele depende também estiver escondido, este some junto
 * — o instalador resolve em cadeia.
 */
data class Condicao(val id: String, val valores: Set<String>)

/**
 * Outra chave que anda junto com esta, com o valor derivado do valor escolhido.
 *
 * Existe para os casos em que a tela do carro grava duas chaves com um toque só — "desativar
 * conectividade" liga hotspot E Bluetooth; o controle de temperatura da barra mantém os dois
 * interruptores antigos coerentes para um downgrade do app. Gravar só a principal deixaria o
 * carro num estado que a tela dele nunca produz.
 */
data class Espelho(val chave: String, val formato: Formato, val derivar: (Any) -> Any)

/**
 * O leitor mínimo de que um ajuste com leitura própria precisa. Interface para ser implementada
 * pelo SharedPreferences no carro e por um mapa no teste.
 */
interface LeitorDePreferencias {
    fun contem(chave: String): Boolean
    fun booleano(chave: String, padrao: Boolean): Boolean
    fun inteiro(chave: String, padrao: Int): Int
    fun texto(chave: String): String?
}

data class Ajuste(
        /** Identidade estável na conversa com o instalador. Para preferência, é a própria chave. */
        val id: String,
        val secao: SecaoDeAjustes,
        val rotulo: String,
        val tipo: TipoDeAjuste,
        val guardado: Guardado,
        /** No formato da conversa: Boolean, Int/Double, String ou "#RRGGBB". */
        val padrao: Any?,
        val ajuda: String? = null,
        val min: Double? = null,
        val max: Double? = null,
        val passo: Double? = null,
        val unidade: String? = null,
        val opcoes: List<Opcao> = emptyList(),
        val maxCaracteres: Int? = null,
        val visivelSe: Condicao? = null,
        val efeito: Efeito = Efeito.NENHUM,
        val espelhos: List<Espelho> = emptyList(),
        /** Leitura própria quando o valor não é "a chave ou o padrão" (legado, duas chaves). */
        val lerDe: ((LeitorDePreferencias) -> Any?)? = null,
        /** Só aparece no modo Avançado do instalador. */
        val avancado: Boolean = false,
        /** Texto que o instalador mostra e pede confirmação antes de LIGAR. */
        val aviso: String? = null,
        /** Não há tela para isto no app do carro: o computador é o único lugar onde se muda. */
        val soNoComputador: Boolean = false,
        /** O nível que este ajuste pede, quando não é o da [secao] (Alpha 8.0). */
        val recurso: Recurso? = null
) {
    /** O recurso que vale para este ajuste: o próprio ou o da seção. */
    val recursoEfetivo: Recurso
        get() = recurso ?: secao.recurso

    /** Número sem casas decimais (inteiro no disco ou no barramento). */
    val inteiro: Boolean
        get() =
                when (val g = guardado) {
                    is Guardado.Preferencia -> g.formato == Formato.INTEIRO
                    is Guardado.Veiculo -> !g.booleano
                    is Guardado.Painel -> false
                    is Guardado.TemaAtivo -> false
                }
}
