import { api } from './api.js';
import {
  blocoErro, blocoVazio, cardEstabelecimento, escapar, esqueletos, icone, iniciarMenu,
  parametroDaUrl, plural, visualCategoria,
} from './comum.js';

const campoBusca = document.getElementById('busca');
const filtroCategorias = document.getElementById('filtro-categorias');
const resumo = document.getElementById('resumo');
const lista = document.getElementById('lista-estabelecimentos');

const filtros = {
  busca: parametroDaUrl('busca') ?? '',
  categoriaId: parametroDaUrl('categoria') ?? '',
};
let categorias = [];
let ultimaConsulta = 0;
let espera;

iniciarMenu();
campoBusca.value = filtros.busca;
iniciar();

async function iniciar() {
  campoBusca.addEventListener('input', () => {
    clearTimeout(espera);
    espera = setTimeout(() => {
      filtros.busca = campoBusca.value.trim();
      carregarEstabelecimentos();
    }, 250);
  });

  filtroCategorias.addEventListener('click', (evento) => {
    const chip = evento.target.closest('[data-categoria]');
    if (!chip) return;
    filtros.categoriaId = chip.dataset.categoria;
    renderizarFiltros();
    carregarEstabelecimentos();
  });

  lista.addEventListener('click', (evento) => {
    if (evento.target.closest('[data-limpar-filtros]')) {
      filtros.busca = '';
      filtros.categoriaId = '';
      campoBusca.value = '';
      renderizarFiltros();
      carregarEstabelecimentos();
    }
  });

  try {
    categorias = await api.listarCategorias();
  } catch {
    categorias = [];
  }
  if (!categorias.some((categoria) => String(categoria.id) === filtros.categoriaId)) {
    filtros.categoriaId = '';
  }
  renderizarFiltros();
  carregarEstabelecimentos();
}

function renderizarFiltros() {
  const todas = `
    <button type="button" class="chip" data-categoria="" aria-pressed="${filtros.categoriaId === ''}">
      ${icone('list')} Todas
    </button>`;
  const chips = categorias.map((categoria) => {
    const visual = visualCategoria(categoria);
    const selecionada = String(categoria.id) === filtros.categoriaId;
    return `
      <button type="button" class="chip" style="--cor-categoria:${visual.cor}"
              data-categoria="${categoria.id}" aria-pressed="${selecionada}">
        ${icone(visual.icone)} ${escapar(categoria.nome)}
      </button>`;
  });
  filtroCategorias.innerHTML = todas + chips.join('');
}

async function carregarEstabelecimentos() {
  const consulta = ++ultimaConsulta;
  atualizarUrl();
  lista.setAttribute('aria-busy', 'true');
  lista.innerHTML = esqueletos(6, 'card');
  resumo.textContent = 'Carregando...';

  try {
    const estabelecimentos = await api.listarEstabelecimentos({
      busca: filtros.busca,
      categoriaId: filtros.categoriaId,
    });
    if (consulta !== ultimaConsulta) return; // uma pesquisa mais recente já foi feita

    // sem resultados, a mensagem do estado vazio já explica a situação
    resumo.textContent = estabelecimentos.length ? descreverResultado(estabelecimentos.length) : '';
    lista.innerHTML = estabelecimentos.length
      ? estabelecimentos.map(cardEstabelecimento).join('')
      : estadoVazio();
  } catch (erro) {
    if (consulta !== ultimaConsulta) return;
    resumo.textContent = '';
    lista.innerHTML = blocoErro(erro.message);
  } finally {
    if (consulta === ultimaConsulta) lista.removeAttribute('aria-busy');
  }
}

function descreverResultado(total) {
  const categoria = categorias.find((item) => String(item.id) === filtros.categoriaId);
  let texto = plural(total, 'estabelecimento encontrado', 'estabelecimentos encontrados');
  if (categoria) texto += ` em ${categoria.nome}`;
  if (filtros.busca) texto += ` para "${filtros.busca}"`;
  return texto;
}

function estadoVazio() {
  if (!filtros.busca && !filtros.categoriaId) {
    return blocoVazio({
      icone: 'store',
      titulo: 'Nenhum estabelecimento cadastrado ainda',
      texto: 'Os estabelecimentos de Capitão Poço aparecerão aqui assim que forem cadastrados.',
    });
  }
  return blocoVazio({
    icone: 'search',
    titulo: 'Nenhum resultado encontrado',
    texto: 'Tente outro nome ou escolha outra categoria.',
    acao: `<button type="button" class="botao botao--secundario" data-limpar-filtros>${icone('x')} Limpar filtros</button>`,
  });
}

function atualizarUrl() {
  const parametros = new URLSearchParams();
  if (filtros.busca) parametros.set('busca', filtros.busca);
  if (filtros.categoriaId) parametros.set('categoria', filtros.categoriaId);
  const consulta = parametros.toString();
  history.replaceState(null, '', consulta ? `?${consulta}` : window.location.pathname);
}
