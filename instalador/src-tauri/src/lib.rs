// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
mod telnet;

use std::time::Duration;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::path::PathBuf;
use base64::{engine::general_purpose::STANDARD as BASE64_STANDARD, Engine as _};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::Mutex;
use tauri::path::BaseDirectory;
use tauri::{AppHandle, Emitter, Manager};
use telnet::{
    collect_until_prefix, abrir_sessao_telnet, sessao_aberta, enviar_comando_com_eventos, wait_for_prefix,
};

#[derive(Clone)]
pub struct EstadoDaConexao {
    sessao: Arc<Mutex<Option<TcpStream>>>,
    // Flag de "continue rodando" do servidor HTTP embarcado (thread nativa, sem
    // subprocesso — necessário porque o Android não tem python3 disponível).
    http_server: Arc<Mutex<Option<Arc<AtomicBool>>>>,
    // Task async do loop de mirror de tela (Opções de Desenvolvedor) — abortada em
    // `stop_mirror` ou antes de iniciar um novo mirror, pra nunca ter dois streams
    // concorrentes brigando pela mesma conexão do carro.
    mirror_task: Arc<Mutex<Option<tokio::task::JoinHandle<()>>>>,
}

const LOCAL_INSTALL_MARKER: &str = "LOCAL_INSTALL_RESULT:";
// Nome fixo usado para o APK durante o transporte (staging local, servidor HTTP e
// caminho remoto), para não ter que lidar com espaços/caracteres especiais no nome
// original do arquivo escolhido pelo usuário dentro de comandos de shell.
const STAGED_APK_NAME: &str = "payload.apk";
const STAGED_SHIZUKU_APK_NAME: &str = "shizuku.apk";
const STAGED_SHIZUKU_STARTER_NAME: &str = "shizuku_starter";
// Nosso fork do Shizuku (mesmo binário `shizuku_starter` oficial, apenas o
// pacote do manager recompilado) usa este applicationId em vez do
// "moe.shizuku.privileged.api" original, para escapar do bloqueio por nome de
// pacote do PackageManager deste OEM (ver comentário em install_and_start_shizuku).
// applicationId != namespace de propósito (ver nota em app/build.gradle.kts): a central
// (beantechs) recusa `pm install` de qualquer applicationId que ela nunca viu
// ("beantechs disallow apk <pkg> to be installed!") — confirmado 04/10/2026 via telnet root
// no carro: só br.com.redesurftank.havalshisuku é uma identidade já conhecida por ela.
const DEEPGWT_PACKAGE: &str = "br.com.redesurftank.havalshisuku";
// Permissão "dangerous" (não "normal") que o DeepGWT declara via <uses-permission>
// para o Shizuku liberar sua API — ver comentário em install_and_start_shizuku.
const SHIZUKU_API_PERMISSION: &str = "moe.shizuku.manager.permission.API_V23";
// Package do fork renomeado do Shizuku (ver comentário em install_and_start_shizuku)
// usado para resolver, via `pm path`, o caminho REAL de instalação do apk depois
// do `pm install` — necessário porque o `shizuku_starter` nativo calcula o diretório
// de libs nativas (.so) como dirname(--apk=)/lib/<abi>, e só o caminho instalado em
// /data/app/... tem essa pasta populada pelo pm install; o arquivo avulso que
// empurramos para /data/local/tmp não tem.
const SHIZUKU_PACKAGE: &str = "br.com.deepgwt.shizuku";

#[derive(thiserror::Error, Debug)]
enum ErroDoInstalador {
    // Alpha 9.4: o instalador só fala com o carro pelo Wi-Fi do próprio carro (192.168.33.x).
    #[error("Este computador está no Wi-Fi do carro, mas a multimídia não respondeu (procurado em 192.168.33.x). Confirme que o carro está ligado e tente de novo.")]
    CarNotFound,
    #[error("Este computador não está no Wi-Fi do carro. Ligue o hotspot da central multimídia, conecte este computador à rede dele e tente de novo.")]
    ForaDoWifiDoCarro,
    #[error("O endereço {0} não é da rede do carro. Com o computador no Wi-Fi do carro, a central fica em 192.168.33.x — use \"Detectar automaticamente\".")]
    IpForaDoCarro(String),
    #[error("A sessão telnet com a multimídia não está aberta. Refaça a conexão.")]
    SemSessao,
    #[error("Já existe uma sessão telnet aberta com a multimídia.")]
    SessaoJaAberta,
    #[error("A multimídia não respondeu dentro do prazo (timeout).")]
    SemResposta,
    #[error("Arquivo APK não encontrado ou inválido: {0}")]
    ApkNotFound(String),
    #[error("Não foi possível determinar o IP local desta máquina na rede do Haval")]
    NoLocalIp,
    #[error("Não foi possível encontrar uma porta livre para o servidor HTTP local")]
    NoFreePort,
    #[error("Falha ao iniciar o servidor HTTP local")]
    HttpServerFailed,
    #[error("Falha ao preparar o APK para envio: {0}")]
    CopyFailed(String),
    #[error("Não foi possível falar com o DeepGWT no carro (porta {port}): {reason}. Confirme que o DeepGWT já está instalado e aberto no carro.")]
    GwLinkUnreachable { port: u16, reason: String },
    #[error("Instalação falhou no dispositivo (pm install saiu com código {0})")]
    InstallFailed(String),
    // Variante separada da acima de propósito: esta carrega o motivo já redigido, incluindo a
    // linha que o próprio `pm` imprimiu. A outra continua existindo para os fluxos que só têm
    // um código de saída na mão.
    #[error("{0}")]
    InstallRejected(String),
    #[error("Falha de entrada/saída: {0}")]
    Io(#[from] std::io::Error),
    #[error("Falha na operação de Opções de Desenvolvedor: {0}")]
    RemoteControlError(String),
    // O carro respondeu e recusou, com a frase dele (licença, Alpha 8.1): vai como está.
    #[error("{0}")]
    RecusadoPeloCarro(String),
}

// O frontend recebe o erro como a frase pronta (a mensagem do `#[error]`).
impl serde::Serialize for ErroDoInstalador {
    fn serialize<Saida: serde::Serializer>(&self, saida: Saida) -> Result<Saida::Ok, Saida::Error> {
        saida.collect_str(self)
    }
}

// Porta do telnet da multimidia (busybox telnetd).
const HEAD_UNIT_TELNET_PORT: u16 = 23;
// Sub-rede do hotspot da multimídia (ex.: 192.168.33.1 como gateway).
//
// Alpha 9.4: é a ÚNICA rede em que o instalador fala com o carro. Até a 9.3 ele também procurava
// a central na rede de casa (carro como cliente do mesmo Wi-Fi) e aceitava um 192.168.33.x
// roteado a partir de outra rede; as duas formas saíram a pedido do dono. Toda conversa com o
// carro — telnet (instalar/reparar) e HTTP 8766 (ajustes, arquivos, espelho) — passa por
// [exigir_wifi_do_carro] e, com IP explícito, por [exigir_ip_do_carro].
const HAVAL_SUBNET_PREFIX: &str = "192.168.33.";

/// O IP é da faixa do hotspot do carro?
fn e_ip_do_carro(ip: &str) -> bool {
    let ip = ip.trim();
    ip.starts_with(HAVAL_SUBNET_PREFIX) && ip.parse::<std::net::Ipv4Addr>().is_ok()
}

/// Algum dos endereços desta máquina está na faixa do hotspot do carro?
fn tem_endereco_do_carro(enderecos: &[std::net::Ipv4Addr]) -> bool {
    enderecos.iter().any(|a| e_ip_do_carro(&a.to_string()))
}

/// Esta máquina está no Wi-Fi do carro agora? Pergunta às interfaces a cada chamada: trocar de
/// rede no meio do uso tem de valer na hora, não depois de reabrir o app.
fn exigir_wifi_do_carro() -> Result<(), ErroDoInstalador> {
    let enderecos: Vec<std::net::Ipv4Addr> = default_net::get_interfaces()
        .iter()
        .flat_map(|itf| itf.ipv4.iter().map(|n| n.addr))
        .collect();
    if tem_endereco_do_carro(&enderecos) {
        Ok(())
    } else {
        Err(ErroDoInstalador::ForaDoWifiDoCarro)
    }
}

/// Para as chamadas com IP explícito: a máquina no Wi-Fi do carro E o IP na faixa dele. Um IP
/// antigo da rede de casa guardado nas preferências para aqui, com a frase certa.
fn exigir_ip_do_carro(car_ip: &str) -> Result<(), ErroDoInstalador> {
    exigir_wifi_do_carro()?;
    if e_ip_do_carro(car_ip) {
        Ok(())
    } else {
        Err(ErroDoInstalador::IpForaDoCarro(car_ip.trim().to_string()))
    }
}

// Tenta abrir o telnet em `ip:23` com timeout curto; true = respondeu.
async fn probe_telnet(ip: &str) -> bool {
    let addr = format!("{}:{}", ip, HEAD_UNIT_TELNET_PORT);
    matches!(
        tokio::time::timeout(Duration::from_millis(600), TcpStream::connect(&addr)).await,
        Ok(Ok(_))
    )
}

// Último IP em que a multimídia respondeu, guardado enquanto o processo viver.
//
// Sem isto, toda tela que checa conexão paga a varredura completa de novo. Com DHCP da casa o
// endereço muda a cada reboot do carro, então o cache é verificado antes de ser usado — nunca
// devolvido às cegas.
fn last_car_ip_cell() -> &'static std::sync::Mutex<Option<String>> {
    static CELL: std::sync::OnceLock<std::sync::Mutex<Option<String>>> = std::sync::OnceLock::new();
    CELL.get_or_init(|| std::sync::Mutex::new(None))
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

fn remember_car_ip(ip: &str) -> String {
    if let Ok(mut guard) = last_car_ip_cell().lock() {
        *guard = Some(ip.to_string());
    }
    ip.to_string()
}

// Varre uma /24 inteira em paralelo procurando quem responde telnet.
//
// O critério é "porta 23 aberta". Desde a 9.4 só a faixa do hotspot é varrida, então quem
// responde é a central (é a rede dela).
async fn scan_slash24(prefix: &str, skip: &[String]) -> Option<String> {
    let mut set = tokio::task::JoinSet::new();
    for host in 1u16..=254 {
        let ip = format!("{}{}", prefix, host);
        if skip.contains(&ip) {
            continue;
        }
        set.spawn(async move { if probe_telnet(&ip).await { Some(ip) } else { None } });
    }
    let mut found: Option<String> = None;
    while let Some(res) = set.join_next().await {
        if let Ok(Some(ip)) = res {
            found = Some(ip);
            break;
        }
    }
    set.abort_all();
    found
}

// Descobre o IP da multimidia testando o telnet (porta 23), nesta ordem:
//   0) exige esta máquina no Wi-Fi do carro (Alpha 9.4) — fora dele, nem procura;
//   1) o último IP que respondeu nesta execução;
//   2) o gateway padrao, se ja estiver em 192.168.33.x (modo hotspot direto);
//   3) os IPs conhecidos .1 (gateway do hotspot) e .82;
//   4) varredura concorrente do resto de 192.168.33.0/24.
// Até a 9.3 havia um passo 5, a varredura das /24 da rede de casa (carro como cliente do mesmo
// Wi-Fi). Saiu na 9.4 junto com toda conexão fora do Wi-Fi do carro.
async fn discover_car_ip() -> Result<String, ErroDoInstalador> {
    exigir_wifi_do_carro()?;
    let mut priority: Vec<String> = Vec::new();
    if let Some(ip) = last_car_ip_cell().lock().ok().and_then(|g| g.clone()) {
        if e_ip_do_carro(&ip) {
            priority.push(ip);
        }
    }
    if let Ok(gw) = default_net::get_default_gateway() {
        let gw = gw.ip_addr.to_string();
        if gw.starts_with(HAVAL_SUBNET_PREFIX) && !priority.contains(&gw) {
            priority.push(gw);
        }
    }
    for host in ["82", "1"] {
        let ip = format!("{}{}", HAVAL_SUBNET_PREFIX, host);
        if !priority.contains(&ip) {
            priority.push(ip);
        }
    }
    for ip in &priority {
        if probe_telnet(ip).await {
            return Ok(remember_car_ip(ip));
        }
    }

    if let Some(ip) = scan_slash24(HAVAL_SUBNET_PREFIX, &priority).await {
        return Ok(remember_car_ip(&ip));
    }

    Err(ErroDoInstalador::CarNotFound)
}

// IP da multimídia no Wi-Fi do carro (não o gateway padrão da máquina), via discover_car_ip().
#[tauri::command]
async fn endereco_do_carro() -> Result<String, ErroDoInstalador> {
    discover_car_ip().await
}

// Este computador está no hotspot da multimídia?
//
// Responde "este computador está no hotspot da multimídia e ela responde". Entre a 6.x e a 9.3 a
// pergunta tinha virado só "a multimídia está alcançável" (valia a rede de casa); na 9.4 voltou a
// ser o que o nome diz.
#[tauri::command]
async fn conferir_wifi_do_carro() -> Result<(), ErroDoInstalador> {
    discover_car_ip().await.map(|_| ())
}

// Descobre o endereço IPv4 desta máquina NA MESMA REDE do carro.
//
// Antes isto devolvia o IP da interface padrão, que é outra pergunta. Com duas interfaces
// ativas — Wi-Fi no carro mais Ethernet de casa, ou uma VPN de pé — a interface padrão pode ser
// justamente a que o carro não alcança. A URL entregue ao `curl` do veículo aponta então para um
// endereço que não responde, o servidor HTTP sobe para ninguém, e o erro só aparece depois, no
// `pm install`, com cara de APK defeituoso.
fn mesma_sub_rede(
    local: std::net::Ipv4Addr,
    mascara: std::net::Ipv4Addr,
    alvo: std::net::Ipv4Addr,
) -> bool {
    let m = u32::from(mascara);
    m != 0 && (u32::from(local) & m) == (u32::from(alvo) & m)
}

fn find_local_ipv4_for(car_ip: &str) -> Result<String, ErroDoInstalador> {
    let alvo: std::net::Ipv4Addr = car_ip.parse().map_err(|_| ErroDoInstalador::NoLocalIp)?;
    for itf in default_net::get_interfaces() {
        for net in &itf.ipv4 {
            if mesma_sub_rede(net.addr, net.netmask, alvo) {
                return Ok(net.addr.to_string());
            }
        }
    }
    // Até a 9.3, sem interface na mesma sub-rede (carro atrás de um roteador) valia o palpite da
    // interface padrão. Desde a 9.4 o carro só é alcançado pelo hotspot dele, então a interface
    // certa sempre existe — faltar é erro, não palpite.
    Err(ErroDoInstalador::NoLocalIp)
}

// Procura uma porta TCP livre a partir de `start`, tentando `tries` portas sequenciais.
fn find_free_port(start: u16, tries: u16) -> Result<u16, ErroDoInstalador> {
    for port in start..start.saturating_add(tries) {
        if std::net::TcpListener::bind(("0.0.0.0", port)).is_ok() {
            return Ok(port);
        }
    }
    Err(ErroDoInstalador::NoFreePort)
}

// Sobe um servidor HTTP embarcado (thread nativa, Rust puro) que serve um único
// arquivo estático. Substitui o antigo `python3 -m http.server`: no Android não há
// python3 disponível, e mesmo no desktop isso remove a dependência de um binário
// externo no PATH do usuário. Retorna uma flag que, ao ser zerada, encerra a thread.
fn start_http_server(dir: PathBuf, port: u16) -> Result<Arc<AtomicBool>, ErroDoInstalador> {
    let server = tiny_http::Server::http(("0.0.0.0", port)).map_err(|_| ErroDoInstalador::HttpServerFailed)?;
    let running = Arc::new(AtomicBool::new(true));
    let running_thread = running.clone();

    std::thread::spawn(move || {
        while running_thread.load(Ordering::SeqCst) {
            match server.recv_timeout(Duration::from_millis(500)) {
                Ok(Some(request)) => {
                    let requested = request.url().trim_start_matches('/').to_string();
                    // Diretório de staging é isolado e só contém os arquivos que nós mesmos
                    // copiamos para lá, mas ainda assim recusa qualquer nome com barra ou ".."
                    // para não virar um path traversal para fora dele.
                    let is_safe_name =
                        !requested.is_empty() && !requested.contains('/') && !requested.contains("..");
                    if is_safe_name {
                        match std::fs::read(dir.join(&requested)) {
                            Ok(data) => {
                                let header = tiny_http::Header::from_bytes(
                                    &b"Content-Type"[..],
                                    &b"application/octet-stream"[..],
                                )
                                .expect("header estático válido");
                                let response = tiny_http::Response::from_data(data).with_header(header);
                                let _ = request.respond(response);
                            }
                            Err(_) => {
                                let _ = request.respond(tiny_http::Response::empty(404));
                            }
                        }
                    } else {
                        let _ = request.respond(tiny_http::Response::empty(404));
                    }
                }
                Ok(None) => continue, // timeout do recv, apenas reavalia `running`
                Err(_) => break,
            }
        }
    });

    Ok(running)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Encerra o servidor HTTP local iniciado para servir o APK, se houver um rodando.
async fn stop_http_server(state: &tauri::State<'_, EstadoDaConexao>) {
    let mut guard = state.http_server.lock().await;
    if let Some(running) = guard.take() {
        running.store(false, Ordering::SeqCst);
    }
}

// Copia um arquivo local para um diretório de staging isolado com um nome fixo
// (não expõe a pasta inteira do usuário, ex.: Downloads, no servidor HTTP local, e
// evita ter que lidar com espaços/acentos no nome original em comandos remotos),
// sobe o servidor HTTP embarcado apontando para ele e devolve a URL que o
// dispositivo pode usar para baixá-lo via `curl`.
async fn stage_file_and_serve(
    state: &tauri::State<'_, EstadoDaConexao>,
    car_ip: &str,
    source_path: &PathBuf,
    staged_name: &str,
) -> Result<String, ErroDoInstalador> {
    let urls = stage_files_and_serve(state, car_ip, &[(source_path, staged_name)]).await?;
    Ok(urls.into_iter().next().expect("um arquivo foi pedido, uma URL volta"))
}

// Mesma ideia de `stage_file_and_serve`, mas para vários arquivos de uma vez, todos
// servidos pelo mesmo servidor HTTP embarcado (mesma porta) — necessário quando um
// único script remoto precisa baixar mais de um arquivo (ex.: o APK do Shizuku +
// o binário `shizuku_starter`), já que subir um novo `stage_file_and_serve` por
// arquivo derrubaria o servidor do arquivo anterior antes do dispositivo baixá-lo.
async fn stage_files_and_serve(
    state: &tauri::State<'_, EstadoDaConexao>,
    car_ip: &str,
    files: &[(&PathBuf, &str)],
) -> Result<Vec<String>, ErroDoInstalador> {
    let staging_dir = std::env::temp_dir().join("instalador-local-apk");
    std::fs::create_dir_all(&staging_dir).map_err(|e| ErroDoInstalador::CopyFailed(e.to_string()))?;

    for (source_path, staged_name) in files {
        if !source_path.is_file() {
            return Err(ErroDoInstalador::ApkNotFound(source_path.to_string_lossy().to_string()));
        }
        let staged_path = staging_dir.join(staged_name);
        std::fs::copy(source_path, &staged_path).map_err(|e| ErroDoInstalador::CopyFailed(e.to_string()))?;
    }

    let local_ip = find_local_ipv4_for(car_ip)?;
    let port = find_free_port(8765, 50)?;

    stop_http_server(state).await;
    let running = start_http_server(staging_dir.clone(), port)?;
    *state.http_server.lock().await = Some(running);

    Ok(files
        .iter()
        .map(|(_, staged_name)| format!("http://{}:{}/{}", local_ip, port, staged_name))
        .collect())
}

// Traduz a linha que o `pm` imprimiu num motivo que sirva para decidir o que fazer.
//
// Sem isto o app mostrava só o código de saída. O código não distingue "o APK exige um Android
// mais novo" de "já existe esse pacote assinado com outra chave", e são conclusões opostas: uma
// é o APK, a outra é o carro.
fn dica_do_pm(msg: &str) -> Option<&'static str> {
    let m = msg.to_uppercase();
    if m.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || m.contains("INCONSISTENT_CERTIFICATES") {
        Some("Já existe um app com esse mesmo nome de pacote instalado, assinado com outra chave. Desinstale o app existente antes (a desinstalação apaga os dados dele) e instale de novo.")
    } else if m.contains("INSTALL_FAILED_VERSION_DOWNGRADE") {
        Some("O APK tem versionCode menor que o já instalado. O downgrade já é permitido nesta chamada; se persistir, desinstale o app existente antes.")
    } else if m.contains("INSTALL_FAILED_OLDER_SDK") {
        Some("O APK exige um Android mais novo que o da central, que é Android 9 (API 28). Rebaixe o minSdkVersion do APK.")
    } else if m.contains("INSTALL_FAILED_NO_MATCHING_ABIS") {
        Some("O APK traz biblioteca nativa, mas nenhuma para arm64-v8a, que é a arquitetura da central.")
    } else if m.contains("INSTALL_FAILED_TEST_ONLY") {
        Some("O APK está marcado como testOnly, típico de build de Run do Android Studio. A permissão para instalar assim já é enviada; se persistir, gere um APK de release.")
    } else if m.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") {
        Some("Falta espaço na central para instalar o APK.")
    } else if m.contains("INSTALL_PARSE_FAILED") {
        Some("A central não conseguiu ler o arquivo como APK. Confira se não é um .aab ou um conjunto de APKs separados (split).")
    } else {
        None
    }
}

