/**
 * Tema claro/escuro do RumoCap.
 *
 * Script comum (não é módulo) carregado no <head> de todas as páginas: aplica o
 * tema antes de a página ser desenhada, evitando o "piscar" do tema errado.
 * A escolha do usuário fica salva no navegador; sem escolha, segue o sistema.
 */
(function () {
  var CHAVE_TEMA = 'rumocap.tema';
  var CHAVE_SESSAO = 'rumocap.sessao.iniciada';
  var COR_BARRA = { light: '#173f2f', dark: '#0c1a13' };
  var raiz = document.documentElement;

  function ler(armazenamento, chave) {
    try { return armazenamento.getItem(chave); } catch (erro) { return null; }
  }

  function gravar(armazenamento, chave, valor) {
    try { armazenamento.setItem(chave, valor); } catch (erro) { /* navegação privada: só não lembra */ }
  }

  function temaDoSistema() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function aplicar(tema) {
    raiz.setAttribute('data-theme', tema);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', COR_BARRA[tema]);
    var botoes = document.querySelectorAll('[data-alternar-tema]');
    for (var i = 0; i < botoes.length; i++) {
      botoes[i].setAttribute('aria-label', tema === 'dark' ? 'Ativar modo claro' : 'Ativar modo escuro');
      botoes[i].setAttribute('title', tema === 'dark' ? 'Modo claro' : 'Modo escuro');
    }
  }

  var salvo = ler(localStorage, CHAVE_TEMA);
  aplicar(salvo === 'dark' || salvo === 'light' ? salvo : temaDoSistema());

  // A tela de carregamento aparece só na primeira abertura de cada sessão.
  if (ler(sessionStorage, CHAVE_SESSAO)) {
    raiz.classList.add('abertura-concluida');
  }

  // Sem escolha salva, acompanha a troca de tema do sistema.
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (evento) {
      if (!ler(localStorage, CHAVE_TEMA)) aplicar(evento.matches ? 'dark' : 'light');
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    aplicar(raiz.getAttribute('data-theme'));
  });

  document.addEventListener('click', function (evento) {
    var botao = evento.target.closest && evento.target.closest('[data-alternar-tema]');
    if (!botao) return;
    var novo = raiz.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    raiz.classList.add('trocando-tema');
    aplicar(novo);
    gravar(localStorage, CHAVE_TEMA, novo);
    window.setTimeout(function () { raiz.classList.remove('trocando-tema'); }, 350);
  });
})();
