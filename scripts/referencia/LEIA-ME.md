<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
# Temas de referência (fora do APK)

## `stuttgart-ntg55/`

O "Stuttgart NTG 5.5" do motor de temas (kWh/100km). Saiu do APK na Alpha 7.6: a única variação
do NTG 5.5 que o usuário escolhe como tema do painel é o **Stuttgart NTG 5.5 (clássico)**
(`app/src/main/assets/PreinstalledThemes/coffe3`). O `ThemeManager` apaga a cópia antiga do carro
(`RETIRED_THEME_FOLDERS`) e quem estava nele cai no clássico.

A pasta fica aqui só como referência dos testes: `instalador/scripts/test-theme-engine.mjs`
confere que o payload e o bloco de mídia da predefinição "Stuttgart NTG 5.5" do criador de temas
continuam iguais aos deste `app.html` (que tem à mão o vão da projeção que o motor ainda não
gera), e `scripts/test-temas-fluidez.mjs` passa as regras de fluidez nele.

Não é copiada para lugar nenhum e não viaja em build nenhum.
<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