// Espera, interpreta e relata cada fase do script remoto de instalação.
//
// Duas esperas em vez de uma porque as fases têm ordens de grandeza diferentes de tempo e
// motivos diferentes de falha: o download do APK pela rede do carro e o `pm install`.
async fn run_apk_install_phases(
    app: &AppHandle,
    state: &tauri::State<'_, EstadoDaConexao>,
    apk_url: &str,
) -> Result<(), ErroDoInstalador> {
    let marcador = format!("{}DOWNLOAD:", LOCAL_INSTALL_MARKER);
    let linha = wait_for_prefix(&marcador, 300, state, app).await?;
    let estado = linha.trim_start_matches(&marcador).trim().to_string();
    if estado != "OK" {
        let detalhe = if estado.starts_with("FAIL:") {
            format!("o curl da central terminou com código {}", estado.trim_start_matches("FAIL:"))
        } else {
            "o arquivo chegou vazio".to_string()
        };
        return Err(ErroDoInstalador::InstallRejected(format!(
            "A central não conseguiu baixar o APK deste computador: {}. Ela tentou {} — confirme que o carro alcança esse endereço e que nenhum firewall bloqueia a porta.",
            detalhe, apk_url,
        )));
    }
    let _ = app.emit("telnet-output", "📥 APK recebido pela central.");

    let marcador = format!("{}PM:", LOCAL_INSTALL_MARKER);
    let linha = wait_for_prefix(&marcador, 300, state, app).await?;
    let resto = linha.trim_start_matches(&marcador).trim().to_string();
    let (codigo, msg) = match resto.split_once(':') {
        Some((c, m)) => (c.trim().to_string(), m.trim().to_string()),
        None => (resto.clone(), String::new()),
    };

    // O `pm` imprime "Failure [...]" e devolve 1 nesta versão do Android, mas a checagem olha as
    // duas coisas: um código 0 com "Failure" na saída seria um sucesso falso, e um sucesso falso
    // manda o usuário procurar defeito num app que nunca foi instalado.
    let falhou = codigo != "0" || msg.to_uppercase().contains("FAILURE");
    if falhou {
        let mut texto = if msg.is_empty() {
            format!("A central recusou a instalação (pm install saiu com código {}).", codigo)
        } else {
            format!("A central recusou a instalação: {}", msg)
        };
        if let Some(dica) = dica_do_pm(&msg) {
            texto.push(' ');
            texto.push_str(dica);
        }
        return Err(ErroDoInstalador::InstallRejected(texto));
    }

    Ok(())
}

