package br.com.opengw.onboarding

/**
 * O que mudou em cada versão, do jeito que aparece para quem atualiza (Alpha 7.9).
 *
 * Uma entrada por versão que tem algo a mostrar — correções miúdas não precisam de uma. Quem
 * pula várias versões de uma vez vê as novidades de todas, da mais antiga para a mais nova.
 *
 * [Novidade.passosDoTour] são ids de [PassosDoTour]: "Mostrar onde fica" percorre só esses.
 */
data class Novidade(
    val versionCode: Int,
    val versao: String,
    val itens: List<String>,
    val passosDoTour: List<String> = emptyList(),
    /** A entrada oferece abrir o assistente de configuração (só a primeira versão que o tem). */
    val ofereceAssistente: Boolean = false
)

/** Entradas antigas usam os nomes de nível da 8.4: quem pula versões lê todas de uma vez. */
object Novidades {
    val TODAS: List<Novidade> =
        listOf(
            Novidade(
                versionCode = 70,
                versao = "Alpha 7.9",
                itens =
                    listOf(
                        "Assistente de configuração: tamanho da interface, tamanho das letras, estilo, painel e barra, em um minuto.",
                        "Tour guiado pelas partes principais do OPEN GW.",
                        "Interface do OPEN GW agora muda o tamanho das telas e das letras do app."
                    ),
                passosDoTour = listOf("interface"),
                ofereceAssistente = true
            ),
            Novidade(
                versionCode = 71,
                versao = "Alpha 8.0",
                itens =
                    listOf(
                        "O OPEN GW agora tem quatro níveis: Essencial, Plus, Pro e Max.",
                        "O que um nível não libera aparece com cadeado e o nome do nível que libera.",
                        "Em Informações → Nível e licença você vê o que cada nível traz."
                    ),
                passosDoTour = listOf("informacoes")
            ),
            Novidade(
                versionCode = 72,
                versao = "Alpha 8.1",
                itens =
                    listOf(
                        "Licença do OPEN GW: um código feito para o seu carro (pelo VIN) libera o Plus, o Pro ou o Max pelas horas de uso.",
                        "Insira em Informações → Nível e licença, pelo celular no Wi-Fi do carro (QR na tela) ou pelo instalador no computador.",
                        "As horas só contam com o carro ligado; quando faltar pouco, um aviso aparece ao ligar, com o carro parado."
                    ),
                passosDoTour = listOf("informacoes")
            ),
            Novidade(
                versionCode = 73,
                versao = "Alpha 8.2",
                itens =
                    listOf(
                        "Licença mais curta: o código agora tem 3 linhas (60 caracteres) em vez de 5.",
                        "O jeito mais fácil de ativar: no computador, abra o OPEN GW, clique em Ativar licença e cole a mensagem inteira que você recebeu.",
                        "No carro e no celular também dá para colar a mensagem inteira: o código é achado no meio do texto."
                    ),
                passosDoTour = listOf("informacoes")
            ),
            Novidade(
                versionCode = 75,
                versao = "Alpha 8.4",
                itens =
                    listOf(
                        "Os níveis ganharam nomes novos: Essencial (grátis), Plus, Pro e Max. As licenças que você já tem continuam valendo.",
                        "Teste grátis: experimente o Max, a versão completa, por 10 minutos de uso — uma vez por carro. Depois o OPEN GW volta ao seu nível.",
                        "Comece em Informações → Nível e licença, pelo celular ou pelo instalador no computador."
                    ),
                passosDoTour = listOf("informacoes")
            ),
            Novidade(
                versionCode = 76,
                versao = "Alpha 8.5",
                itens =
                    listOf(
                        "Assistentes de faixa e aviso de velocidade: o OPEN GW lembra como você deixou e devolve a cada partida (Configurações). Frenagem automática nunca entra.",
                        "Botões coringa do volante: dê a eles uma função do OPEN GW (câmera 360°, desembaçador, recirculação, macro…). Desligando, volta a função que você escolheu no menu do carro.",
                        "H6 PHEV com o conector de recarga preso: \"Liberar\" em Configurações faz o contorno num toque.",
                        "O monitoramento de distração desligado agora segura também o aviso de fadiga.",
                        "Correções de segurança e estabilidade (envio de arquivos grandes, conexão com o instalador)."
                    ),
                passosDoTour = listOf("configuracoes")
            ),
            Novidade(
                versionCode = 77,
                versao = "Alpha 8.6",
                itens =
                    listOf(
                        "Tema novo no painel: Bávaro Chevron. Velocidade e potência em dois chevrons que enchem de luz, a música no centro e a placa da via no rodapé (Telas, nível Plus).",
                        "No Bávaro Chevron dá para escolher o tom do painel (cobre, azul, vermelho ou seguindo o modo de condução) e o modo dia/noite.",
                        "Stuttgart Sport corrigido: a projeção da central aparece no miolo do túnel, e o fundo não sai mais deslocado das meias-luas."
                    ),
                passosDoTour = listOf("telas")
            ),
            Novidade(
                versionCode = 78,
                versao = "Alpha 8.7",
                itens =
                    listOf(
                        "Tema novo no painel: Sweede Redondo, com dois mostradores redondos. O ponteiro leva uma luz junto; velocidade à esquerda e, à direita, a potência híbrida, com OFF, READY, zona de carga, combustível e bateria (Telas, nível Plus).",
                        "No centro, a música que está tocando; embaixo, hodômetro, trecho, autonomias elétrica e a combustão e o consumo em km/l.",
                        "Dá para escolher o tom do painel (ardósia, azul, cobre, vermelho ou seguindo o modo de condução) e o modo dia/noite."
                    ),
                passosDoTour = listOf("telas")
            ),
            Novidade(
                versionCode = 79,
                versao = "Alpha 8.8",
                itens =
                    listOf(
                        "Calibração do painel, em Diagnóstico: uma grade de coordenadas no painel para mapear onde o carro desenha as luzes e os avisos por cima dos temas.",
                        "A cada passo (porta aberta, cinto solto, setas…) ela guarda as luzes e avisos do carro e as telas; o pacote fica em /sdcard/OpenGW/calibracao para baixar pelo instalador, em Arquivos.",
                        "Só com o carro parado: se o carro andar, ou depois de 30 minutos, o painel volta sozinho ao seu tema."
                    )
            ),
            Novidade(
                versionCode = 80,
                versao = "Alpha 8.9",
                itens =
                    listOf(
                        "Tela inicial 3D mais leve: as rodas do H6 GT ficaram com quase metade dos triângulos, sem mudar o desenho, e os painéis por cima do carro não desfocam mais o fundo a cada quadro.",
                        "Mais estável: se a tela inicial 3D travar por falta de memória, só ela recomeça, em menos de um segundo, e o resto do OPEN GW (painel, barra, Shizuku) segue funcionando."
                    )
            ),
            // A 9.0 (Alexa) e a 9.1 (assistente de voz com o Gemini) saíram só como builds de teste; o
            // assistente foi removido na 9.2, então as notas delas não aparecem para ninguém.
            Novidade(
                versionCode = 83,
                versao = "Alpha 9.2",
                itens =
                    listOf(
                        "Luzes e avisos no painel agora são só os do carro: os temas não desenham mais cinto, porta, farol, ESP, ponto cego nem READY próprios.",
                        "Sweede Redondo com fundo de um tom só. Bávaro Chevron: chevrons um pouco mais baixos, livres das luzes do farol e do cinto.",
                        "Ligar as informações sobre o painel original não faz mais o tema Embutido piscar.",
                        "O assistente de voz saiu (o Gemini não funcionou no carro); a chave que estava guardada foi apagada."
                    )
            ),
            Novidade(
                versionCode = 84,
                versao = "Alpha 9.3",
                itens =
                    listOf(
                        "Sweede Redondo: mostradores grandes de novo, no lugar dos de fábrica, com as luzes do carro em volta. O READY e a placa da via do carro aparecem na face do mostrador da velocidade, entre os números, que agora vão de 40 em 40."
                    )
            ),
            Novidade(
                versionCode = 85,
                versao = "Alpha 9.4",
                itens =
                    listOf(
                        "Avisos do HUD redesenhados: cada aviso tem o desenho da luz do painel dentro de um círculo, o sistema, o título e o detalhe. Vermelho pulsando para cinto, freio, óleo, temperatura e motor; âmbar para portas, pneus e combustível.",
                        "Cinto solto no HUD diz qual lugar (Motorista, Passageiro…). O freio de estacionamento só avisa com o carro andando.",
                        "Sweede Redondo, Bávaro Chevron e Stuttgart Sport: o READY e o ponteiro de potência acompanham o READY do carro. Antes apagavam com o carro pronto e parado.",
                        "HUD: a página no ar aparece como um traço entre os pontinhos, e o \"km/h\" ficou mais leve que o número."
                    )
            ),
            Novidade(
                versionCode = 86,
                versao = "Alpha 9.5",
                itens =
                    listOf(
                        "Controle do HUD: segure OK no volante. O painel é coberto com \"Controle do HUD ativado\" e ↑/↓ trocam as páginas do HUD. Segure OK de novo para as setas voltarem ao painel.",
                        "HUD com modo noturno (Telas): escurece à noite, com o farol ou sempre, com brilho e tons âmbar escolhíveis.",
                        "Luz ambiente: não pisca mais com a seta nem fica piscando depois dela. Em Recursos você escolhe o que ela segue: pisca-alerta, ponto cego, seta, farol, modo de condução e temperatura.",
                        "Dois temas novos no painel: TEA 3.0, no desenho do painel de fábrica, e Cupertino Extended, com vidro líquido.",
                        "A projeção da central enche o centro de todos os temas, entre os mostradores, como no Stuttgart Clássico.",
                        "Interface do OPEN GW ganhou o estilo Cupertino: vidro líquido, ícones coloridos e dock."
                    )
            ),
            Novidade(
                versionCode = 87,
                versao = "Alpha 9.6",
                itens =
                    listOf(
                        "Estilo Cupertino refeito, mais sóbrio: fundo grafite, ícones escuros de cantos arredondados com o desenho em cor, anel de foco azul e Dock de vidro com o nome do item em destaque.",
                        "Tema Cupertino Extended refeito: fundo preto, mostradores finos com ponteiro laranja, marcha e autonomias dentro dos mostradores, tocando agora e cápsulas de bateria e combustível no centro."
                    )
            ),
            Novidade(
                versionCode = 88,
                versao = "Alpha 9.7",
                itens =
                    listOf(
                        "Avisos legais: a licença do OPEN GW e os avisos do software de terceiros estão dentro do app, em Informações, e no instalador, em Sobre.",
                        "Os 20 fundos prontos do cluster e do launcher foram trocados por imagens originais do OPEN GW. Quem tinha um dos antigos escolhido continua com um fundo equivalente.",
                        "Nomes de tema sem marcas de terceiros: Bávaro Aberto, Bávaro Chevron, Stuttgart Clássico, Stuttgart Sport e Sweede Redondo (os mesmos temas, só o nome mudou).",
                        "O ícone Atualizar passou a explicar que a atualização do OPEN GW é feita pelo instalador, no computador, pelo Wi-Fi do carro."
                    )
            )
        )

    /** As novidades depois de [vistasAte] até [atual], inclusive, em ordem de versão. */
    fun entre(vistasAte: Int, atual: Int): List<Novidade> =
        TODAS.filter { it.versionCode in (vistasAte + 1)..atual }.sortedBy { it.versionCode }
}
