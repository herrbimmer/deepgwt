// Só o macOS recebe o instalador; a lógica toda fica em `lib.rs`, onde os testes também a alcançam.
fn main() {
    opengw_instalador_lib::iniciar();
}
