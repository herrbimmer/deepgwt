// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class InstaladorDeArquivoTest {

    private val registro = RegistroFalso()

    @Test
    fun `sh de verdade - instala por temporario e deixa o destino identico e executavel`() {
        val bin = pastaTemporaria("instalador-bin")
        assumeTrue(File("/usr/bin/shasum").exists() || File("/usr/bin/sha256sum").exists())
        // O aparelho tem `sha256sum`; aqui ele vem do shasum, quando faltar.
        comandoFalso(bin, "sha256sum", "exec shasum -a 256 \"\$@\"")
        val destino = File(pastaTemporaria("instalador-dst"), "hotrouter.sh")
        val conteudo = "#!/bin/sh\necho 'olá, mundo' \$HOME \"aspas\"\n".toByteArray()
        val executor = ExecutorReal(bin)
        assertTrue(InstaladorDeArquivo(executor, registro).instalar(destino.absolutePath, conteudo))
        assertEquals(conteudo.toList(), destino.readBytes().toList())
        assertTrue(destino.canExecute())
        assertFalse(File(destino.absolutePath + ".tmp").exists())
        assertTrue(registro.erros.isEmpty())
    }

    @Test
    fun `sh de verdade - reescrever um destino ja existente troca o arquivo por renome`() {
        val bin = pastaTemporaria("instalador-bin")
        assumeTrue(File("/usr/bin/shasum").exists() || File("/usr/bin/sha256sum").exists())
        comandoFalso(bin, "sha256sum", "exec shasum -a 256 \"\$@\"")
        val destino = File(pastaTemporaria("instalador-dst"), "x.sh")
        destino.writeText("antigo")
        val inode = java.nio.file.Files.getAttribute(destino.toPath(), "unix:ino")
        assertTrue(InstaladorDeArquivo(ExecutorReal(bin), registro).instalar(destino.absolutePath, "novo".toByteArray()))
        assertEquals("novo", destino.readText())
        assertTrue("o renome troca o inode", inode != java.nio.file.Files.getAttribute(destino.toPath(), "unix:ino"))
    }

    @Test
    fun `temporario que nao confere e apagado e o destino nao e tocado`() {
        val shell = ShellFalso { "deadbeef  x" }
        assertFalse(InstaladorDeArquivo(shell, registro).instalar("/data/local/tmp/a.sh", "x".toByteArray()))
        assertEquals("rm -f /data/local/tmp/a.sh.tmp", shell.comandos.last())
        assertTrue(shell.comandos.none { "mv -f" in it })
    }

    @Test
    fun `destino que nao confere depois de mover tambem falha`() {
        val sha = sha256Hex("x".toByteArray())
        val shell = ShellFalso { comando -> if ("mv -f" in comando) "deadbeef  x" else "$sha  x" }
        assertFalse(InstaladorDeArquivo(shell, registro).instalar("/data/local/tmp/a.sh", "x".toByteArray()))
        assertTrue(registro.erros.isNotEmpty())
    }

    @Test
    fun `sha256 em hexadecimal minusculo`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