// Corpo compartilhado do fluxo de instalação a partir de um APK já presente no disco
// (seja um arquivo escolhido pelo usuário, seja um recurso embarcado no próprio app):
// copia para um diretório de staging com nome fixo, sobe o servidor HTTP embarcado,
// conecta ao telnet e manda o dispositivo baixar + instalar.
//
// O script remoto confere cada passo e relata o resultado de cada fase com um marcador próprio.
// A versão anterior não conferia nada: um `curl` que falhasse seguia direto para o `pm install`
// de um arquivo ausente. Hoje cada fase
// termina com um marcador próprio, e o instalador só segue se o anterior deu certo.
async fn install_apk_from_path(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
    source_path: PathBuf,
    display_name: String,
) -> Result<(), ErroDoInstalador> {
    let car_ip = discover_car_ip().await?;

    let apk_url = stage_file_and_serve(&state, &car_ip, &source_path, STAGED_APK_NAME).await?;
    let staged_path = std::env::temp_dir().join("instalador-local-apk").join(STAGED_APK_NAME);

    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    let remote_path = format!("/data/local/tmp/{}", STAGED_APK_NAME);

    let mut script = String::from("cd /data/local/tmp\n");

    // `-f` faz o curl tratar erro de HTTP como falha em vez de gravar a página de erro no lugar
    // do APK. `-t` aceita APK marcado como testOnly (build de Run do Android Studio) e `-d`
    // aceita versionCode menor que o instalado — os dois motivos mais comuns de um APK próprio
    // ser recusado, e os dois a um parâmetro de distância.
    script.push_str(&format!(
        r#"curl -fL --progress-bar -o {remote_path} {apk_url}
CURL_CODE=$?
if [ "$CURL_CODE" != "0" ]; then
echo "{marker}DOWNLOAD:FAIL:$CURL_CODE"
exit 1
fi
if [ ! -s {remote_path} ]; then
echo "{marker}DOWNLOAD:EMPTY"
exit 1
fi
echo "{marker}DOWNLOAD:OK"
pm install -r -t -d {remote_path} > /data/local/tmp/pm_out.txt 2>&1
PM_CODE=$?
PM_MSG=$(grep -m1 -iE "success|failure" /data/local/tmp/pm_out.txt)
if [ -z "$PM_MSG" ]; then PM_MSG=$(head -n1 /data/local/tmp/pm_out.txt); fi
echo "{marker}PM:$PM_CODE:$PM_MSG"
"#,
        remote_path = remote_path,
        apk_url = apk_url,
        marker = LOCAL_INSTALL_MARKER,
    ));

    // O script inteiro viaja numa única linha de comando. O modo canônico do tty corta linha em
    // 4096 bytes, então ela precisa ficar folgadamente abaixo disso — hoje fica abaixo de 1 KB. Quem crescer este script confere o tamanho antes.
    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/local_install.sh"#,
        script_escaped
    );

    // Sem isto o tty quebra as linhas na largura da janela, e um pedaço de comando ecoado pode
    // começar exatamente com o marcador que estamos esperando — lido como resultado.
    let _ = enviar_comando_com_eventos("stty cols 4000 2>/dev/null".to_string(), state.clone(), &app).await;
    tokio::time::sleep(Duration::from_millis(300)).await;

    let _ = app.emit("telnet-output", "📦 Enviando script de instalação local...");
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/local_install.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    let _ = app.emit(
        "telnet-output",
        format!("🚀 Instalando {} no dispositivo...", display_name),
    );
    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./local_install.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let resultado = run_apk_install_phases(&app, &state, &apk_url).await;

    // Só agora o servidor HTTP pode cair: derrubado no fim de qualquer espera (inclusive timeout),
    // ele sumia com o carro ainda baixando o APK.
    stop_http_server(&state).await;
    let _ = std::fs::remove_file(&staged_path);

    resultado?;

    let _ = app.emit("telnet-output", "✅ Instalação concluída com sucesso!");
    Ok(())
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Equivalente a: api.installLocalApk
// Permite escolher um APK qualquer no disco (ex.: um build próprio) e instalá-lo no
// Haval.
#[tauri::command]
async fn install_local_apk(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
    apk_path: String,
) -> Result<(), ErroDoInstalador> {
    let source_path = PathBuf::from(&apk_path);
    let display_name = source_path
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or_else(|| ErroDoInstalador::ApkNotFound(apk_path.clone()))?
        .to_string();

    install_apk_from_path(app, state, source_path, display_name).await
}

// Instala o DeepGWT (nosso app) embarcado dentro do próprio instalador — não depende
// de escolher um arquivo (o que no Android exigiria lidar com URIs `content://` do
// Storage Access Framework em vez de um caminho de arquivo comum). É o fluxo pensado
// para uso no celular: baixar o instalador, conectar no Wi-Fi do carro e instalar
// com um único toque.
#[tauri::command]
async fn install_bundled_deepgwt(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    let resource_path = app
        .path()
        .resolve("resources/deepgwt.apk", BaseDirectory::Resource)
        .map_err(|_| ErroDoInstalador::ApkNotFound("resources/deepgwt.apk".to_string()))?;

    install_apk_from_path(app.clone(), state.clone(), resource_path, "DeepGWT".to_string()).await?;

    Ok(())
}

// Inicia o serviço do Shizuku no carro — sem isso, o DeepGWT fica visivelmente
// "inativo" (HVAC, modo de condução, equalizador e gerenciador de arquivos não
// funcionam, pois todos dependem do binder do Shizuku para chamar APIs
// privilegiadas).
//
// Histórico: a v0.1.4 tentava rodar o `shizuku_starter` apontando `--apk=` para
// um shizuku.apk avulso em /data/local/tmp, sem `pm install`, pra contornar o
// bloqueio deste OEM (beantechs) que rejeita a instalação do pacote com
// "INSTALL_FAILED_INVALID_APK: beantechs disallow apk moe.shizuku.privileged.api
// to be installed". Isso não funciona: o processo do servidor Shizuku
// (rikka.shizuku.server.ShizukuService), já carregado a partir do --apk=, chama
// PackageManager.getApplicationInfo("moe.shizuku.privileged.api") no seu
// construtor e dá `System.exit(50)` imediatamente se o pacote não estiver de
// fato instalado — independente de como o processo foi iniciado. O
// `shizuku_starter` não verifica isso (daí o log antigo mostrar "iniciado" antes
// do processo morrer sozinho ~instantes depois).
//
// Fix: usamos um fork deste shizuku.apk com o applicationId recompilado para
// "br.com.deepgwt.shizuku" (nada mais mudou — mesmo código, mesmo
// `shizuku_starter` nativo, que não faz verificação de assinatura do --apk=).
// Como o bloqueio do carro é por nome de pacote, o `pm install` deste apk
// renomeado funciona normalmente, e o próprio ShizukuService passa a encontrar
// seu "manager" via PackageManager.
//
// Histórico 2 (v0.1.5): tentamos pré-preencher a permissão do DeepGWT
// escrevendo diretamente em /data/user_de/0/com.android.shell/shizuku.json
// (flags=2 = FLAG_ALLOWED), já que o DeepGWT nunca chama
// Shizuku.requestPermission(). Isso não funciona: o construtor de
// ShizukuConfigManager (server/.../ShizukuConfigManager.java) roda TODA vez
// que o servidor sobe e recalcula os flags de QUALQUER app que declare
// "moe.shizuku.manager.permission.API_V23" a partir do estado real de
// permissão do Android (PermissionManagerApis.checkPermission), sobrescrevendo
// qualquer coisa que a gente tenha escrito manualmente no JSON.
//
// Fix real: essa permissão é "dangerous" (não "normal" — ver
// manager/src/main/AndroidManifest.xml no fork), então o Android só concede
// automaticamente via um diálogo de runtime permission, que o DeepGWT nunca
// dispara. A gente contorna isso concedendo a permissão manualmente via
// `pm grant`, o mesmo mecanismo que o shell/root usa — precisa rodar depois
// que os dois apks (Shizuku, que declara a permissão, e DeepGWT, que a
// solicita) já estão instalados, mas antes de subir o shizuku_server, para
// que o scan de permissões dele já capture a concessão nesta mesma execução.
//
// Histórico 3 (v0.1.7): mesmo com a permissão concedida, o shizuku_server
// morria ~instantes depois de subir com um UnsatisfiedLinkError: dlopen failed:
// library "/data/local/tmp/lib/arm64/librish.so" not found (confirmado via
// logcat capturado no próprio script). O `shizuku_starter` nativo (starter.cpp)
// calcula o diretório de libs nativas como dirname(--apk=)/lib/<abi> — e a gente
// estava passando `--apk=/data/local/tmp/shizuku.apk`, o arquivo avulso que
// empurramos, cujo diretório não tem pasta lib/ nenhuma. O app oficial do
// Shizuku não tem esse problema porque, quando `--apk=` não é passado, o
// starter roda `pm path moe.shizuku.privileged.api` e usa o caminho instalado
// de verdade em /data/app/~~xxx/pkg-xxx/, que o pm install POPULA com
// lib/arm64/*.so. Não dá pra confiar nesse fallback porque o nome do pacote
// está hardcoded no starter para o original, e a gente renomeou o pacote.
//
// Fix: depois do `pm install`, resolvemos o caminho instalado de verdade via
// `pm path {SHIZUKU_PACKAGE}` e passamos ESSE caminho (não o arquivo avulso)
// para `--apk=`, replicando o que o starter faria sozinho com o pacote original.
#[tauri::command]
async fn install_and_start_shizuku(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    let car_ip = discover_car_ip().await?;

    let apk_resource = app
        .path()
        .resolve("resources/shizuku.apk", BaseDirectory::Resource)
        .map_err(|_| ErroDoInstalador::ApkNotFound("resources/shizuku.apk".to_string()))?;
    let starter_resource = app
        .path()
        .resolve("resources/shizuku-starter", BaseDirectory::Resource)
        .map_err(|_| ErroDoInstalador::ApkNotFound("resources/shizuku-starter".to_string()))?;

    let _ = app.emit(
        "telnet-output",
        "🔑 Enviando Shizuku e o binário de inicialização...",
    );
    let urls = stage_files_and_serve(
        &state,
        &car_ip,
        &[
            (&apk_resource, STAGED_SHIZUKU_APK_NAME),
            (&starter_resource, STAGED_SHIZUKU_STARTER_NAME),
        ],
    )
    .await?;
    let apk_url = &urls[0];
    let starter_url = &urls[1];

    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    let remote_apk_path = format!("/data/local/tmp/{}", STAGED_SHIZUKU_APK_NAME);
    let remote_starter_path = format!("/data/local/tmp/{}", STAGED_SHIZUKU_STARTER_NAME);
    let script = format!(
        r#"cd /data/local/tmp
curl -L --progress-bar -o {apk_path} {apk_url}
curl -L --progress-bar -o {starter_path} {starter_url}
chmod +x {starter_path}
pkill -9 -f {starter_path} 2>/dev/null
pkill -9 -f shizuku_server 2>/dev/null
PM_OUT=$(pm install -r {apk_path} 2>&1)
PM_CODE=$?
if [ "$PM_CODE" != "0" ]; then
  echo "$PM_OUT"
  case "$PM_OUT" in *DUPLICATE_PERMISSION*) echo "Ja existe outro Shizuku instalado que usa a mesma permissao. Remova-o antes e rode o instalador de novo." ;; esac
  echo "{marker}PM_INSTALL_FAILED:$PM_CODE"
else
  pm grant {app_pkg} {permission} 2>/dev/null
  INSTALLED_APK=$(pm path {shizuku_pkg} 2>/dev/null | head -n1 | sed 's/^package://')
  if [ -z "$INSTALLED_APK" ]; then
    INSTALLED_APK={apk_path}
  fi
  setsid {starter_path} --apk="$INSTALLED_APK" >/data/local/tmp/shizuku.log 2>&1 < /dev/null &
  sleep 3
  if pidof shizuku_server >/dev/null; then
    echo "{marker}0"
  else
    SHELL_CTX=$(id -Z 2>/dev/null)
    LOGCAT_TAIL=$(logcat -d -t 400 2>/dev/null | grep -iE "shizuku|FATAL EXCEPTION|AndroidRuntime|avc:" | tr "\n" "|")
    echo "{marker}SERVER_DIED:INSTALLED_APK=$INSTALLED_APK $(tr "\n" " " < /data/local/tmp/shizuku.log) ||SHELL_CTX:$SHELL_CTX ||LOGCAT:$LOGCAT_TAIL"
  fi
fi
"#,
        apk_path = remote_apk_path,
        apk_url = apk_url,
        starter_path = remote_starter_path,
        starter_url = starter_url,
        app_pkg = DEEPGWT_PACKAGE,
        permission = SHIZUKU_API_PERMISSION,
        shizuku_pkg = SHIZUKU_PACKAGE,
        marker = LOCAL_INSTALL_MARKER,
    );

    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/start_shizuku.sh"#,
        script_escaped
    );

    let _ = app.emit("telnet-output", "📦 Enviando script de inicialização do Shizuku...");
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/start_shizuku.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    let _ = app.emit("telnet-output", "🚀 Instalando e iniciando o Shizuku...");
    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./start_shizuku.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let result = wait_for_prefix(LOCAL_INSTALL_MARKER, 60, &state, &app).await;
    stop_http_server(&state).await;

    let marker_line = result?;
    let code = marker_line.trim_start_matches(LOCAL_INSTALL_MARKER).trim();
    if code != "0" {
        return Err(ErroDoInstalador::InstallFailed(code.to_string()));
    }

    let _ = app.emit("telnet-output", "✅ Shizuku instalado e rodando (verificado)!");
    Ok(())
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Reinicia o Shizuku sem reinstalar nada — necessário porque o servidor do Shizuku (subido via
// `shizuku_starter` como um processo shell comum, não um daemon do sistema) NÃO sobrevive a um
// reboot do carro: não existe (nem pode existir facilmente, dado que o app roda sem privilégios
// de sistema) um BroadcastReceiver de BOOT_COMPLETED reiniciando-o sozinho. Sem isso, depois de
// todo reboot o usuário tinha que rodar o fluxo completo "Instalar no Carro" de novo (baixando e
// reinstalando os APKs) só para religar o Shizuku.
//
// Caminho rápido: reaproveita o `shizuku_starter` e o apk já instalados (resolvidos via
// `pm path`, igual install_and_start_shizuku faz) sem baixar nada de novo. Se o binário do
// starter não estiver mais em /data/local/tmp (ex.: o diretório foi limpo) ou o apk não estiver
// instalado, cai de volta no fluxo completo via install_and_start_shizuku.
#[tauri::command]
async fn restart_shizuku(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    conferir_wifi_do_carro().await?;

    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    let remote_starter_path = format!("/data/local/tmp/{}", STAGED_SHIZUKU_STARTER_NAME);
    let script = format!(
        r#"pkill -9 -f {starter_path} 2>/dev/null
pkill -9 -f shizuku_server 2>/dev/null
INSTALLED_APK=$(pm path {shizuku_pkg} 2>/dev/null | head -n1 | sed 's/^package://')
if [ -z "$INSTALLED_APK" ] || [ ! -x {starter_path} ]; then
  echo "{marker}NEEDS_FULL_INSTALL"
else
  pm grant {app_pkg} {permission} 2>/dev/null
  setsid {starter_path} --apk="$INSTALLED_APK" >/data/local/tmp/shizuku.log 2>&1 < /dev/null &
  sleep 3
  if pidof shizuku_server >/dev/null; then
    echo "{marker}0"
  else
    echo "{marker}SERVER_DIED:$(tr "\n" " " < /data/local/tmp/shizuku.log)"
  fi
fi
"#,
        starter_path = remote_starter_path,
        shizuku_pkg = SHIZUKU_PACKAGE,
        app_pkg = DEEPGWT_PACKAGE,
        permission = SHIZUKU_API_PERMISSION,
        marker = LOCAL_INSTALL_MARKER,
    );

    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/restart_shizuku.sh"#,
        script_escaped
    );

    let _ = app.emit("telnet-output", "🔁 Tentando reiniciar o Shizuku (sem reinstalar)...");
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/restart_shizuku.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./restart_shizuku.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let result = wait_for_prefix(LOCAL_INSTALL_MARKER, 30, &state, &app).await;
    let marker_line = result?;
    let code = marker_line.trim_start_matches(LOCAL_INSTALL_MARKER).trim();

    if code == "NEEDS_FULL_INSTALL" {
        let _ = app.emit(
            "telnet-output",
            "ℹ️ Caminho rápido indisponível (arquivos não encontrados) — reinstalando o Shizuku...",
        );
        return install_and_start_shizuku(app, state).await;
    }

    if code != "0" {
        return Err(ErroDoInstalador::InstallFailed(code.to_string()));
    }

    let _ = app.emit("telnet-output", "✅ Shizuku reiniciado (verificado)!");
    Ok(())
}

