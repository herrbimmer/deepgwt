package ts.car.dcm.common.data

import android.os.Parcel
import android.os.Parcelable
import br.com.opengw.monitores.EscritorDeParcel
import br.com.opengw.monitores.EscritorDeParcelAndroid
import br.com.opengw.monitores.LeitorDeParcel
import br.com.opengw.monitores.LeitorDeParcelAndroid

/**
 * Um aparelho conhecido pelo gerenciador de conexões (DCM) da central.
 *
 * ESTE NOME É CONTRATO. O `Bundle` que a central devolve em `getDevices` carrega o parcelable pela
 * classe com este nome (`ts.car.dcm.common.data.ConnDevice`): ela precisa existir no APK com este
 * nome, ser `Parcelable`, ter o campo estático público `CREATOR` e ler os campos EXATAMENTE na
 * ordem da central (o `Bundle` não delimita cada valor: um campo errado desalinha o resto da lista).
 * A regra de preservação do R8 está em `app/proguard-rules.pro`.
 *
 * Os 31 campos são lidos sempre, na ordem da tabela D.3 de `extras-monitores.md`: a ordem das
 * propriedades abaixo É a ordem do `Parcel` (cada inicializador lê o seu campo, de cima para baixo).
 * O valor falso/ausente de cada campo não é interpretado aqui; só os que o app usa têm acesso fácil.
 */
class ConnDevice private constructor(leitor: LeitorDeParcel) : Parcelable {

    /** 1. Id do aparelho (uuid). */
    val uuid: String? = leitor.lerString()

    /** 2. Nome amigável. */
    val nomeAmigavel: String? = leitor.lerString()

    /** 3. Tipo do aparelho: 0 desconhecido, 1 Android, 2 iPhone, 3 iPod, 4 BT. */
    val tipoDoAparelho: Int = leitor.lerInt()

    /** 4. Aceito. */
    val aceito: Int = leitor.lerInt()

    /** 5. Estado do aparelho: 0 offline, 1 online, 2 removendo, 3 desabilitado, 4 visível. */
    val estadoDoAparelho: Int = leitor.lerInt()

    /** 6. Estado por tipo de conexão (1 USB, 2 BT, 3 Wi-Fi → estado). */
    val estadoPorTipoDeConexao: Map<Int, Int> = leitor.lerMapa()

    /** 7. Serial USB. */
    val serialUsb: String? = leitor.lerString()

    /** 8. Endereço BT. */
    val enderecoBt: String? = leitor.lerString()

    /** 9. Endereço Wi-Fi. */
    val enderecoWifi: String? = leitor.lerString()

    /** 10. IP Wi-Fi. */
    val ipWifi: String? = leitor.lerString()

    /** 11. Último "uptime". */
    val ultimoUptime: String? = leitor.lerString()

    /** 12. Último serviço. */
    val ultimoServico: Int = leitor.lerInt()

    /** 13. É o último. */
    val eoUltimo: Boolean = leitor.lerByte().toInt() != 0

    /** 14. Favorito. */
    val favorito: Boolean = leitor.lerByte().toInt() != 0

    /** 15. Capacidade. */
    val capacidade: Int = leitor.lerInt()

    /** 16. Capacidades disponíveis (bits). */
    val capacidadesDisponiveis: Int = leitor.lerInt()

    /** 17. Capacidade ativa. */
    val capacidadeAtiva: Int = leitor.lerInt()

    /** 18. Estado por capacidade ativa (capacidade → estado; 4 = conectado). */
    val estadoPorCapacidadeAtiva: Map<Int, Int> = leitor.lerMapa()

    /** 19. Capacidade "sempre". */
    val capacidadeSempre: Int = leitor.lerInt()

    /** 20. Estado por perfil BT (1 HFP, 2 PBAP, 4 A2DP, 8 AVRCP, 16 MAP, 32 PAN → estado). */
    val estadoPorPerfilBt: Map<Int, Int> = leitor.lerMapa()

    /** 21. Em modo dispositivo USB. */
    val emModoDispositivoUsb: Boolean = leitor.lerByte().toInt() != 0

    /** 22. Nó HID USB. */
    val noHidUsb: String? = leitor.lerString()

    /** 23. Nó iAP. */
    val noIap: String? = leitor.lerString()

