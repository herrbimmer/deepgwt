package br.com.opengw.managers

/**
 * A seção em que um tema do painel aparece na tela Telas e no instalador.
 *
 * Decidida pela PASTA, não pelo `theme.xml`: Legacy é o que vem dentro do APK
 * (`assets/PreinstalledThemes`), XTRA é todo o resto que chegou em `files/themes` — o catálogo XTRA
 * do instalador, um HTML importado, um tema do criador de temas. Assim nenhum tema precisa declarar
 * nada novo e o contrato v1.0 do `theme.xml` fica como está.
 */
enum class CastaDoTema(val id: String, val titulo: String) {
    LEGACY("legacy", "Temas Legacy"),
    XTRA("xtra", "Temas XTRA");

    companion object {
        fun de(pasta: String, pastasDoApk: Set<String>): CastaDoTema =
            if (pasta.isBlank() ||
                pasta.equals("Default", ignoreCase = true) ||
                pastasDoApk.any { it.equals(pasta, ignoreCase = true) }
            ) LEGACY else XTRA
    }
}
