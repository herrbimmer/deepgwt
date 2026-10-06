// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.deepgwt.licenca.Recurso

/**
 * Uma seção do menu principal: o nome que aparece, o ícone e o nível que ela pede para abrir.
 *
 * [recurso] é o que a seção exige do nível do usuário. Seção com itens de níveis diferentes abre no
 * nível mais baixo, e a trava item por item fica lá dentro.
 */
data class ItemDoMenu(
    val titulo: String,
    val icone: ImageVector,
    val recurso: Recurso = Recurso.ESSENCIAL,
)

/**
 * As seções do menu, na ordem em que aparecem: o que se mexe no dia a dia vem primeiro e o avançado
 * ou de depuração, por último.
 *
 * Um item por linha, sempre no mesmo formato (título, ícone e, se não for o padrão, o recurso): os
 * testes leem este arquivo como texto e conferem o menu contra a tabela de níveis e o roteiro do
 * tour. O item de ADAS só chega à tela com o uso avançado ligado; quem filtra é a navegação.
 */
val itensDoMenu: List<ItemDoMenu> = listOf(
    // O dia a dia.
    ItemDoMenu("Configurações", Icons.Outlined.Settings),
    ItemDoMenu("ASD", Icons.Outlined.Speed, Recurso.ASD),
    ItemDoMenu("Áudio", Icons.Outlined.GraphicEq, Recurso.AUDIO),
    ItemDoMenu("Telas", Icons.Outlined.SmartDisplay),
    // A tela inicial que abre com o carro: fica ao lado de "Telas".
    ItemDoMenu("Tela Inicial", Icons.Outlined.Home),
    // O visual do próprio app, fora de "Telas".
    ItemDoMenu("Interface do DeepGWT", Icons.Outlined.Palette),
    ItemDoMenu("Recursos XTRA", Icons.Outlined.Apps),
    // O caderno de manutenção: revisões e serviços feitos, no app ou pelo instalador.
    ItemDoMenu("Histórico de Serviço", Icons.Outlined.Build),
    // Dados móveis (4G) da central e roteador do hotspot.
    ItemDoMenu("Rede", Icons.Outlined.Wifi, Recurso.SISTEMA),
    ItemDoMenu("Instalar Apps", Icons.Outlined.ShoppingCart, Recurso.INSTALAR_APPS),
    ItemDoMenu("Arquivos", Icons.Outlined.Folder, Recurso.ARQUIVOS),
    ItemDoMenu("Informações", Icons.Outlined.Info),
    // O avançado e a depuração.
    ItemDoMenu("Diagnóstico", Icons.Outlined.MonitorHeart, Recurso.DIAGNOSTICO),
    ItemDoMenu("Valores Atuais", Icons.Outlined.DeveloperMode, Recurso.VALORES_ATUAIS),
    // Mexe com sistemas de segurança ativa do carro.
    ItemDoMenu("ADAS", Icons.Outlined.Shield, Recurso.ADAS),
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