// Remove completamente o DeepGWT, o nosso fork do Shizuku e qualquer app de microG instalado no carro. O microG nunca é instalado por este instalador, então
// procuramos o pacote dinamicamente por nome em vez de assumir um applicationId fixo — pode ter
// sido colocado lá por fora deste fluxo.
//
// Diferente de instalação, uma falha em `pm uninstall` de um pacote que já não está instalado é
// esperada e inofensiva (ex.: rodar "Remover" duas vezes), então não tratamos código de saída
// != 0 de cada `pm uninstall` individual como erro — só reportamos no terminal para transparência.
/// Onde o app guarda a função de fábrica dos coringas do volante enquanto os reprograma
/// (`CoringasDoVolante.ARQUIVO_DO_INSTALADOR` no app). Lido e apagado por `uninstall_deepgwt`.
const CORINGAS_FILE: &str = "/data/local/tmp/.deepgwt_coringas";

#[tauri::command]
async fn uninstall_deepgwt(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<String, ErroDoInstalador> {
    conferir_wifi_do_carro().await?;

    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    // Alpha 8.5: coringas do volante reprogramados ficam com 99 (sem função de fábrica). O app
    // guarda a função escolhida no menu do carro em CORINGAS_FILE; devolvê-la ANTES de remover
    // evita deixar os botões sem função. Só as duas chaves conhecidas, só valores numéricos.
    // O app é parado ANTES de devolver os coringas: parado depois, ele ainda os reescreveria com 99.
    let script = format!(
        r#"am force-stop {app_pkg} 2>/dev/null
CFILE={coringas_file}
if [ -f "$CFILE" ]; then
  while IFS="=" read -r K V; do
    case "$K" in bean_sw_custom_key1_config|bean_sw_custom_key2_config) ;; *) continue ;; esac
    if [ "$V" = "null" ]; then settings delete system "$K" >/dev/null 2>&1
    else case "$V" in *[!0-9-]*|"") ;; *) settings put system "$K" "$V" >/dev/null 2>&1 ;; esac
    fi
  done < "$CFILE"
  rm -f "$CFILE"
fi
am force-stop {shizuku_pkg} 2>/dev/null
pkill -9 -f shizuku_server 2>/dev/null
pkill -9 -f shizuku_starter 2>/dev/null
MICROG_PKG=$(pm list packages 2>/dev/null | grep -i microg | head -n1 | sed 's/^package://')
pm uninstall {app_pkg} >/dev/null 2>&1
GT_CODE=$?
pm uninstall {shizuku_pkg} >/dev/null 2>&1
SHIZUKU_CODE=$?
MICROG_CODE=0
if [ -n "$MICROG_PKG" ]; then
  pm uninstall "$MICROG_PKG" >/dev/null 2>&1
  MICROG_CODE=$?
