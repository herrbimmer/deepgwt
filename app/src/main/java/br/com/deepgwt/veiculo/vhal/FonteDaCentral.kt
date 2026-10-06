// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.vhal

import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * A ligação real com a central: o serviço de sistema `car_service`, com as chamadas saindo pelo
 * Shizuku, e a leitura de propriedades da camada VHAL. As classes (`ICar`, `ICarProperty`,
 * `CarPropertyValue`) são ocultas, então tudo é por reflexão, com o desvio de APIs ocultas como
 * segunda via. Sem Shizuku autorizado a transação falha, e o leitor trata como falha comum.
 */
internal object FonteDaCentral {

    private const val SERVICO_DO_CARRO = "car_service"
    private const val SERVICO_DE_PROPRIEDADES = "property"

    /** Liga na ordem: car_service → ICar → serviço "property" → ICarProperty. Falha = `null` ou exceção. */
    fun abrir(): FonteDePropriedades? {
        val bruto = servicoDoSistema(SERVICO_DO_CARRO) ?: return null
        if (!bruto.pingBinder()) return null

        val classeICar = Class.forName("android.car.ICar")
        val icar = Class.forName("android.car.ICar\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(bruto)) ?: return null

        val propriedades = chamar(classeICar, icar, "getCarService", arrayOf(String::class.java), SERVICO_DE_PROPRIEDADES) as? IBinder
            ?: return null
        if (!propriedades.pingBinder()) return null

        val classeIProp = Class.forName("android.car.hardware.property.ICarProperty")
        val iprop = Class.forName("android.car.hardware.property.ICarProperty\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(propriedades)) ?: return null

        val classeValor = Class.forName("android.car.hardware.CarPropertyValue")
        return FonteDePropriedades { id, area ->
            val valor = chamar(classeIProp, iprop, "getProperty", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), id, area)
            if (valor == null) null else chamar(classeValor, valor, "getValue", emptyArray())
        }
    }

    /** `android.os.ServiceManager.getService(nome)`; `null` se o nome não existe. */
    private fun servicoDoSistema(nome: String): IBinder? {
        val classe = Class.forName("android.os.ServiceManager")
        return chamar(classe, null, "getService", arrayOf(String::class.java), nome) as IBinder?
    }

    /**
     * Chama [nome] em [classe] (a interface ou classe pública, não a implementação). Se a API
     * oculta bloquear a busca do método, tenta o desvio. A exceção de dentro do método sai sem o
     * embrulho de reflexão.
     */
    private fun chamar(classe: Class<*>, alvo: Any?, nome: String, tipos: Array<Class<*>>, vararg args: Any?): Any? {
        val metodo: Method = try {
            classe.getMethod(nome, *tipos)
        } catch (e: NoSuchMethodException) {
            return try {
                HiddenApiBypass.invoke(classe, alvo, nome, *args)
            } catch (f: InvocationTargetException) {
                throw f.cause ?: f
            }
        }
        return try {
            metodo.invoke(alvo, *args)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
