//! Sessão telnet com a multimídia (busybox telnetd, porta 23).
//!
//! O soquete único fica em `EstadoDaConexao::sessao`. Este módulo reúne o que o abre, o fecha, o
//! consulta e escreve nele, mais as duas leituras por linha que os fluxos de instalação usam para
//! esperar o marcador de resultado.
//!
//! Estrutura e texto próprios do OPEN GW.

use std::time::Duration;

use tauri::{Emitter, State};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::time::timeout;

use crate::{ErroDoInstalador, EstadoDaConexao, HEAD_UNIT_TELNET_PORT};

/// Evento por onde cada linha lida (e cada comando enviado com eco) chega ao frontend.
const EVENTO_DE_SAIDA: &str = "telnet-output";

/// Tamanho de cada leitura do soquete.
const TAMANHO_DO_BLOCO: usize = 4096;

#[tauri::command]
pub(crate) async fn sessao_aberta(state: State<'_, EstadoDaConexao>) -> Result<bool, ErroDoInstalador> {
    Ok(state.sessao.lock().await.is_some())
}

/// Abre a sessão com a central. Recusa se já houver uma: quem quer reabrir fecha antes.
#[tauri::command]
pub(crate) async fn abrir_sessao_telnet(state: State<'_, EstadoDaConexao>) -> Result<(), ErroDoInstalador> {
    let mut sessao = state.sessao.lock().await;
    if sessao.is_some() {
        return Err(ErroDoInstalador::SessaoJaAberta);
    }

    let ip = crate::endereco_do_carro().await?;
    println!("telnet: abrindo sessão com {}:{}", ip, HEAD_UNIT_TELNET_PORT);
    *sessao = Some(TcpStream::connect((ip.as_str(), HEAD_UNIT_TELNET_PORT)).await?);
    Ok(())
}

/// Fecha a sessão, se houver. Soltar o `TcpStream` já encerra a conexão.
#[tauri::command]
pub(crate) async fn fechar_sessao_telnet(
    state: State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    if state.sessao.lock().await.take().is_some() {
        println!("telnet: sessão encerrada");
    }
    Ok(())
}

/// Manda uma linha de comando à central e não espera resposta.
///
/// Se a escrita falhar a sessão é descartada na hora: o telnetd do carro fecha o soquete por
/// inatividade ou quando outro fluxo reinicia a central, e sem isto `sessao_aberta` continuaria
/// dizendo "sim" para um soquete morto e toda tentativa seguinte repetiria o mesmo "Broken pipe".
#[tauri::command]
pub(crate) async fn send_command(
    command: String,
    state: State<'_, EstadoDaConexao>,
) -> Result<(), ErroDoInstalador> {
    let mut sessao = state.sessao.lock().await;
    let soquete = sessao.as_mut().ok_or(ErroDoInstalador::SemSessao)?;

    let mut quadro = command.into_bytes();
    quadro.push(b'\n');
    let enviado = async {
        soquete.write_all(&quadro).await?;
        soquete.flush().await
    }
    .await;

    if let Err(erro) = enviado {
        *sessao = None;
        return Err(ErroDoInstalador::Io(erro));
    }
    Ok(())
}

/// Como [send_command], mas antes mostra o comando ao usuário (`$ comando`) no registro.
pub(crate) async fn enviar_comando_com_eventos(
    command: String,
    state: State<'_, EstadoDaConexao>,
    app: &tauri::AppHandle,
) -> Result<(), ErroDoInstalador> {
    let _ = app.emit(EVENTO_DE_SAIDA, format!("$ {}", command));
    send_command(command, state).await
}

/// Lê a sessão linha a linha, publica cada linha não vazia em `telnet-output` e para na primeira
/// que começa com `prefixo`.
///
/// Devolve as linhas anteriores à do marcador (só se `guardar`) e a própria linha do marcador.
/// O que chegar depois dela, no mesmo bloco, é descartado. Em erro, EOF ou prazo estourado a
/// sessão é derrubada, para a próxima tentativa reconectar do zero em vez de reusar um soquete
/// que já morreu.
async fn ler_ate_prefixo(
    prefixo: &str,
    prazo_em_segundos: u64,
    guardar: bool,
    state: &State<'_, EstadoDaConexao>,
    app: &tauri::AppHandle,
) -> Result<(Vec<String>, String), ErroDoInstalador> {
    let leitura = async {
        let mut sessao = state.sessao.lock().await;
        let soquete = sessao.as_mut().ok_or(ErroDoInstalador::SemSessao)?;

        let mut pendente: Vec<u8> = Vec::new();
        let mut anteriores: Vec<String> = Vec::new();
        let mut bloco = [0u8; TAMANHO_DO_BLOCO];

        loop {
            let lidos = soquete.read(&mut bloco).await?;
            let fim_da_conexao = lidos == 0;
            pendente.extend_from_slice(&bloco[..lidos]);
            if fim_da_conexao && !pendente.is_empty() && pendente.last() != Some(&b'\n') {
                // Última linha sem quebra: ainda vale como linha.
                pendente.push(b'\n');
            }

            while let Some(fim) = pendente.iter().position(|&b| b == b'\n') {
                let bruta: Vec<u8> = pendente.drain(..=fim).collect();
                let linha = String::from_utf8_lossy(&bruta).trim().to_string();
                if linha.is_empty() {
                    continue;
                }
                let _ = app.emit(EVENTO_DE_SAIDA, linha.clone());
                if linha.starts_with(prefixo) {
                    return Ok((anteriores, linha));
                }
                if guardar {
                    anteriores.push(linha);
                }
            }

            if fim_da_conexao {
                return Err(ErroDoInstalador::SemSessao);
            }
        }
    };

    let resultado = match timeout(Duration::from_secs(prazo_em_segundos), leitura).await {
        Ok(r) => r,
        Err(_) => Err(ErroDoInstalador::SemResposta),
    };
    if resultado.is_err() {
        *state.sessao.lock().await = None;
    }
    resultado
}

/// Espera a linha que começa com `prefix` e a devolve; as anteriores são descartadas.
pub(crate) async fn wait_for_prefix(
    prefix: &str,
    timeout_secs: u64,
    state: &State<'_, EstadoDaConexao>,
    app: &tauri::AppHandle,
) -> Result<String, ErroDoInstalador> {
    ler_ate_prefixo(prefix, timeout_secs, false, state, app)
        .await
        .map(|(_, marcador)| marcador)
}

/// Espera a linha que começa com `prefix` e devolve tudo o que veio antes dela (sem ela). Serve
/// aos comandos que listam resultados, em vez de devolver só um código de saída.
pub(crate) async fn collect_until_prefix(
    prefix: &str,
    timeout_secs: u64,
    state: &State<'_, EstadoDaConexao>,
    app: &tauri::AppHandle,
) -> Result<Vec<String>, ErroDoInstalador> {
    ler_ate_prefixo(prefix, timeout_secs, true, state, app)
        .await
        .map(|(anteriores, _)| anteriores)
}

#[cfg(test)]
mod testes {
    use super::*;

    #[test]
    fn evento_de_saida_e_o_que_o_frontend_escuta() {
        // `ProgressPanel` e as telas de instalação assinam exatamente este nome.
        assert_eq!(EVENTO_DE_SAIDA, "telnet-output");
    }
}
