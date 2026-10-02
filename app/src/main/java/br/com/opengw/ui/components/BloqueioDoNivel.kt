package br.com.opengw.ui.components

import br.com.opengw.ui.GlossyCard
import br.com.opengw.ui.Cores
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.licenca.Nivel
import br.com.opengw.licenca.Portao
import br.com.opengw.licenca.Recurso

/*
 * Cadeados dos níveis na tela do carro (Alpha 8.0).
 *
 * Bloqueado APARECE — apagado, com o nome do nível que libera — em vez de sumir: quem está no Drive
 * vê o que existe no Sport. As telas perguntam por aqui; os serviços, direto ao [Portao].
 */

/** O nível agora, observado: a tela redesenha quando ele muda. */
@Composable
fun nivelAtual(): Nivel {
    val estado by Portao.estado.collectAsState()
    return estado.nivel
}

@Composable
fun liberado(recurso: Recurso): Boolean = nivelAtual().alcanca(recurso.nivel)

/** Selo pequeno: cadeado e o nome do nível. */
@Composable
fun SeloDoNivel(nivel: Nivel, modifier: Modifier = Modifier) {
    val formato = RoundedCornerShape(50)
    Row(
        modifier
            .clip(formato)
            .background(Cores.conteiner)
            .border(1.dp, Cores.destaque.copy(alpha = 0.6f), formato)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(14.dp))
        Text(nivel.titulo.uppercase(), color = Cores.textoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

/** A seção inteira travada: o que é, qual nível libera e o que vem junto com ele. */
@Composable
fun CartaoDoNivel(recurso: Recurso, modifier: Modifier = Modifier) {
    val atual = nivelAtual()
    GlossyCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(44.dp))
            Text(
                "Disponível no ${recurso.nivel.titulo}",
                color = Cores.textoPrincipal,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(recurso.titulo, color = Cores.textoPrincipal, fontSize = 18.sp)
            Text(
                recurso.nivel.resumo,
                color = Cores.textoSecundario,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp)
            )
            Text(
                "Seu OPEN GW está no ${atual.titulo}. Em Informações → Nível você vê o que cada nível libera.",
                color = Cores.textoApagado,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** [conteudo] quando o nível libera [recurso]; senão, o [CartaoDoNivel]. Para uma seção inteira. */
@Composable
fun BloqueioDoNivel(recurso: Recurso, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    if (liberado(recurso)) conteudo() else CartaoDoNivel(recurso, modifier)
}

/**
 * Um item dentro de uma tela: com o nível, o próprio item; sem, o item apagado, sem toque, com o
 * selo do nível no canto. Mostra o que existe sem deixar mexer.
 */
@Composable
fun TravaDoNivel(recurso: Recurso, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    if (liberado(recurso)) {
        Box(modifier) { conteudo() }
        return
    }
    Box(modifier) {
        Box(Modifier.alpha(0.38f)) { conteudo() }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
        )
        SeloDoNivel(recurso.nivel, Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}
