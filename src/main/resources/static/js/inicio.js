import { api } from './api.js';
import {
  blocoErro, cardEstabelecimento, escapar, esqueletos, icone, iniciarMenu, plural, visualCategoria,
} from './comum.js';
import { iniciarAbertura } from './abertura.js';

const QUANTIDADE_DESTAQUES = 6;

iniciarMenu();
// A tela de carregamento sai quando categorias e destaques terminam de carregar.
iniciarAbertura(Promise.allSettled([carregarCategorias(), carregarDestaques()]));

async function carregarCategorias() {
  const lista = document.getElementById('lista-categorias');
  lista.innerHTML = esqueletos(9, 'categoria');

  try {
    const categorias = await api.listarCategorias();
    lista.innerHTML = categorias.map((categoria) => {
      const visual = visualCategoria(categoria);
      const total = categoria.totalEstabelecimentos > 0
        ? plural(categoria.totalEstabelecimentos, 'estabelecimento', 'estabelecimentos')
        : 'Nenhum cadastrado';
      return `
        <a class="categoria" style="--cor-categoria:${visual.cor}"
           href="estabelecimentos.html?categoria=${encodeURIComponent(categoria.id)}">
          <span class="categoria__icone">${icone(visual.icone)}</span>
          <span class="categoria__texto">
            <span class="categoria__nome">${escapar(categoria.nome)}</span>
            <span class="categoria__total">${total}</span>
          </span>
          ${icone('chevron-right', 'categoria__seta')}
        </a>`;
    }).join('');
  } catch (erro) {
    lista.innerHTML = blocoErro(erro.message);
  } finally {
    lista.removeAttribute('aria-busy');
  }
}

/** Mostra alguns estabelecimentos (sorteados) quando já existirem cadastros. */
async function carregarDestaques() {
  const secao = document.getElementById('secao-destaques');
  try {
    const estabelecimentos = await api.listarEstabelecimentos();
    if (estabelecimentos.length === 0) {
      secao.hidden = true;
      return;
    }
    const selecionados = sortear(estabelecimentos, QUANTIDADE_DESTAQUES);
    document.getElementById('lista-destaques').innerHTML = selecionados.map(cardEstabelecimento).join('');
    secao.hidden = false;
  } catch {
    secao.hidden = true; // o erro já aparece na seção de categorias
  }
}

function sortear(lista, quantidade) {
  const copia = [...lista];
  for (let i = copia.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copia[i], copia[j]] = [copia[j], copia[i]];
  }
  return copia.slice(0, quantidade);
}
