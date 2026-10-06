package br.com.deepgwt.managers

import br.com.deepgwt.BuildConfig
import br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O componente da tela inicial 3D que o resto do app cita e a troca do HOME preferido. */
class ComponenteDaTela3dTest {

    @Test
    fun `o componente citado pela guarda e a Activity nova`() {
        assertEquals(TelaInicial3dActivity::class.java.name, GuardaDoLauncher3D.COMPONENTE_3D)
    }

    @Test
    fun `so o componente completo de hoje conta como a tela 3d atual`() {
        val pacote = BuildConfig.APPLICATION_ID
        assertTrue(HomeLauncherController.ehATela3dAtual(pacote to GuardaDoLauncher3D.COMPONENTE_3D))
        // o app instalado antes registrou a Activity antiga, com o MESMO pacote: precisa refazer
        assertFalse(HomeLauncherController.ehATela3dAtual(pacote to GuardaDoLauncher3D.COMPONENTE_3D_ANTIGO))
        assertFalse(HomeLauncherController.ehATela3dAtual("com.beantechs.mediacenter" to "com.beantechs.mediacenter.Home"))
        assertFalse(HomeLauncherController.ehATela3dAtual(null))
    }

    @Test
    fun `ehOHomeAtual compara pacote e classe`() {
        val atual = "com.a" to "com.a.Home"
        assertTrue(HomeLauncherController.ehOHomeAtual(atual, "com.a", "com.a.Home"))
        assertFalse(HomeLauncherController.ehOHomeAtual(atual, "com.a", "com.a.Outra"))
        assertFalse(HomeLauncherController.ehOHomeAtual(atual, "com.b", "com.a.Home"))
        assertFalse(HomeLauncherController.ehOHomeAtual(null, "com.a", "com.a.Home"))
    }
}
