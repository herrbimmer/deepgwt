package br.com.opengw.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * O cartão das telas antigas (caso 34): largura total, margem de 8 dp em volta, borda clássica de
 * 1 dp com canto 8 por cima de um cartão de canto 12 com sombra de 1 dp. A borda não acompanha o
 * canto do cartão — é o visual de hoje (D10).
 */
@Composable
fun CartaoClassico(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(MedidasClassicas.margemDoCartao)
            .border(
                width = MedidasClassicas.espessuraDaBorda,
                color = CoresClassicas.borda,
                shape = RoundedCornerShape(MedidasClassicas.cantoDaBorda),
            ),
        shape = RoundedCornerShape(MedidasClassicas.cantoDoCartao),
        colors = CardDefaults.cardColors(containerColor = CoresClassicas.cartao),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = conteudo,
    )
}

/**
 * O campo preenchido das telas antigas (caso 35). Só fundo, texto, linha de baixo e rótulo são
 * clássicos; cursor, seleção e desabilitado ficam os do Material (o cursor sai na primária do
 * esquema de reserva — D1).
 */
@Composable
fun CampoDeTextoClassico(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    linhaUnica: Boolean = true,
    teclado: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        value = valor,
        onValueChange = aoMudar,
        modifier = modifier,
        enabled = habilitado,
        label = rotulo,
        keyboardOptions = teclado,
        singleLine = linhaUnica,
        colors = TextFieldDefaults.colors(
            focusedTextColor = CoresClassicas.texto,
            unfocusedTextColor = CoresClassicas.textoSecundario,
            focusedContainerColor = CoresClassicas.superficie,
            unfocusedContainerColor = CoresClassicas.superficie,
            focusedIndicatorColor = CoresClassicas.destaque,
            unfocusedIndicatorColor = CoresClassicas.botaoSecundario,
            focusedLabelColor = CoresClassicas.destaque,
            unfocusedLabelColor = CoresClassicas.textoSecundario,
        ),
    )
}