fi
echo "{marker}GT=$GT_CODE SHIZUKU=$SHIZUKU_CODE MICROG=${{MICROG_PKG:-nenhum}}:$MICROG_CODE"
"#,
        app_pkg = DEEPGWT_PACKAGE,
        shizuku_pkg = SHIZUKU_PACKAGE,
        marker = LOCAL_INSTALL_MARKER,
        coringas_file = CORINGAS_FILE,
    );

    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/uninstall_deepgwt.sh"#,
        script_escaped
    );

    let _ = app.emit(
        "telnet-output",
        "🗑️ Enviando script de remoção (DeepGWT + Shizuku + microG)...",
    );
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/uninstall_deepgwt.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    let _ = app.emit("telnet-output", "🚀 Removendo aplicações do carro...");
    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./uninstall_deepgwt.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let result = wait_for_prefix(LOCAL_INSTALL_MARKER, 30, &state, &app).await?;
    let _ = app.emit("telnet-output", format!("📋 Resultado: {}", result));
    // Devolve à tela o que o carro recusou ("" = tudo removido): sem isso ela anunciava "o carro voltou
    // ao estado de fábrica" mesmo quando o `pm uninstall` tinha saído com erro.
    match resumo_da_remocao(&result) {
        ResumoDaRemocao::Completa => {
            let _ = app.emit(
                "telnet-output",
                "✅ Remoção concluída. O carro volta a ficar como de fábrica (sem DeepGWT, Shizuku ou microG).",
            );
            Ok(String::new())
        }
        ResumoDaRemocao::Parcial(quais) => {
            let _ = app.emit(
                "telnet-output",
                format!(
                    "⚠️ Remoção terminada, mas o carro recusou: {}. Se o app já não estava instalado isto é normal; senão, tente de novo.",
                    quais
                ),
            );
            Ok(quais)
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

#[derive(Debug, PartialEq)]
enum ResumoDaRemocao {
    Completa,
    Parcial(String),
}

// A linha do roteiro é `GT=<código> SHIZUKU=<código> MICROG=<pacote>:<código>`. `pm uninstall` sai
// com 0 quando removeu; qualquer outro valor é "não removeu" (ou "não existia").
fn resumo_da_remocao(linha: &str) -> ResumoDaRemocao {
    let mut recusados: Vec<&str> = Vec::new();
    if !linha.contains("GT=0") {
        recusados.push("DeepGWT");
    }
    if !linha.contains("SHIZUKU=0") {
        recusados.push("Shizuku");
    }
    if let Some(resto) = linha.split("MICROG=").nth(1) {
        if !resto.starts_with("nenhum") && !resto.trim_end().ends_with(":0") {
            recusados.push("microG");
        }
    }
    if recusados.is_empty() {
        ResumoDaRemocao::Completa
    } else {
        ResumoDaRemocao::Parcial(recusados.join(", "))
    }
}

// Fluxo completo pensado para o botão único "Instalar no Carro": garante que o
// Shizuku esteja instalado e rodando antes do DeepGWT, já que sem ele o app fica
// com a maior parte das telas "inativas" (o motivo original deste comando existir).
//
// Importante: o DeepGWT precisa ser instalado ANTES do Shizuku, e não depois
// como na v0.1.4 — install_and_start_shizuku agora roda `pm grant` para o
// pacote do DeepGWT antes de subir o servidor (ver comentário lá), o que só
// funciona se o pacote já estiver instalado no aparelho.
#[tauri::command]
async fn install_deepgwt_with_shizuku(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    let resource_path = app
        .path()
        .resolve("resources/deepgwt.apk", BaseDirectory::Resource)
        .map_err(|_| ErroDoInstalador::ApkNotFound("resources/deepgwt.apk".to_string()))?;

    install_apk_from_path(app.clone(), state.clone(), resource_path, "DeepGWT".to_string()).await?;

    install_and_start_shizuku(app.clone(), state.clone()).await?;

    // Ao ATUALIZAR o app, o Shizuku às vezes "caía" logo depois e o usuário precisava reiniciá-lo
    // à mão. A causa conhecida era o bypass de assinatura por Frida, que saiu no IndependentGW; o
    // reinício final fica até o carro mostrar que não faz mais falta. Um restart final — exatamente o caminho do botão manual "Reiniciar Shizuku", sem reinstalar
    // nada — deixa o servidor num estado estável com o sistema já assentado, eliminando o passo
    // manual. Se o caminho rápido não estiver disponível, restart_shizuku cai no fluxo completo
    // sozinho.
    tokio::time::sleep(Duration::from_secs(3)).await;
    let _ = app.emit(
        "telnet-output",
        "🔁 Reinício final do Shizuku para estabilizar após a atualização...",
    );
    restart_shizuku(app.clone(), state.clone()).await?;

    // Alpha 7.9: abre o app no carro ao terminar. Numa instalação nova ele mostra o assistente de
    // configuração; numa atualização, as novidades — sem a pessoa ter de procurar o ícone. Falhar
    // aqui não desfaz nada do que foi instalado, então o erro só vai para o terminal.
    let _ = app.emit("telnet-output", "📱 Abrindo o DeepGWT no carro...");
    if let Err(e) = enviar_comando_com_eventos(
        format!("am start -n {} >/dev/null 2>&1", DEEPGWT_SPLASH_ACTIVITY),
        state,
        &app,
    )
    .await
    {
        let _ = app.emit("telnet-output", format!("ℹ️ Não deu para abrir o app sozinho: {}", e));
    }
    Ok(())
}

// "Modo sorrateiro" do DeepGWT oculta a barra, efeitos, painel virtual e o
// ícone do launcher, deixando a multimídia como se nunca tivesse sido
// modificada. A ÚNICA saída documentada na UI do DeepGWT é uma sequência
// física no volante (pisca-alta 2x + seta D-E-D-E em menos de 8s — ver
// StealthModeController.kt no fork do app): não existe switch de "desativar"
// na tela de Configurações. Se a sequência falhar por qualquer motivo (ex.:
// o ForegroundService ainda não tinha o listener de dados do carro
// registrado quando o modo foi ativado, ou as 6 ações não couberam na janela
// de 8s), o usuário fica trancado fora do próprio app.
//
// Saída de emergência: o estado ("stealthModeActive") mora numa
// SharedPreferences "device protected" (sobrevive a reboot) em
// /data/user_de/0/<pkg>/shared_prefs/deepgwt_prefs.xml. A gente:
// 1. mata todos os processos do app com `am force-stop` — a instância em
//    memória cacheia o valor antigo e não relê o arquivo sozinha;
// 2. reescreve o valor pra "false" direto no XML;
// 3. reabilita o LauncherAlias (fica desabilitado via PackageManager
//    enquanto o modo está ativo — ver setLauncherIconVisible no fork);
// 4. reabre o app direto pela AberturaActivity, que continua respondendo a
//    `am start` mesmo com o alias desabilitado (a mesma "saída de
//    emergência" comentada no próprio código do DeepGWT).
// O diretório é o applicationId real (br.com.redesurftank.havalshisuku — ver nota em
// DEEPGWT_PACKAGE); o nome do arquivo de SharedPreferences (deepgwt_prefs.xml) é arbitrário
// e não mudou (definido em StealthModeController.kt, independente do applicationId).
const DEEPGWT_STEALTH_PREFS_PATH: &str =
    "/data/user_de/0/br.com.redesurftank.havalshisuku/shared_prefs/deepgwt_prefs.xml";
// Pacote à esquerda da barra é o applicationId real; a classe à direita continua com o
// nome de pacote compilado (namespace br.com.deepgwt), que não mudou.
const DEEPGWT_LAUNCHER_ALIAS: &str =
    "br.com.redesurftank.havalshisuku/br.com.deepgwt.LauncherAlias";
/// O segundo ícone ("Atualizar DeepGWT"): o app o esconde junto com o primeiro (ver
/// `StealthModeController`), então a saída precisa reabilitar os dois.
const DEEPGWT_UPDATER_ALIAS: &str =
    "br.com.redesurftank.havalshisuku/br.com.deepgwt.UpdaterAlias";
const DEEPGWT_SPLASH_ACTIVITY: &str =
    "br.com.redesurftank.havalshisuku/br.com.deepgwt.AberturaActivity";

/// O script que desliga o modo sorrateiro. Vai para o carro dentro de `echo -e '<script>' > arquivo`,
/// então NÃO pode ter aspas simples: uma só fecha o `echo` e o shell do carro monta outro roteiro
/// (foi o que impediu o `sed` de corrigir o XML). O `sed` usa só o endereço `/stealthModeActive/`.
fn script_do_modo_sorrateiro() -> String {
    format!(
        r#"am force-stop {app_pkg}
sleep 1
if [ -f {prefs_path} ]; then
  sed -i /stealthModeActive/s/true/false/ {prefs_path}
  if grep stealthModeActive {prefs_path} | grep -q true; then
    PATCH_STATUS=NOT_PATCHED
  else
    PATCH_STATUS=PATCHED
  fi
else
  PATCH_STATUS=NO_PREFS_FILE
fi
pm enable {alias}
pm enable {updater_alias}
am start -n {splash} >/dev/null 2>&1
echo "{marker}$PATCH_STATUS"
"#,
        app_pkg = DEEPGWT_PACKAGE,
        prefs_path = DEEPGWT_STEALTH_PREFS_PATH,
        alias = DEEPGWT_LAUNCHER_ALIAS,
        updater_alias = DEEPGWT_UPDATER_ALIAS,
        splash = DEEPGWT_SPLASH_ACTIVITY,
        marker = LOCAL_INSTALL_MARKER,
    )
}

#[tauri::command]
async fn reset_stealth_mode(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    conferir_wifi_do_carro().await?;

    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    let script = script_do_modo_sorrateiro();

    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/reset_stealth.sh"#,
        script_escaped
    );

    let _ = app.emit(
        "telnet-output",
        "🔓 Enviando script de recuperação do Modo Sorrateiro...",
    );
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/reset_stealth.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    let _ = app.emit(
        "telnet-output",
        "🚀 Restaurando ícone e desativando Modo Sorrateiro...",
    );
    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./reset_stealth.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let result = wait_for_prefix(LOCAL_INSTALL_MARKER, 30, &state, &app).await;
    let marker_line = result?;
    let code = marker_line.trim_start_matches(LOCAL_INSTALL_MARKER).trim();
    if code == "NO_PREFS_FILE" {
        return Err(ErroDoInstalador::InstallFailed(
            "Arquivo de preferências do DeepGWT não encontrado — o Modo Sorrateiro já deve estar desativado.".to_string(),
        ));
    }
    if code == "NOT_PATCHED" {
        return Err(ErroDoInstalador::InstallFailed(
            "Não consegui desligar o Modo Sorrateiro no arquivo de preferências do app. O ícone foi reabilitado; tente de novo.".to_string(),
        ));
    }

    let _ = app.emit(
        "telnet-output",
        "✅ Modo Sorrateiro desativado, ícone e app restaurados!",
    );
    Ok(())
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Executa um comando no shell do carro e DEVOLVE a saída.
//
// O que já existia: `send_command`, que dispara e esquece (`Result<(), _>`), e as esperas por
// marcador (`wait_for_prefix`), que só sabem reconhecer uma linha de resultado. Nenhum dos dois
// serve para perguntar qualquer coisa ao carro e ler a resposta.
//
// Duas particularidades deste telnet, ambas descobertas apanhando delas:
//
//  1. O SHELL ECOA tudo que recebe. Se a sentinela aparecesse inteira dentro do comando enviado,
//     a leitura encontraria o eco do próprio comando e pararia antes da saída chegar. Por isso a
//     sentinela é montada por concatenação (`"@@B""EG_..."`): o comando ecoado contém as duas
//     metades separadas, e a string inteira só existe no que o `printf` imprime.
//
//  2. O TTY QUEBRA A LINHA na largura da janela, cortando saídas longas no meio. `stty cols 4000`
//     antes do comando resolve, e afeta apenas esta sessão de terminal — não muda nada no carro.
//
// Isto executa o que lhe mandarem, exatamente como `send_command` e a tela de Terminal já fazem;
// a diferença é que agora a resposta volta. Quem chama é responsável por mandar leitura.
#[tauri::command]
async fn telnet_exec(
    command: String,
    timeout_secs: Option<u64>,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<String, ErroDoInstalador> {
    // Nenhuma tela abre a sessão antes de chamar isto (só os fluxos de instalação abrem a deles):
    // sem esta abertura, "Reler do carro" da configuração de fábrica falhava com "sem sessão".
    if !sessao_aberta(state.clone()).await? {
        conferir_wifi_do_carro().await?;
        abrir_sessao_telnet(state.clone()).await?;
    }

    // Marca única por chamada: duas leituras seguidas não podem confundir a sentinela uma da outra
    // se restar saída atrasada da anterior no buffer.
    let tag = format!(
        "{:x}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0)
    );
    let begin = format!("@@BEG_{}@@", tag);
    let end = format!("@@END_{}@@", tag);

    let linha = format!(
        "stty cols 4000 2>/dev/null; printf \"%s\\n\" \"@@B\"\"EG_{tag}@@\"; {command} 2>&1; printf \"%s\\n\" \"@@E\"\"ND_{tag}@@\"\n",
        tag = tag,
        command = command
    );

    let conversa = async {
        let mut sessao = state.sessao.lock().await;
        let soquete = sessao.as_mut().ok_or(ErroDoInstalador::SemSessao)?;

        soquete.write_all(linha.as_bytes()).await?;
        soquete.flush().await?;

        let mut acumulado = String::new();
        let mut buf = [0u8; 8192];
        loop {
            let n = soquete.read(&mut buf).await?;
            if n == 0 {
                return Err(ErroDoInstalador::SemSessao);
            }
            acumulado.push_str(&String::from_utf8_lossy(&buf[..n]));
            if acumulado.contains(&end) {
                break;
            }
        }

        // A saída útil é o que está entre as sentinelas. Sem a de abertura, devolve-se o que veio
        // — é preferível entregar algo inspecionável a silenciar a resposta.
        let corpo = match acumulado.find(&begin) {
            Some(i) => {
                let depois = &acumulado[i + begin.len()..];
                match depois.find(&end) {
                    Some(j) => &depois[..j],
                    None => depois,
                }
            }
            None => acumulado.as_str(),
        };

        Ok(corpo.trim_matches(['\r', '\n']).to_string())
    };

    let prazo = Duration::from_secs(timeout_secs.unwrap_or(30));
    tokio::time::timeout(prazo, conversa)
        .await
        .unwrap_or(Err(ErroDoInstalador::SemResposta))
}

// Porta fixa do GwLinkServer embarcado no DeepGWT (ver GwLinkServer.kt) — só é alcançável
// quando o DeepGWT já está instalado e rodando no carro, já que a leitura de telemetria de
// verdade (via AIDL/binder pro serviço `intelligentvehiclecontrol`) só existe em código
// rodando dentro do carro, não em um shell puro via telnet.
const GWLINK_SERVER_PORT: u16 = 8766;

// Equivalente a: a função "GWLink" do hub — busca o snapshot atual de telemetria do DeepGWT
// já instalado no carro. Devolve o JSON cru (o frontend decide o que mostrar/traduzir).
//
// Erros aqui são reportados com a causa real (timeout / conexão recusada / etc.): o motivo mais
// comum é o DeepGWT não estar rodando no carro, ou a porta 8766 não estar alcançável.
#[tauri::command]
async fn gwlink_fetch_telemetry() -> Result<String, ErroDoInstalador> {
    gwlink_fetch("/").await
}

// Snapshot de diagnóstico do bootstrap automático do Shizuku (ver BootDiagnostics.kt e
// GwLinkServer.kt no DeepGWT) — usado pra investigar por que o restart automático do Shizuku
// não aconteceu depois de um reboot da MMI, sem precisar de acesso físico ao carro.
#[tauri::command]
async fn gwlink_fetch_diagnostics() -> Result<String, ErroDoInstalador> {
    gwlink_fetch("/diag").await
}

async fn gwlink_fetch(path: &str) -> Result<String, ErroDoInstalador> {
    let gateway = endereco_do_carro().await?;
    let url = format!("http://{}:{}{}", gateway, GWLINK_SERVER_PORT, path);
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(|e| ErroDoInstalador::GwLinkUnreachable {
            port: GWLINK_SERVER_PORT,
            reason: e.to_string(),
        })?;
    let response = client
        .get(&url)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::GwLinkUnreachable {
            port: GWLINK_SERVER_PORT,
            reason: e.to_string(),
        })?;
    response
        .text()
        .await
        .map_err(|e| ErroDoInstalador::GwLinkUnreachable {
            port: GWLINK_SERVER_PORT,
            reason: e.to_string(),
        })
}

#[derive(serde::Serialize, Clone)]
struct InstalledPackage {
    package_name: String,
    is_system: bool,
}

// Equivalente ao que "Instalar Apps" faz ao contrário: lista tudo que está instalado no
// carro pra função "Desinstalar Aplicativos" do hub. `pm list packages -3` = apps de
// usuário (instalados por nós ou de fábrica como apps não-system), `-s` = apps de
// sistema — essa distinção é o que decide se a UI mostra o aviso de risco.
#[tauri::command]
async fn list_installed_packages(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<Vec<InstalledPackage>, ErroDoInstalador> {
    conferir_wifi_do_carro().await?;
    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    let script = format!(
        r#"pm list packages -3 | sed 's/^package:/USER:/'
pm list packages -s | sed 's/^package:/SYSTEM:/'
echo "{marker}0"
"#,
        marker = LOCAL_INSTALL_MARKER,
    );
    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/list_packages.sh"#,
        script_escaped
    );

    let _ = app.emit("telnet-output", "📋 Listando aplicativos instalados no carro...");
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/list_packages.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./list_packages.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let lines = collect_until_prefix(LOCAL_INSTALL_MARKER, 30, &state, &app).await?;

    let mut packages: Vec<InstalledPackage> = lines
        .into_iter()
        .filter_map(|line| {
            if let Some(name) = line.strip_prefix("USER:") {
                Some(InstalledPackage { package_name: name.to_string(), is_system: false })
            } else if let Some(name) = line.strip_prefix("SYSTEM:") {
                Some(InstalledPackage { package_name: name.to_string(), is_system: true })
            } else {
                None
            }
        })
        .collect();
    packages.sort_by(|a, b| a.package_name.cmp(&b.package_name));
    Ok(packages)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Remoção "para o usuário atual" (`pm uninstall --user 0`) em vez de apagar o APK do
// /system: pra apps de sistema isso desativa/desinstala só pra esse usuário — geralmente
// reversível via `cmd package install-existing` ou um reset de fábrica — em vez de mexer
// direto na partição /system, que arriscaria não bootar mais a central.
#[tauri::command]
async fn uninstall_package(
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
    package_name: String,
) -> Result<(), ErroDoInstalador> {
    if package_name.is_empty()
        || !package_name.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_')
    {
        return Err(ErroDoInstalador::InstallFailed(format!(
            "Nome de pacote inválido: {}",
            package_name
        )));
    }

    conferir_wifi_do_carro().await?;
    if !sessao_aberta(state.clone()).await? {
        abrir_sessao_telnet(state.clone()).await?;
    }

    let script = format!(
        r#"RESULT=$(pm uninstall --user 0 {pkg} 2>&1)
echo "$RESULT"
echo "{marker}$RESULT"
"#,
        pkg = package_name,
        marker = LOCAL_INSTALL_MARKER,
    );
    let script_escaped = script.split('\n').collect::<Vec<_>>().join("\\n");
    let gravar_script = format!(
        r#"echo -e '{}' > /data/local/tmp/uninstall_package.sh"#,
        script_escaped
    );

    let _ = app.emit("telnet-output", format!("🗑️ Removendo {}...", package_name));
    enviar_comando_com_eventos(gravar_script, state.clone(), &app).await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "chmod +x /data/local/tmp/uninstall_package.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;
    tokio::time::sleep(Duration::from_millis(1000)).await;

    enviar_comando_com_eventos(
        "cd /data/local/tmp && ./uninstall_package.sh".to_string(),
        state.clone(),
        &app,
    )
    .await?;

    let marker_line = wait_for_prefix(LOCAL_INSTALL_MARKER, 30, &state, &app).await?;
    let result = marker_line.trim_start_matches(LOCAL_INSTALL_MARKER).trim().to_string();
    if !result.to_lowercase().contains("success") {
        return Err(ErroDoInstalador::InstallFailed(result));
    }

    let _ = app.emit("telnet-output", format!("✅ {} removido!", package_name));
    Ok(())
}

// ===================== Opções de Desenvolvedor (menu escondido, só no instalador) =====================
//
// Tudo aqui fala com as rotas novas do GwLinkServer (ver GwLinkServer.kt/RemoteControlOps.kt no
// app do carro), na mesma porta 8766 do GWLink. A diferença chave em relação ao resto do
// arquivo: `car_ip` é passado explicitamente pelo frontend em vez de assumido como
// `endereco_do_carro()`. Até a 9.3 isso servia para alcançar o carro pela rede de casa; desde a 9.4
// [remote_url] recusa qualquer IP fora do hotspot do carro (192.168.33.x) e qualquer chamada com
// este computador fora do Wi-Fi dele.

fn remote_control_client(long_timeout: bool) -> Result<reqwest::Client, ErroDoInstalador> {
    let mut builder = reqwest::Client::builder();
    if !long_timeout {
        builder = builder.timeout(Duration::from_secs(15));
    }
    // Downloads/backups não levam timeout de request inteiro (o timeout do reqwest cobre o
    // corpo todo, não só a conexão) — um backup grande de verdade pode levar minutos.
    builder
        .build()
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Toda rota com IP explícito passa aqui, então é aqui que fica a trava do Wi-Fi do carro.
fn remote_url(car_ip: &str, path: &str) -> Result<String, ErroDoInstalador> {
    exigir_ip_do_carro(car_ip)?;
    Ok(format!("http://{}:{}{}", car_ip.trim(), GWLINK_SERVER_PORT, path))
}

#[tauri::command]
async fn remote_list_displays(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/displays")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Telemetria com IP explícito (em vez de auto-detectar o gateway como `gwlink_fetch_telemetry`).
// Desde a 9.4 só no Wi-Fi do carro (ver [remote_url]).
#[tauri::command]
async fn remote_gwlink_telemetry(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// ── Nível e licença (Alpha 8.0) ──────────────────────────────────────────────────────────────
//
// O nível do DeepGWT mora no carro: `GET /licenca` devolve o nível e a tabela recurso -> nível, e o
// instalador só desenha os cadeados. Um carro anterior à 8.0 cai no ramo padrão do servidor e
// devolve telemetria sem `ok` — quem interpreta é `tiers/niveis.ts` ("nada trancado").
#[tauri::command]
async fn remote_licenca_ler(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/licenca")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Nível de teste: só um build de teste do app aceita (o do instalador responde 403 com o
// motivo). `None` volta ao nível normal. Um 403 vira erro com o motivo do carro.
// `cobranca` (Alpha 8.1) liga/desliga a cobrança de teste; sozinho, não mexe no nível de teste.
#[tauri::command]
async fn remote_licenca_nivel_de_teste(
    car_ip: String,
    nivel: Option<String>,
    cobranca: Option<bool>,
) -> Result<String, ErroDoInstalador> {
    let corpo = match cobranca {
        Some(c) if nivel.is_none() => serde_json::json!({ "cobranca": c }),
        Some(c) => serde_json::json!({ "nivel": nivel, "cobranca": c }),
        None => serde_json::json!({ "nivel": nivel }),
    };
    licenca_post(&car_ip, "/licenca/nivel-de-teste", corpo).await
}

// Licença (Alpha 8.1): manda o código ao carro, que confere assinatura, VIN e série. A recusa
// (400) vira erro com a frase do carro ("A linha 3 tem um caractere errado", "já foi usada"...).
#[tauri::command]
async fn remote_licenca_ativar(car_ip: String, codigo: String) -> Result<String, ErroDoInstalador> {
    licenca_post(&car_ip, "/licenca/ativar", serde_json::json!({ "codigo": codigo })).await
}

/// Teste grátis do Max (Alpha 8.4): começa no carro. Uma vez por carro; a recusa vem com o motivo.
#[tauri::command]
async fn remote_licenca_avaliacao(car_ip: String) -> Result<String, ErroDoInstalador> {
    licenca_post(&car_ip, "/licenca/avaliacao", serde_json::json!({})).await
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/// "Voltar ao meu plano" (Alpha 10.1): encerra o teste do Max no carro.
#[tauri::command]
async fn remote_licenca_avaliacao_encerrar(car_ip: String) -> Result<String, ErroDoInstalador> {
    licenca_post(&car_ip, "/licenca/avaliacao/encerrar", serde_json::json!({})).await
}

async fn licenca_post(car_ip: &str, caminho: &str, corpo: serde_json::Value) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(car_ip, caminho)?)
        .json(&corpo)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let ok = resp.status().is_success();
    let texto = resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    if ok {
        return Ok(texto);
    }
    let motivo = serde_json::from_str::<serde_json::Value>(&texto)
        .ok()
        .and_then(|v| v.get("error").and_then(|e| e.as_str()).map(|s| s.to_string()))
        .unwrap_or(texto);
    Err(ErroDoInstalador::RecusadoPeloCarro(motivo))
}

// ── Perfis de motorista ──────────────────────────────────────────────────────────────────────
//
// Os perfis moram NO CARRO, não aqui. Esta tela do instalador chegou a ter os próprios perfis,
// guardados no localStorage do computador com uma lista fixa de 16 chaves copiada do lado
// Android; as duas listas divergiram, e o resultado eram dois recursos com o mesmo nome que não
// se enxergavam — salvar um perfil no computador não fazia o carro saber de nada. Agora o
// instalador é um cliente do perfil do carro: as quatro rotas abaixo são a mesma coisa que o
// launcher 3D e a tela do app usam.

#[tauri::command]
async fn remote_profiles_list(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/profiles")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Sem timeout de requisição: aplicar um perfil grava ~150 preferências, manda os subsistemas
// relerem e escreve no barramento chave por chave. Cortar em 15 s não cancelaria nada — o carro
// seguiria aplicando — só faria o instalador relatar uma falha que não houve.
#[tauri::command]
async fn remote_profile_select(car_ip: String, name: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .post(remote_url(&car_ip, "/profiles/select")?)
        .json(&serde_json::json!({ "name": name }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_profile_save(
    car_ip: String,
    name: String,
    seat_memory_slot: Option<i64>,
) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let mut corpo = serde_json::json!({ "name": name });
    if let Some(slot) = seat_memory_slot {
        corpo["seatMemorySlot"] = serde_json::json!(slot);
    }
    let resp = client
        .post(remote_url(&car_ip, "/profiles/save")?)
        .json(&corpo)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_profile_delete(car_ip: String, name: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/profiles/delete")?)
        .json(&serde_json::json!({ "name": name }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Histórico de serviço (GwLinkServer `/servico`): o caderno de revisões mora no carro. O instalador
// lista, registra e apaga por lá; o corpo do registro é montado na tela e o app valida de novo.
#[tauri::command]
async fn remote_servico_list(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/servico")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_servico_add(car_ip: String, registro: serde_json::Value) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/servico/adicionar")?)
        .json(&registro)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_servico_delete(car_ip: String, id: i64) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/servico/apagar")?)
        .json(&serde_json::json!({ "id": id }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Tema do HUD (GwLinkServer `/hud/tema`, Alpha 5.9). O corpo é o arquivo do tema como o editor do
// instalador monta — o carro valida e corrige (HudTema.deJson), então aqui ele só viaja. Um carro
// mais antigo responde telemetria sem `ok`; quem interpreta isso é a tela (hud-editor/carro.ts).
#[tauri::command]
async fn remote_hud_theme_get(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/hud/tema")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_hud_theme_apply(car_ip: String, tema_json: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/hud/tema")?)
        .header("Content-Type", "application/json")
        .body(tema_json)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// ── Ajustes do DeepGWT (GwLinkServer `/ajustes`, Alpha 6.4) ─────────────────────────────────
//
// O catálogo mora no carro: a primeira leitura traz a descrição de cada ajuste (rótulo, tipo,
// faixa, dependência) junto com os valores, e as seguintes (`catalogo=0`) só os valores. Um carro
// anterior à 6.4 cai no ramo padrão do servidor e devolve telemetria sem `ok`; quem interpreta
// isso é a tela (ajustes-deepgwt/carro.ts), igual ao tema do HUD.
#[tauri::command]
async fn remote_ajustes_ler(car_ip: String, catalogo: bool) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let caminho = if catalogo { "/ajustes" } else { "/ajustes?catalogo=0" };
    let resp = client
        .get(remote_url(&car_ip, caminho)?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// `valores_json` é o objeto `{id: valor}` já serializado pela tela — os valores têm tipos
// diferentes (booleano, número, texto) e o carro valida cada um contra o próprio catálogo, então
// aqui ele só é embrulhado. Um 503 do carro também traz JSON com o motivo: devolve o corpo em vez
// de virar erro de rede.
#[tauri::command]
async fn remote_ajustes_aplicar(car_ip: String, valores_json: String) -> Result<String, ErroDoInstalador> {
    let valores: serde_json::Value = serde_json::from_str(&valores_json)
        .map_err(|e| ErroDoInstalador::RemoteControlError(format!("valores inválidos: {}", e)))?;
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/ajustes")?)
        .json(&serde_json::json!({ "valores": valores }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Espera longa: o carro segura a resposta até algum ajuste mudar (lá ou por outro cliente) ou
// `ms` passar. O timeout da requisição é a espera mais uma folga — com os 15 s padrão, toda espera
// de 20 s viraria erro de rede.
#[tauri::command]
async fn remote_ajustes_esperar(car_ip: String, desde: i64, ms: u64) -> Result<String, ErroDoInstalador> {
    let ms = ms.min(25_000);
    let client = reqwest::Client::builder()
        .timeout(Duration::from_millis(ms + 10_000))
        .build()
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let resp = client
        .get(remote_url(&car_ip, &format!("/ajustes/espera?desde={}&ms={}", desde, ms))?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// ── Tema do painel (GwLinkServer `/temas`, Alpha 6.5) ──────────────────────────────────────
//
// Listar os temas instalados com o ativo, ativar um pela pasta, e o rascunho do "Ver no carro" do
// criador de temas. Como nos ajustes, quem interpreta a resposta (inclusive "carro antigo, sem
// `ok`") é a tela.
#[tauri::command]
async fn remote_temas_listar(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/temas")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_temas_ativar(car_ip: String, pasta: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/temas/ativar")?)
        .json(&serde_json::json!({ "pasta": pasta }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_temas_rascunho(car_ip: String, ativar: bool) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/temas/rascunho")?)
        .json(&serde_json::json!({ "ativar": ativar }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Lê um arquivo do carro inteiro para a memória, em base64. Só para arquivos pequenos — hoje, o
// `theme.deepgwt.json` de um tema, para "baixar para editar". Arquivo grande vai por
// `remote_download_file`, que grava em disco conforme chega.
#[tauri::command]
async fn remote_read_file_b64(car_ip: String, remote_path: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .get(remote_url(&car_ip, "/files/get")?)
        .query(&[("path", remote_path.as_str())])
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    if !resp.status().is_success() {
        return Err(ErroDoInstalador::RemoteControlError(format!("HTTP {} ao ler {}", resp.status(), remote_path)));
    }
    let bytes = resp.bytes().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    Ok(BASE64_STANDARD.encode(&bytes))
}

// Escrita genérica no carro (POST /set no GwLinkServer do DeepGWT) — endpoint único
// reaproveitado por todas as telas novas do instalador (ADAS, perfis, DVR, ajustes) em vez de
// um comando por feature, espelhando o mesmo endpoint genérico usado no lado Android.
#[tauri::command]
async fn remote_gwlink_set(car_ip: String, key: String, value: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/set")?)
        .json(&serde_json::json!({ "key": key, "value": value }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let status = resp.status();
    let body = resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    // O carro sempre responde 200 com corpo JSON, mas {"ok": false, ...} quando o
    // ControlService não aplicou a mudança de verdade (ex.: binder morto) — sem checar o
    // campo `ok` aqui, o front achava que salvou (a promise resolvia) mesmo quando nada
    // mudou no carro, porque só HTTP 5xx virava erro.
    let ok = serde_json::from_str::<serde_json::Value>(&body)
        .ok()
        .and_then(|v| v.get("ok").and_then(|o| o.as_bool()))
        .unwrap_or(status.is_success());
    if !status.is_success() || !ok {
        return Err(ErroDoInstalador::RemoteControlError(format!(
            "Carro recusou a alteração ({}): {}",
            status, body
        )));
    }
    Ok(body)
}

// Espaço ocupado e livre no armazenamento da central. Devolve o JSON cru do GwLinkServer
// (`{ok, totalBytes, freeBytes, usedBytes, volumes[]}`), vindo do mesmo `StorageInfo` que o app do
// carro mostra no card "Armazenamento" — por isso os dois lados nunca discordam. Lá dentro é
// `StatFs`, não Shizuku: esta rota responde mesmo num carro onde nada foi autorizado ainda.
#[tauri::command]
async fn remote_storage_info(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/storage")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let status = resp.status();
    let body = resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    if !status.is_success() {
        return Err(ErroDoInstalador::RemoteControlError(body));
    }
    Ok(body)
}

// Fallback pra quando nenhum dos 8 caminhos candidatos fixos do DVR bate: varre o armazenamento
// do carro de verdade (find recursivo via Shizuku) em vez de depender de um palpite acertar.
#[tauri::command]
async fn remote_dvr_find(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/dvr/find")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let status = resp.status();
    let body = resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    if !status.is_success() {
        return Err(ErroDoInstalador::RemoteControlError(body));
    }
    Ok(body)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// As três funções abaixo devolvem o JSON cru do GwLinkServer (`{ok, exitCode, output}`) em vez
// de só `Result<()>` — os primeiros testes reais mostraram toque/swipe falhando sem nenhum erro
// visível, porque antes o Rust só checava se a requisição HTTP em si tinha sido enviada, nunca se
// o `input -d` no carro realmente funcionou. Devolvendo o corpo, o frontend consegue mostrar o
// `exitCode`/`output` reais (ex.: "Error: Invalid display id") quando o toque não fizer nada.
#[tauri::command]
async fn remote_input_tap(car_ip: String, display: i32, x: i32, y: i32) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/input/tap")?)
        .json(&serde_json::json!({ "display": display, "x": x, "y": y }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_input_swipe(
    car_ip: String,
    display: i32,
    x1: i32,
    y1: i32,
    x2: i32,
    y2: i32,
    duration_ms: i32,
) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/input/swipe")?)
        .json(&serde_json::json!({
            "display": display, "x1": x1, "y1": y1, "x2": x2, "y2": y2, "durationMs": duration_ms
        }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

#[tauri::command]
async fn remote_input_key(car_ip: String, display: i32, keycode: i32) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/input/key")?)
        .json(&serde_json::json!({ "display": display, "keycode": keycode }))
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Reinicia a central (reboot do head unit) pelo GwLinkServer do DeepGWT. O carro agenda o reboot
// com atraso e responde ok ANTES de cair, então esta chamada retorna normalmente; a conexão só
// morre uns instantes depois, quando o Android reinicia de fato. Sem parâmetros além do IP.
#[tauri::command]
async fn remote_restart(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .post(remote_url(&car_ip, "/system/restart")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let status = resp.status();
    let texto = resp
        .text()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    // O carro recusa (409, andando) ou diz `ok:false` (Shizuku fora): isso não é "enviado".
    match recusa_do_reinicio(status.is_success(), &texto) {
        Some(motivo) => Err(ErroDoInstalador::RemoteControlError(motivo)),
        None => Ok(texto),
    }
}

// `Some(motivo)` quando a resposta do carro ao pedido de reinício não confirma o agendamento.
fn recusa_do_reinicio(http_ok: bool, corpo: &str) -> Option<String> {
    let json: Option<serde_json::Value> = serde_json::from_str(corpo).ok();
    let confirmado = json
        .as_ref()
        .and_then(|v| v.get("ok"))
        .and_then(|v| v.as_bool());
    if http_ok && confirmado != Some(false) {
        return None;
    }
    let mensagem = json
        .as_ref()
        .and_then(|v| v.get("error"))
        .and_then(|v| v.as_str())
        .map(|s| s.to_string());
    Some(mensagem.unwrap_or_else(|| "O carro não confirmou o reinício.".to_string()))
}

// Lê o stream binário do endpoint `/mirror` (ver comentário em GwLinkServer.handleMirror):
// depois dos headers HTTP, cada frame é um `u32` big-endian com o tamanho seguido dos bytes
// JPEG. Não é MJPEG/multipart de verdade — um protocolo próprio, mais simples de parsear aqui
// do que boundary de multipart, já que os dois lados desse protocolo são nosso próprio código.
async fn run_mirror_loop(
    addr: &str,
    path: &str,
    display: i32,
    app: &AppHandle,
) -> Result<(), ErroDoInstalador> {
    let conexao = TcpStream::connect(addr)
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(format!("Não conectou em {}: {}", addr, e)))?;

    let request = format!(
        "GET {} HTTP/1.1\r\nHost: {}\r\nConnection: close\r\n\r\n",
        path, addr
    );
    let mut leitor = BufReader::new(conexao);
    leitor
        .get_mut()
        .write_all(request.as_bytes())
        .await
        .map_err(ErroDoInstalador::Io)?;

    // Descarta os headers da resposta HTTP até a linha em branco, conferindo o status: um erro do
    // carro (4xx/5xx com JSON no corpo) não é um fluxo de quadros, e seus primeiros 4 bytes lidos
    // como tamanho viravam uma alocação de gigabytes.
    let mut primeira_linha = true;
    loop {
        let mut line = String::new();
        let n = leitor.read_line(&mut line).await.map_err(ErroDoInstalador::Io)?;
        if n == 0 || line.trim().is_empty() {
            break;
        }
        if primeira_linha {
            primeira_linha = false;
            if !status_http_ok(&line) {
                return Ok(());
            }
        }
    }

    loop {
        let mut len_buf = [0u8; 4];
        if leitor.read_exact(&mut len_buf).await.is_err() {
            break; // carro fechou a conexão (display não suportado ou stream cancelado)
        }
        let len = u32::from_be_bytes(len_buf) as usize;
        if len == 0 || len > TAMANHO_MAXIMO_DO_QUADRO {
            break; // fluxo fora do formato: melhor parar do que alocar o que vier
        }
        let mut frame = vec![0u8; len];
        if leitor.read_exact(&mut frame).await.is_err() {
            break;
        }
        let data_url = format!("data:image/jpeg;base64,{}", BASE64_STANDARD.encode(&frame));
        let _ = app.emit(
            "mirror-frame",
            serde_json::json!({ "display": display, "dataUrl": data_url }),
        );
    }

    Ok(())
}

/// Um quadro JPEG de 1920x720 passa de poucas centenas de KB; 16 MiB é folga de sobra.
const TAMANHO_MAXIMO_DO_QUADRO: usize = 16 * 1024 * 1024;

/// A linha de status de uma resposta HTTP/1.x diz 200?
fn status_http_ok(linha: &str) -> bool {
    let mut partes = linha.split_whitespace();
    matches!((partes.next(), partes.next()), (Some(v), Some("200")) if v.starts_with("HTTP/1."))
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

#[tauri::command]
async fn start_mirror(
    car_ip: String,
    display: i32,
    fps: u32,
    quality: u32,
    max_width: u32,
    app: AppHandle,
    state: tauri::State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    // Garante que não fica mais de um loop de mirror rodando ao mesmo tempo (ex.: usuário troca
    // de display rapidamente na UI).
    if let Some(handle) = state.mirror_task.lock().await.take() {
        handle.abort();
    }

    exigir_ip_do_carro(&car_ip)?;
    let addr = format!("{}:{}", car_ip.trim(), GWLINK_SERVER_PORT);
    let path = format!(
        "/mirror?display={}&fps={}&quality={}&maxWidth={}",
        display, fps, quality, max_width
    );

    let app_for_task = app.clone();
    let handle = tokio::spawn(async move {
        if let Err(e) = run_mirror_loop(&addr, &path, display, &app_for_task).await {
            let _ = app_for_task.emit("mirror-error", e.to_string());
        }
        let _ = app_for_task.emit("mirror-stopped", ());
    });

    *state.mirror_task.lock().await = Some(handle);
    Ok(())
}

#[tauri::command]
async fn stop_mirror(state: tauri::State<'_, EstadoDaConexao>) -> Result<(), ErroDoInstalador> {
    if let Some(handle) = state.mirror_task.lock().await.take() {
        handle.abort();
    }
    Ok(())
}

#[tauri::command]
async fn remote_list_files(car_ip: String, path: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/files/list")?)
        .query(&[("path", path.as_str())])
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Usado tanto por download de arquivo quanto pelos dois backups — grava o corpo da resposta em
// disco conforme chega (sem bufferizar tudo em memória) e emite progresso incremental, já que o
// servidor no carro não manda Content-Length (resposta "close-delimited": o fim é o fechamento
// da conexão, não um tamanho anunciado).
async fn stream_response_to_file(
    mut resp: reqwest::Response,
    local_path: &str,
    app: &AppHandle,
    progress_event: &str,
) -> Result<(), ErroDoInstalador> {
    if !resp.status().is_success() {
        return Err(ErroDoInstalador::RemoteControlError(format!(
            "HTTP {} ao buscar o arquivo",
            resp.status()
        )));
    }
    let mut file = tokio::fs::File::create(local_path).await.map_err(ErroDoInstalador::Io)?;
    let mut downloaded: u64 = 0;
    while let Some(chunk) = resp
        .chunk()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?
    {
        file.write_all(&chunk).await.map_err(ErroDoInstalador::Io)?;
        downloaded += chunk.len() as u64;
        let _ = app.emit(progress_event, downloaded);
    }
    file.flush().await.map_err(ErroDoInstalador::Io)?;
    Ok(())
}

// O backup chega "close-delimited": se a conexão cai no meio, o fim do corpo parece normal e o
// arquivo truncado seria dado como salvo. Um tar inteiro termina com blocos zerados; sem eles o
// backup está incompleto, e o arquivo é removido para não parecer bom.
async fn stream_backup_to_file(
    resp: reqwest::Response,
    local_path: &str,
    app: &AppHandle,
) -> Result<(), ErroDoInstalador> {
    stream_response_to_file(resp, local_path, app, "backup-progress").await?;
    if !tar_termina_inteiro(local_path) {
        let _ = std::fs::remove_file(local_path);
        return Err(ErroDoInstalador::RemoteControlError(
            "O backup veio incompleto (a conexão com o carro caiu no meio). O arquivo parcial foi apagado; tente de novo."
                .to_string(),
        ));
    }
    Ok(())
}

// Um tar completo acaba com pelo menos dois blocos de 512 bytes zerados.
fn tar_termina_inteiro(caminho: &str) -> bool {
    use std::io::{Read, Seek, SeekFrom};
    let Ok(mut arquivo) = std::fs::File::open(caminho) else {
        return false;
    };
    let Ok(tamanho) = arquivo.metadata().map(|m| m.len()) else {
        return false;
    };
    if tamanho < 1024 || arquivo.seek(SeekFrom::Start(tamanho - 1024)).is_err() {
        return false;
    }
    let mut fim = [0u8; 1024];
    arquivo.read_exact(&mut fim).is_ok() && fim.iter().all(|b| *b == 0)
}

// Print da tela do carro em resolução cheia (Alpha 7.6), gravada onde o usuário escolheu. O
// espelhamento manda JPEG reduzido; a print vem do `/screenshot` do GwLinkServer, PNG cru do
// `screencap`, sem perda.
#[tauri::command]
async fn remote_screenshot_save(
    car_ip: String,
    display: i32,
    local_path: String,
    app: AppHandle,
) -> Result<(), ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .get(remote_url(&car_ip, "/screenshot")?)
        .query(&[("display", display.to_string())])
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    stream_response_to_file(resp, &local_path, &app, "screenshot-progress").await
}

#[tauri::command]
async fn remote_download_file(
    car_ip: String,
    remote_path: String,
    local_path: String,
    app: AppHandle,
) -> Result<(), ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .get(remote_url(&car_ip, "/files/get")?)
        .query(&[("path", remote_path.as_str())])
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    stream_response_to_file(resp, &local_path, &app, "download-progress").await
}

#[tauri::command]
async fn remote_upload_file(
    car_ip: String,
    local_path: String,
    remote_path: String,
) -> Result<(), ErroDoInstalador> {
    let bytes = tokio::fs::read(&local_path).await.map_err(ErroDoInstalador::Io)?;
    let client = remote_control_client(true)?;
    let resp = client
        .post(remote_url(&car_ip, "/files/put")?)
        .query(&[("path", remote_path.as_str())])
        .body(bytes)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    if !resp.status().is_success() {
        return Err(ErroDoInstalador::RemoteControlError(format!(
            "Upload falhou: HTTP {}",
            resp.status()
        )));
    }
    Ok(())
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Lê um arquivo local qualquer (ex: imagem escolhida no editor de temas) e devolve em base64.
// Existe pra evitar precisar configurar o escopo do protocolo asset:// do Tauri só pra exibir
// uma imagem escolhida pelo usuário — o frontend monta uma data: URL a partir do retorno.
#[tauri::command]
async fn read_local_file_b64(path: String) -> Result<String, ErroDoInstalador> {
    let bytes = tokio::fs::read(&path).await.map_err(ErroDoInstalador::Io)?;
    Ok(BASE64_STANDARD.encode(&bytes))
}

// Espelho exato de `read_local_file_b64`: grava um arquivo local a partir de base64. O caminho
// vem do seletor nativo (`plugin-dialog`), ou seja, de uma pasta que o usuário escolheu na hora
// — é por isso que este comando não precisa (e não abre) escopo de fs nas capabilities.
//
// Existe porque o instalador não tinha NENHUMA primitiva de escrita em disco: dava para ler um
// arquivo local e mandar para o carro, mas não para salvar nada vindo do app (histórico de
// alterações, ponto de restauração, relatório).
#[tauri::command]
async fn write_local_file_b64(path: String, data_b64: String) -> Result<(), ErroDoInstalador> {
    let bytes = BASE64_STANDARD
        .decode(&data_b64)
        .map_err(|e| ErroDoInstalador::RemoteControlError(format!("base64 inválido: {}", e)))?;
    tokio::fs::write(&path, bytes).await.map_err(ErroDoInstalador::Io)?;
    Ok(())
}

// Mesmo destino de `remote_upload_file` (POST em /files/put), mas recebe o conteúdo já em
// memória (base64) em vez de um caminho de arquivo local — usado pelo editor "Criação de
// Temas" para empurrar o tema pro carro sem precisar materializar theme.xml/app.html/PNGs
// gerados no navegador como arquivos reais no disco do instalador primeiro.
#[tauri::command]
async fn remote_upload_bytes_b64(
    car_ip: String,
    remote_path: String,
    data_b64: String,
) -> Result<(), ErroDoInstalador> {
    let bytes = BASE64_STANDARD
        .decode(&data_b64)
        .map_err(|e| ErroDoInstalador::RemoteControlError(format!("base64 inválido: {}", e)))?;
    let client = remote_control_client(true)?;
    let resp = client
        .post(remote_url(&car_ip, "/files/put")?)
        .query(&[("path", remote_path.as_str())])
        .body(bytes)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    if !resp.status().is_success() {
        return Err(ErroDoInstalador::RemoteControlError(format!(
            "Upload falhou: HTTP {}",
            resp.status()
        )));
    }
    Ok(())
}

// Espelho de `remote_upload_bytes_b64`, do outro lado: apaga uma pasta de tema no carro.
//
// Recebe NOME DE PASTA e não caminho, de propósito. A rota `/files/delete` no carro também só
// aceita nome e resolve dentro de files/themes — um apagar-por-caminho numa rota sem autenticação
// na rede do veículo apagaria os dados do próprio app com uma chamada errada. O parâmetro se chama
// `folder` para que nenhuma tela possa passar "/data/..." achando que funcionaria.
#[tauri::command]
async fn remote_delete_theme(car_ip: String, folder: String) -> Result<(), ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .post(remote_url(&car_ip, "/files/delete")?)
        .query(&[("theme", folder.as_str())])
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let status = resp.status();
    if !status.is_success() {
        let corpo = resp.text().await.unwrap_or_default();
        return Err(ErroDoInstalador::RemoteControlError(format!(
            "Falha ao apagar o tema: HTTP {} {}",
            status, corpo
        )));
    }
    Ok(())
}

#[tauri::command]
async fn remote_backup_apps(
    car_ip: String,
    local_path: String,
    app: AppHandle,
) -> Result<(), ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .get(remote_url(&car_ip, "/backup/apps")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    stream_backup_to_file(resp, &local_path, &app).await
}

#[tauri::command]
async fn remote_backup_full(
    car_ip: String,
    local_path: String,
    app: AppHandle,
) -> Result<(), ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .get(remote_url(&car_ip, "/backup/full")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    stream_backup_to_file(resp, &local_path, &app).await
}

#[tauri::command]
async fn remote_list_apps(car_ip: String) -> Result<String, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let resp = client
        .get(remote_url(&car_ip, "/apps/list")?)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))
}

// Chamado antes de iniciar qualquer um dos três backups pra saber o total esperado — a resposta
// do backup em si é "close-delimited" (sem Content-Length), então sem isso a barra de progresso
// só teria os bytes já baixados, nunca uma porcentagem.
#[tauri::command]
async fn remote_backup_size(car_ip: String, kind: String, package: Option<String>) -> Result<u64, ErroDoInstalador> {
    let client = remote_control_client(false)?;
    let mut query = vec![("kind", kind)];
    if let Some(pkg) = &package {
        query.push(("package", pkg.clone()));
    }
    let resp = client
        .get(remote_url(&car_ip, "/backup/size")?)
        .query(&query)
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let status = resp.status();
    let body = resp.text().await.map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    let parsed: serde_json::Value = serde_json::from_str(&body)
        .map_err(|e| ErroDoInstalador::RemoteControlError(format!("resposta inválida: {}", e)))?;
    let ok = parsed.get("ok").and_then(|v| v.as_bool()).unwrap_or(false);
    if !status.is_success() || !ok {
        let err = parsed.get("error").and_then(|v| v.as_str()).unwrap_or(&body).to_string();
        return Err(ErroDoInstalador::RemoteControlError(err));
    }
    parsed
        .get("bytes")
        .and_then(|v| v.as_u64())
        .ok_or_else(|| ErroDoInstalador::RemoteControlError("resposta sem campo bytes".to_string()))
}

#[tauri::command]
async fn remote_backup_app(
    car_ip: String,
    package: String,
    local_path: String,
    app: AppHandle,
) -> Result<(), ErroDoInstalador> {
    let client = remote_control_client(true)?;
    let resp = client
        .get(remote_url(&car_ip, "/backup/app")?)
        .query(&[("package", package.as_str())])
        .send()
        .await
        .map_err(|e| ErroDoInstalador::RemoteControlError(e.to_string()))?;
    stream_backup_to_file(resp, &local_path, &app).await
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/// Tudo o que o frontend pode invocar com `invoke(...)` precisa estar nesta lista.
fn comandos_do_frontend() -> impl Fn(tauri::ipc::Invoke<tauri::Wry>) -> bool + Send + Sync + 'static {
    tauri::generate_handler![
            endereco_do_carro,
            conferir_wifi_do_carro,
            telnet::abrir_sessao_telnet,
            telnet::fechar_sessao_telnet,
            telnet::send_command,
            telnet::sessao_aberta,
            install_local_apk,
            install_bundled_deepgwt,
            install_and_start_shizuku,
            install_deepgwt_with_shizuku,
            reset_stealth_mode,
            restart_shizuku,
            uninstall_deepgwt,
            list_installed_packages,
            uninstall_package,
            gwlink_fetch_telemetry,
            gwlink_fetch_diagnostics,
            telnet_exec,
            remote_list_displays,
            remote_gwlink_telemetry,
            remote_gwlink_set,
            remote_profiles_list,
            remote_profile_select,
            remote_profile_save,
            remote_hud_theme_get,
            remote_ajustes_ler,
            remote_licenca_ler,
            remote_licenca_nivel_de_teste,
            remote_licenca_ativar,
            remote_licenca_avaliacao,
            remote_licenca_avaliacao_encerrar,
            remote_ajustes_aplicar,
            remote_ajustes_esperar,
            remote_temas_listar,
            remote_temas_ativar,
            remote_temas_rascunho,
            remote_read_file_b64,
            remote_hud_theme_apply,
            remote_profile_delete,
            remote_servico_list,
            remote_servico_add,
            remote_servico_delete,
            remote_dvr_find,
            remote_storage_info,
            remote_backup_size,
            remote_input_tap,
            remote_input_swipe,
            remote_input_key,
            remote_restart,
            start_mirror,
            stop_mirror,
            remote_list_files,
            remote_screenshot_save,
            remote_download_file,
            remote_upload_file,
            remote_upload_bytes_b64,
            remote_delete_theme,
            read_local_file_b64,
            write_local_file_b64,
            remote_backup_apps,
            remote_backup_full,
            remote_list_apps,
            remote_backup_app
        ]
}

/// Monta e abre a janela do instalador. Chamada por `main.rs`.
pub fn iniciar() {
    let registro = tauri_plugin_log::Builder::new().build();
    // Estado compartilhado: a sessão telnet, o servidor HTTP de instalação e o espelho de tela.
    let estado = EstadoDaConexao {
        sessao: Arc::new(Mutex::new(None)),
        http_server: Arc::new(Mutex::new(None)),
        mirror_task: Arc::new(Mutex::new(None)),
    };
    let contexto = tauri::generate_context!();

    let construtor = tauri::Builder::default();
    construtor
        .plugin(registro)
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .manage(estado)
        .invoke_handler(comandos_do_frontend())
        .run(contexto)
        .expect("o DeepGWT não conseguiu abrir a janela do instalador");
}

#[cfg(test)]
mod testes {
    use super::*;
    use std::net::Ipv4Addr;

    // O roteiro vai dentro de `echo -e '...'`: aspas simples nele quebrariam o comando no carro.
    #[test]
    fn o_roteiro_do_modo_sorrateiro_nao_tem_aspas_simples() {
        let roteiro = script_do_modo_sorrateiro();
        assert!(!roteiro.contains('\''), "{roteiro}");
        assert!(roteiro.contains("sed -i /stealthModeActive/s/true/false/"));
    }

    // O app esconde os dois ícones no modo sorrateiro; a saída tem de devolver os dois.
    #[test]
    fn a_saida_do_modo_sorrateiro_reabilita_os_dois_icones() {
        let roteiro = script_do_modo_sorrateiro();
        assert!(roteiro.contains("pm enable br.com.redesurftank.havalshisuku/br.com.deepgwt.LauncherAlias"));
        assert!(roteiro.contains("pm enable br.com.redesurftank.havalshisuku/br.com.deepgwt.UpdaterAlias"));
    }

    #[test]
    fn so_status_200_vira_fluxo_de_quadros() {
        assert!(status_http_ok("HTTP/1.1 200 OK\r\n"));
        assert!(status_http_ok("HTTP/1.0 200 OK"));
        assert!(!status_http_ok("HTTP/1.1 500 Internal Server Error\r\n"));
        assert!(!status_http_ok("HTTP/1.1 404 Not Found"));
        assert!(!status_http_ok("{\"ok\":false}"));
        assert!(!status_http_ok(""));
    }

    #[test]
    fn a_remocao_so_e_completa_quando_o_pm_saiu_com_zero() {
        assert_eq!(
            resumo_da_remocao("GT=0 SHIZUKU=0 MICROG=nenhum:0"),
            ResumoDaRemocao::Completa
        );
        assert_eq!(
            resumo_da_remocao("GT=0 SHIZUKU=0 MICROG=org.microg.gms:0"),
            ResumoDaRemocao::Completa
        );
        assert_eq!(
            resumo_da_remocao("GT=1 SHIZUKU=0 MICROG=nenhum:0"),
            ResumoDaRemocao::Parcial("DeepGWT".to_string())
        );
        assert_eq!(
            resumo_da_remocao("GT=0 SHIZUKU=1 MICROG=org.microg.gms:1"),
            ResumoDaRemocao::Parcial("Shizuku, microG".to_string())
        );
    }

    #[test]
    fn o_reinicio_recusado_pelo_carro_nao_vira_enviado() {
        assert_eq!(recusa_do_reinicio(true, r#"{"ok":true,"scheduled":true}"#), None);
        assert_eq!(
            recusa_do_reinicio(false, r#"{"ok":false,"error":"Não reiniciado: o carro está andando."}"#),
            Some("Não reiniciado: o carro está andando.".to_string())
        );
        assert_eq!(
            recusa_do_reinicio(true, r#"{"ok":false,"error":"Shizuku indisponível no carro"}"#),
            Some("Shizuku indisponível no carro".to_string())
        );
        assert!(recusa_do_reinicio(false, "").is_some());
    }

    #[test]
    fn tar_truncado_nao_e_backup() {
        let pasta = std::env::temp_dir().join(format!("deepgwt-tar-{}", std::process::id()));
        std::fs::create_dir_all(&pasta).unwrap();
        let inteiro = pasta.join("inteiro.tar");
        let mut bytes = vec![7u8; 2048];
        bytes.extend(vec![0u8; 1024]);
        std::fs::write(&inteiro, &bytes).unwrap();
        assert!(tar_termina_inteiro(inteiro.to_str().unwrap()));
        let cortado = pasta.join("cortado.tar");
        std::fs::write(&cortado, &bytes[..2500]).unwrap();
        assert!(!tar_termina_inteiro(cortado.to_str().unwrap()));
        let vazio = pasta.join("vazio.tar");
        std::fs::write(&vazio, b"").unwrap();
        assert!(!tar_termina_inteiro(vazio.to_str().unwrap()));
        assert!(!tar_termina_inteiro(pasta.join("nao-existe.tar").to_str().unwrap()));
        let _ = std::fs::remove_dir_all(&pasta);
    }

    // O caso que motivou a correção: a máquina está em 192.168.100.230 e o carro, no mesmo
    // Wi-Fi, em 192.168.100.35. A interface certa é a dessa faixa, não a "padrão".
    #[test]
    fn reconhece_a_interface_da_mesma_faixa() {
        assert!(mesma_sub_rede(
            Ipv4Addr::new(192, 168, 100, 230),
            Ipv4Addr::new(255, 255, 255, 0),
            Ipv4Addr::new(192, 168, 100, 35),
        ));
    }

    // E o caso oposto, que era o defeito: a interface padrão de casa não alcança o carro no
    // hotspot dele. Entregar esse IP ao `curl` do veículo aponta para um endereço morto.
    #[test]
    fn recusa_interface_de_outra_faixa() {
        assert!(!mesma_sub_rede(
            Ipv4Addr::new(192, 168, 100, 230),
            Ipv4Addr::new(255, 255, 255, 0),
            Ipv4Addr::new(192, 168, 33, 104),
        ));
    }

    // Alpha 9.4: só o hotspot do carro vale.
    #[test]
    fn so_a_faixa_do_hotspot_e_do_carro() {
        assert!(e_ip_do_carro("192.168.33.1"));
        assert!(e_ip_do_carro(" 192.168.33.177 "));
        assert!(!e_ip_do_carro("192.168.100.35"));
        assert!(!e_ip_do_carro("192.168.3.1"));
        assert!(!e_ip_do_carro("192.168.33.999"));
        assert!(!e_ip_do_carro(""));
    }

    #[test]
    fn maquina_na_rede_de_casa_nao_esta_no_wifi_do_carro() {
        assert!(!tem_endereco_do_carro(&[Ipv4Addr::new(192, 168, 100, 230), Ipv4Addr::new(127, 0, 0, 1)]));
        assert!(tem_endereco_do_carro(&[Ipv4Addr::new(192, 168, 33, 20)]));
    }

    #[test]
    fn mascara_zerada_nunca_casa() {
        assert!(!mesma_sub_rede(
            Ipv4Addr::new(10, 0, 0, 1),
            Ipv4Addr::new(0, 0, 0, 0),
            Ipv4Addr::new(10, 0, 0, 2),
        ));
    }

    #[test]
    fn dica_do_pm_separa_causa_do_carro_da_causa_do_apk() {
        assert!(dica_do_pm("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
            .unwrap()
            .contains("outra chave"));
        assert!(dica_do_pm("Failure [INSTALL_FAILED_OLDER_SDK]")
            .unwrap()
            .contains("API 28"));
        assert!(dica_do_pm("Success").is_none());
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
