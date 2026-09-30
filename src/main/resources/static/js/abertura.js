/**
 * Primeira abertura do RumoCap: tela de carregamento e, em seguida, a
 * apresentação do projeto (que o usuário pode ocultar para sempre).
 */

const CHAVE_SESSAO = 'rumocap.sessao.iniciada';
const CHAVE_OCULTAR = 'rumocap.apresentacao.oculta';

/** Tempo mínimo da tela de carregamento, para a animação não "piscar". */
const TEMPO_MINIMO = 900;
/** Tempo máximo: mesmo com a API lenta, a tela sai. */
const TEMPO_MAXIMO = 6000;

function ler(armazenamento, chave) {
  try {
    return armazenamento.getItem(chave);
  } catch {
    return null;
  }
}

function gravar(armazenamento, chave, valor) {
  try {
    armazenamento.setItem(chave, valor);
  } catch {
    // navegação privada ou armazenamento bloqueado: apenas não lembra a escolha
  }
}

const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

/**
 * @param {Promise} pronto resolvida quando a página terminou de carregar os dados
 */
export async function iniciarAbertura(pronto) {
  const tela = document.getElementById('abertura');
  if (!tela || document.documentElement.classList.contains('abertura-concluida')) {
    return;
  }

  await Promise.all([
    esperar(TEMPO_MINIMO),
    Promise.race([Promise.resolve(pronto).catch(() => {}), esperar(TEMPO_MAXIMO)]),
  ]);

  gravar(sessionStorage, CHAVE_SESSAO, '1');
  tela.classList.add('abertura--saindo');
  tela.addEventListener('transitionend', () => tela.remove(), { once: true });
  setTimeout(() => tela.remove(), 800);

  if (ler(localStorage, CHAVE_OCULTAR) !== '1') {
    mostrarApresentacao();
  }
}

function mostrarApresentacao() {
  const dialogo = document.getElementById('apresentacao');
  if (!dialogo || typeof dialogo.showModal !== 'function') {
    return;
  }
  const ocultar = document.getElementById('apresentacao-ocultar');
  const continuar = document.getElementById('apresentacao-continuar');

  const fechar = () => {
    if (ocultar.checked) {
      gravar(localStorage, CHAVE_OCULTAR, '1');
    }
    if (dialogo.open) {
      dialogo.close();
    }
  };

  continuar.addEventListener('click', fechar);
  dialogo.addEventListener('cancel', (evento) => { // tecla Esc
    evento.preventDefault();
    fechar();
  });

  dialogo.showModal();
  continuar.focus();
}
