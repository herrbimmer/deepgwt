/**
 * O erro que o `invoke` devolveu é de rede (o carro não respondeu) e não recusa do carro? Numa falha
 * de rede o código de licença pendente fica guardado para a próxima tentativa; numa recusa ele é
 * descartado.
 */
export const ehErroDeRede = (e: unknown) => {
  const texto = String(e)
  // As duas recusas do instalador por estar fora do Wi-Fi do carro também são de rede: o código
  // não chegou ao carro, então a pendente não pode ser apagada.
  return (
    texto.startsWith('Falha na operação') ||
    texto.startsWith('Este computador não está no Wi-Fi do carro') ||
    /^O endereço .+ não é da rede do carro/.test(texto)
  )
}
