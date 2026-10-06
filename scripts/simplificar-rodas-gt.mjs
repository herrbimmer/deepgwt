// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Simplifica SÓ as malhas de roda do haval-h6-gt-lite.glb (aros "Circle.*" e pneus "hub_lb.*").
// Nós, nomes, materiais, hierarquia e texturas KTX2 ficam como estão.
// Uso (Alpha 8.9, o que gerou o haval-h6-gt-lite.glb ?rev=3):
//   RATIO_ARO=0.5 ERRO_ARO=0.0003 node scripts/simplificar-rodas-gt.mjs <original.glb> <saida.glb> 0.25 0.001
// Precisa de @gltf-transform/{core,extensions,functions} 4.x, meshoptimizer e draco3dgltf. GLTF_NODE_MODULES
// aponta para a pasta node_modules que os tem (o padrão é o cache do `npx @gltf-transform/cli` desta máquina).
// Rodar sobre o ORIGINAL, nunca sobre a saída: simplificar de novo acumula erro. O original (rev=2) está em
// _QUARENTENA_LAUNCHER_2026-09-27/assets/www/assets/haval-h6-gt-lite.rev2-original.glb.
const D = process.env.GLTF_NODE_MODULES || process.env.HOME + '/.npm/_npx/d659fe5fabadba55/node_modules'
const { NodeIO } = await import(D + '/@gltf-transform/core/dist/index.js')
const { ALL_EXTENSIONS, KHRDracoMeshCompression } = await import(D + '/@gltf-transform/extensions/dist/index.js')
const { weldPrimitive, simplifyPrimitive } = await import(D + '/@gltf-transform/functions/dist/index.js')
const { MeshoptSimplifier } = await import(D + '/meshoptimizer/index.js')
const draco3d = (await import(D + '/draco3dgltf/draco3dgltf.js')).default

const [entrada, saida] = process.argv.slice(2)
const ratio = Number(process.argv[4] || 0.25)
const erro = Number(process.argv[5] || 0.001)

await MeshoptSimplifier.ready
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({
  'draco3d.decoder': await draco3d.createDecoderModule(),
  'draco3d.encoder': await draco3d.createEncoderModule()
})
const doc = await io.read(entrada)

const ALVO = /^(Circle|hub_lb)\.\d+$/
const MINIMO = 15000
let antes = 0, depois = 0
for (const mesh of doc.getRoot().listMeshes()) {
  if (!ALVO.test(mesh.getName())) continue
  for (const prim of mesh.listPrimitives()) {
    const idx = prim.getIndices()
    const tris = (idx ? idx.getCount() : prim.getAttribute('POSITION').getCount()) / 3
    if (tris < MINIMO) continue
    weldPrimitive(prim)
    // O aro é preto brilhante: o reflexo denuncia normal interpolada em triângulo grande, então ele
    // tem teto de erro próprio (RATIO_ARO/ERRO_ARO). O pneu é fosco e aguenta o padrão.
    const aro = /^Circle/.test(mesh.getName())
    const r = aro && process.env.RATIO_ARO ? Number(process.env.RATIO_ARO) : ratio
    const e = aro && process.env.ERRO_ARO ? Number(process.env.ERRO_ARO) : erro
    simplifyPrimitive(prim, { simplifier: MeshoptSimplifier, ratio: r, error: e, lockBorder: false })
    const novo = prim.getIndices().getCount() / 3
    antes += tris; depois += novo
    console.log(`${mesh.getName()}: ${tris} -> ${novo}`)
  }
}

// Recomprimir com mais bits que o padrão para não empilhar erro de quantização sobre o original.
const draco = doc.getRoot().listExtensionsUsed().find((e) => e.extensionName === KHRDracoMeshCompression.EXTENSION_NAME)
  || doc.createExtension(KHRDracoMeshCompression)
draco.setRequired(true).setEncoderOptions({
  method: KHRDracoMeshCompression.EncoderMethod.EDGEBREAKER,
  encodeSpeed: 5,
  decodeSpeed: 5,
  quantizationBits: { POSITION: 16, NORMAL: 12, TEXCOORD: 14, COLOR: 8, GENERIC: 12 }
})
await io.write(saida, doc)
console.log(`rodas: ${antes} -> ${depois} triângulos por instância de malha`)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
