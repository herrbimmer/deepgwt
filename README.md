# OPEN GW

**OPEN GW** is an Android app for the Haval H6 GT (GWM) multimedia system — instrument-cluster themes, HUD, projection circle, bottom bar, 3D launcher, macros, driver profiles and tiered licensing — plus a companion desktop installer (Tauri/React) for managing the car from a Mac/Windows/Linux machine.

It includes a custom instrument-cluster theme engine, driver profiles, ambient-light BLE control, DVR management, a mobile-data/hotspot router and an experimental physical USB Stream Deck integration.

## Vision

The goal is to become, for GWM/Haval vehicles, what tools like **E-SYS**, **BimmerLink**, and **BimmerCode** are for BMW: a proper diagnostics-and-customization companion that goes beyond what the factory head unit exposes, run from a computer or phone alongside the car. `instalador/` is the desktop side of that ambition — settings sync, theme management, driver profiles, and file access today, with more coding/diagnostic-style features planned.

On the instrument-cluster side, the roadmap is a small library of refined, configurable cluster designs — drawing inspiration from **BMW Live Cockpit Professional**, **BMW 6WB**, **Volvo SENSUS**, and **Mercedes-Benz MBUX** — while staying within what the car's WebView-based cluster and real telemetry can actually drive.

## Disclaimer

This project is an unofficial, educational reverse-engineering initiative. It is **not affiliated with, endorsed by, or connected to Haval, Great Wall Motors (GWM), or any related entity**, nor with any third-party hardware vendor referenced in the code (e.g. Stream Deck clone manufacturers).

**English:** Independent product, developed through interoperability research. Users assume all risks. No warranty or liability — see [LICENSE](LICENSE) sections 4 and 5. Use may affect vehicle behaviour, may void manufacturer warranties, and may conflict with the manufacturer's terms of service — proceed at your own risk.

**Português:** Produto independente, desenvolvido a partir de pesquisa de interoperabilidade. Os usuários assumem todos os riscos. Sem garantia ou responsabilidade — veja as seções 4 e 5 do [LICENSE](LICENSE). O uso pode afetar o comportamento do veículo, anular garantias do fabricante e conflitar com os termos de serviço dele — prossiga por sua conta e risco.

## Structure

- `app/` — the Android app installed on the car's head unit (Kotlin/Java, Jetpack Compose, WebView-based instrument cluster).
- `instalador/` — the desktop companion app (Tauri + React + TypeScript).
- `third_party/` — sources and licences of third-party components shipped in the app.
- `docs/`, `scripts/`, `tools/`, `knowledge/` — internal documentation and developer tooling.

## Licensing & Privacy

### Licensing

**OPEN GW is proprietary, closed-source software.** It is licensed to users under the terms in [LICENSE](LICENSE); all rights not expressly granted there are reserved by the copyright holder. Unauthorized reproduction or redistribution is not permitted.

**It incorporates third-party components, and each of those stays under its own license** (Apache-2.0 libraries from AOSP/Google/Apache/JetBrains, MIT libraries from Rikka, fonts under the SIL Open Font License 1.1, among others — see the documents below). Those licenses grant rights this product's license does not, and nothing here overrides or revokes them.

| Document | Contents |
|---|---|
| [LICENSE](LICENSE) | The proprietary license covering our own code |
| [NOTICE](NOTICE) | Third-party notices that ship with every copy |
| [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) | Full third-party inventory and license texts |
| [COPYRIGHT.md](COPYRIGHT.md) | Ownership breakdown — ours vs. third parties |
| [LICENSING.md](LICENSING.md) | How the three licensing layers fit together |

**Not affiliated with the manufacturer.** This is an independent product. It is **not affiliated with, endorsed by, or connected to Haval, Great Wall Motors (GWM), or any related entity**, nor with any third-party hardware or software vendor referenced in the code. All trademarks belong to their respective owners and are used only to identify the systems this product interoperates with.

### Privacy

The product runs locally on the car's head unit and on your own computer. It requires no account and collects no background analytics or telemetry. Data leaves the device only in these cases:

- **Problem reports (user-initiated).** If you choose to send a problem report, the app uploads application logs together with a random session identifier and an installation identifier to our backend, solely to diagnose the reported problem.
- **Update check.** None by default: no update feed is configured in release builds, and the app queries nothing to look for updates. Updates are delivered by the desktop installer.
- **Optional online features you trigger.** QR-code generation (api.qrserver.com) sends only the local URL being encoded, and only when you use that feature.
- **Local network only.** The desktop installer talks to the car over your own local network. Settings, themes, driver profiles and vehicle data stay between your computer and your car.

Vehicle data read by the app — speed, temperatures, diagnostic values, coding keys — is processed on the device and is not transmitted, except where it appears in a problem report you choose to send.

*(PT) O OPEN GW é software **proprietário e de código fechado** — veja [LICENSE](LICENSE). Ele incorpora componentes de terceiros, cada um sob a sua própria licença (veja [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) e [NOTICE](NOTICE)). **Não é afiliado, endossado ou conectado à Haval, à Great Wall Motors (GWM) ou a qualquer entidade relacionada.** Privacidade: o produto roda localmente, não exige conta e não coleta telemetria em segundo plano; dados só saem do aparelho em relatórios de problema enviados por você (logs + identificadores de sessão/instalação), na checagem de atualização, e em funções online que você mesmo aciona. Dados do veículo são processados no próprio aparelho.*

## Legal disclaimer / limitation of liability

This software is provided "as is", without warranty of any kind, express or implied, including but not limited to warranties of merchantability, fitness for a particular purpose, and non-infringement. The developers and contributors are not liable for any direct, indirect, incidental, special, or consequential damages arising from the use of this software, including damage to vehicle systems, hardware, or data. **Use at your own risk** — back up your data, test in a safe environment, and understand that modifying your car's head unit may violate the manufacturer's terms of service or warranty.

*(PT) Este software é fornecido "como está", sem garantias de qualquer tipo. Os desenvolvedores e contribuidores não se responsabilizam por danos diretos, indiretos, incidentais ou consequenciais decorrentes do uso deste software, incluindo danos a sistemas do veículo, hardware ou dados. **Uso por sua conta e risco** — faça backup, teste em ambiente seguro, e esteja ciente de que modificar a central do seu carro pode violar os termos do fabricante.*
