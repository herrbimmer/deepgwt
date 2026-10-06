// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

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
                        "Tour guiado pelas partes principais do DeepGWT.",
                        "Interface do DeepGWT agora muda o tamanho das telas e das letras do app."
                    ),
                passosDoTour = listOf("interface"),
                ofereceAssistente = true
            ),
            Novidade(
                versionCode = 71,
                versao = "Alpha 8.0",
                itens =
                    listOf(
                        "O DeepGWT agora tem quatro níveis: Essencial, Plus, Pro e Max.",
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
                        "Licença do DeepGWT: um código feito para o seu carro (pelo VIN) libera o Plus, o Pro ou o Max pelas horas de uso.",
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
                        "O jeito mais fácil de ativar: no computador, abra o DeepGWT, clique em Ativar licença e cole a mensagem inteira que você recebeu.",
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
                        "Teste grátis: experimente o Max, a versão completa, por 10 minutos de uso — uma vez por carro. Depois o DeepGWT volta ao seu nível.",
                        "Comece em Informações → Nível e licença, pelo celular ou pelo instalador no computador."
                    ),
                passosDoTour = listOf("informacoes")
            ),
            Novidade(
                versionCode = 76,
                versao = "Alpha 8.5",
                itens =
                    listOf(
                        "Assistentes de faixa e aviso de velocidade: o DeepGWT lembra como você deixou e devolve a cada partida (Configurações). Frenagem automática nunca entra.",
                        "Botões coringa do volante: dê a eles uma função do DeepGWT (câmera 360°, desembaçador, recirculação, macro…). Desligando, volta a função que você escolheu no menu do carro.",
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
                        "A cada passo (porta aberta, cinto solto, setas…) ela guarda as luzes e avisos do carro e as telas; o pacote fica em /sdcard/DeepGWT/calibracao para baixar pelo instalador, em Arquivos.",
                        "Só com o carro parado: se o carro andar, ou depois de 30 minutos, o painel volta sozinho ao seu tema."
                    )
            ),
            Novidade(
                versionCode = 80,
                versao = "Alpha 8.9",
                itens =
                    listOf(
                        "Tela inicial 3D mais leve: as rodas do H6 GT ficaram com quase metade dos triângulos, sem mudar o desenho, e os painéis por cima do carro não desfocam mais o fundo a cada quadro.",
                        "Mais estável: se a tela inicial 3D travar por falta de memória, só ela recomeça, em menos de um segundo, e o resto do DeepGWT (painel, barra, Shizuku) segue funcionando."
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
                        "Luz ambiente: não pisca mais com a seta nem fica piscando depois dela. Em Recursos XTRA você escolhe o que ela segue: pisca-alerta, ponto cego, seta, farol, modo de condução e temperatura.",
                        "Dois temas novos no painel: TEA 3.0, no desenho do painel de fábrica, e Cupertino Extended, com vidro líquido.",
                        "A projeção da central enche o centro de todos os temas, entre os mostradores, como no Stuttgart Clássico.",
                        "Interface do DeepGWT ganhou o estilo Cupertino: vidro líquido, ícones coloridos e dock."
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
                        "Avisos legais: a licença do DeepGWT e os avisos do software de terceiros estão dentro do app, em Informações, e no instalador, em Sobre.",
                        "Os 20 fundos prontos do cluster e do launcher foram trocados por imagens originais do DeepGWT. Quem tinha um dos antigos escolhido continua com um fundo equivalente.",
                        "Nomes de tema sem marcas de terceiros: Bávaro Aberto, Bávaro Chevron, Stuttgart Clássico, Stuttgart Sport e Sweede Redondo (os mesmos temas, só o nome mudou).",
                        "O ícone Atualizar passou a explicar que a atualização do DeepGWT é feita pelo instalador, no computador, pelo Wi-Fi do carro."
                    )
            ),
            Novidade(
                versionCode = 89,
                versao = "Alpha 9.8.0",
                itens =
                    listOf(
                        "Telas › Aparência: o modo noturno do painel agora liga e desliga. Desligado, os letreiros do painel não mudam de cor ao acender o farol.",
                        "Configurações › Câmera 360°: opção de desligar a câmera 360° com o carro parado, com a subopção de deixá-la abrir se você apertar o botão da câmera 360°.",
                        "Versão no formato 9.8.0: o primeiro número é a versão principal, o segundo a subversão e o terceiro conta só correções de erros."
                    )
            ),
            Novidade(
                versionCode = 90,
                versao = "Alpha 9.9.0",
                itens =
                    listOf(
                        "Gestos rápidos com 3 dedos mais fáceis: o arrasto vale pelo eixo que mais andou, sem precisar ser reto, e pede menos caminho (para os lados também)."
                    )
            ),
            Novidade(
                versionCode = 92,
                versao = "Alpha 10.0.0",
                itens =
                    listOf(
                        "Botões extra (antes Stream Deck): escolha entre um Stream Deck (MiraBox StreamDock 293S) ou um teclado/dispositivo USB, e ensine cada tecla apertando-a.",
                        "Atalhos no launcher: aperte e segure um interruptor do app e toque em Adicionar atalho. Ele vira um ícone na grade de apps do launcher que liga e desliga o ajuste.",
                        "Assistente inicial: agora pede a escolha entre a predefinição recomendada (sem AVAS, gestos, HUD, tema Stuttgart, câmera 360° só pelo botão, hotspot e Bluetooth desligando com o carro) e customizar do zero."
                    )
            ),
            Novidade(
                versionCode = 93,
                versao = "Alpha 10.1.0",
                itens =
                    listOf(
                        "Teste do Max sem relógio: vale até desligar o carro (ou a central reiniciar) e depois volta ao seu plano.",
                        "São 2 testes grátis por instalação. Depois dá para testar de novo, com um aviso de pagamento em todas as telas (central, painel e HUD), sem bloquear nada.",
                        "Voltar ao meu plano: encerra o teste na hora, em Informações → Nível, no instalador ou no celular."
                    )
            ),
            Novidade(
                versionCode = 94,
                versao = "Alpha 10.2.0",
                itens =
                    listOf(
                        "Launcher 3D com visual novo (Grafite): widgets refeitos, mais limpos e fáceis de ler, em tema escuro e claro.",
                        "Configurações do launcher refeitas: uma folha fina na lateral, com seções Cor, Rodas, Cenário, Câmera, Carro e Tela. Os ajustes aparecem no carro na hora, e ao tocar num controle a folha fica transparente para você ver o resultado.",
                        "No instalador, o Coding completo agora junta as mudanças numa lista e só grava no carro depois do botão Flash e de uma confirmação."
                    )
            ),
            Novidade(
                versionCode = 95,
                versao = "Alpha 10.3.0",
                itens =
                    listOf(
                        "Cartões do launcher 3D agora com vidro de verdade: o carro e o cenário aparecem desfocados atrás deles. Em Configurações > Tela, \"Vidro dos cartões\" tem Desligado, Suave e Forte (Desligado é o mais leve para a central).",
                        "Os ícones de baixo agora ficam numa dock. Em Configurações > Tela escolha o estilo: Flat (bandeja arredondada, como no macOS atual), 3D (prateleira de vidro com reflexo, como no Mac OS X Lion), Barra (de ponta a ponta) ou Sem dock."
                    )
            ),
            Novidade(
                versionCode = 96,
                versao = "Alpha 10.4.0",
                itens =
                    listOf(
                        "O modelo 3D do carro agora aparece sempre inteiro: duas colunas à direita ficam reservadas para ele e a câmera se afasta o quanto precisar para o carro caber por completo no espaço livre.",
                        "Autonomia e Estado do veículo agora entram no mesmo espaço do cartão de Mídia, com abas na base para alternar, em vez de ocupar mais lugar na tela. Cartões de layouts antigos que não cabem mais também entram nessa pilha."
                    )
            ),
            Novidade(
                versionCode = 97,
                versao = "Alpha 10.4.1",
                itens =
                    listOf(
                        "Saiu o botão \"Cards\" no canto inferior esquerdo da dock: os cartões agora ficam fixos na tela, então a dock mostra só os apps."
                    )
            ),
            Novidade(
                versionCode = 98,
                versao = "Alpha 10.5.0",
                itens =
                    listOf(
                        "Pilha de cartões: deslize para os lados num cartão empilhado para trocar entre Mídia, Clima e os outros (as abas na base continuam).",
                        "Qualquer cartão pode entrar na pilha: segure o cartão e toque em \"Empilhar\" (ele vai para a Mídia), ou \"Separar\" para devolvê-lo à tela. Quando não sobra lugar livre, o cartão novo entra na pilha sozinho."
                    )
            ),
            Novidade(
                versionCode = 99,
                versao = "Alpha 10.5.1",
                itens =
                    listOf(
                        "A pilha de cartões já vem pronta: Mídia, Navegação, Clima, Autonomia e Estado ficam no mesmo espaço, e é só passar o dedo para os lados para trocar (a troca desliza). Quem tinha só a Mídia na tela ganha a pilha uma vez; para voltar só à Mídia, remova os outros cartões.",
                        "O deslize também funciona começando sobre os botões de um cartão, e as abas da base viraram pontos, com o nome só do cartão aberto."
                    )
            ),
            Novidade(
                versionCode = 100,
                versao = "Alpha 10.6.0",
                itens =
                    listOf(
                        "A dock agora é Liquid Glass, como nos sistemas da Apple: vidro translúcido com reflexos e borda luminosa (em Configurações > Tela, o estilo \"Flat\" virou \"Liquid Glass\"; 3D e Barra também ganharam o visual).",
                        "Todos os controles deslizantes do launcher, do app e do instalador agora são Liquid Glass: calha de vidro, parte cheia translúcida e botão em pílula de vidro que cresce ao tocar.",
                        "Corrigido: os botões do cartão de Clima se sobrepunham em janelas pequenas e a barra da pilha cobria o fim do cartão. O conteúdo agora se ajusta ao tamanho da janela."
                    )
            ),
            Novidade(
                versionCode = 101,
                versao = "Alpha 10.7.0",
                itens =
                    listOf(
                        "Todos os recursos estão liberados: não é mais preciso licença, código de ativação nem VIN. A estrutura das licenças continua no código, desligada, para voltar no futuro.",
                        "Novo botão \"Nos apoie\" no rodapé da tela inicial do app, com o espaço da chave PIX e do QR (por enquanto em branco, com um QR de exemplo marcado como INVALID CODE).",
                        "Nova licença do projeto: Vespermann Accessible Software License (VASL) 1.0 — uso pessoal gratuito; forks públicos, redistribuição e uso comercial exigem autorização do autor."
                    )
            )
        )

    /** As novidades depois de [vistasAte] até [atual], inclusive, em ordem de versão. */
    fun entre(vistasAte: Int, atual: Int): List<Novidade> =
        TODAS.filter { it.versionCode in (vistasAte + 1)..atual }.sortedBy { it.versionCode }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