    /** 24. Nó NCM USB. */
    val noNcmUsb: String? = leitor.lerString()

    /** 25. Pareado. */
    val pareado: Boolean = leitor.lerByte().toInt() != 0

    /** 26. Hora do pareamento BT. */
    val horaDoPareamentoBt: Long = leitor.lerLong()

    /** 27. Hora da última conexão CarPlay sem fio. */
    val horaDaUltimaConexaoCarPlaySemFio: Long = leitor.lerLong()

    /** 28. Hora da última conexão CarPlay USB. */
    val horaDaUltimaConexaoCarPlayUsb: Long = leitor.lerLong()

    /** 29. Porta USB OTG. */
    val portaUsbOtg: Boolean = leitor.lerByte().toInt() != 0

    /** 30. Máscara de mudanças. */
    val mascaraDeMudancas: Long = leitor.lerLong()

    /** 31. Suporta CarPlay por UUID. */
    val suportaCarPlayPorUuid: Boolean = leitor.lerByte().toInt() != 0

    /** O estado de uma capacidade (4 = conectado); 0 se ausente. */
    fun estadoDaCapacidade(capacidadeProcurada: Int): Int = estadoPorCapacidadeAtiva[capacidadeProcurada] ?: 0

    /** Escreve os 31 campos na mesma ordem da leitura (a central nunca lê o que o app escreve). */
    fun escrever(escritor: EscritorDeParcel) {
        escritor.escreverString(uuid)
        escritor.escreverString(nomeAmigavel)
        escritor.escreverInt(tipoDoAparelho)
        escritor.escreverInt(aceito)
        escritor.escreverInt(estadoDoAparelho)
        escritor.escreverMapa(estadoPorTipoDeConexao)
        escritor.escreverString(serialUsb)
        escritor.escreverString(enderecoBt)
        escritor.escreverString(enderecoWifi)
        escritor.escreverString(ipWifi)
        escritor.escreverString(ultimoUptime)
        escritor.escreverInt(ultimoServico)
        escritor.escreverByte(byteDe(eoUltimo))
        escritor.escreverByte(byteDe(favorito))
        escritor.escreverInt(capacidade)
        escritor.escreverInt(capacidadesDisponiveis)
        escritor.escreverInt(capacidadeAtiva)
        escritor.escreverMapa(estadoPorCapacidadeAtiva)
        escritor.escreverInt(capacidadeSempre)
        escritor.escreverMapa(estadoPorPerfilBt)
        escritor.escreverByte(byteDe(emModoDispositivoUsb))
        escritor.escreverString(noHidUsb)
        escritor.escreverString(noIap)
        escritor.escreverString(noNcmUsb)
        escritor.escreverByte(byteDe(pareado))
        escritor.escreverLong(horaDoPareamentoBt)
        escritor.escreverLong(horaDaUltimaConexaoCarPlaySemFio)
        escritor.escreverLong(horaDaUltimaConexaoCarPlayUsb)
        escritor.escreverByte(byteDe(portaUsbOtg))
        escritor.escreverLong(mascaraDeMudancas)
        escritor.escreverByte(byteDe(suportaCarPlayPorUuid))
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        escrever(EscritorDeParcelAndroid(dest))
    }

    override fun toString(): String =
        "ConnDevice(uuid=$uuid, nome=$nomeAmigavel, estado=$estadoDoAparelho, " +
            "disponiveis=$capacidadesDisponiveis, ativa=$capacidadeAtiva)"

    companion object {
        private fun byteDe(valor: Boolean): Byte = if (valor) 1 else 0

        /** Lê um aparelho de [leitor] (os 31 campos, sempre). */
        @JvmStatic
        fun ler(leitor: LeitorDeParcel): ConnDevice = ConnDevice(leitor)

        /** O que o `Bundle` da central procura pelo nome. */
        @JvmField
        val CREATOR: Parcelable.Creator<ConnDevice> = object : Parcelable.Creator<ConnDevice> {
            override fun createFromParcel(origem: Parcel): ConnDevice =
                ler(LeitorDeParcelAndroid(origem, ConnDevice::class.java.classLoader))

            override fun newArray(tamanho: Int): Array<ConnDevice?> = arrayOfNulls(tamanho)
        }
    }
}
