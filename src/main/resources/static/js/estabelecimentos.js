import { api } from './api.js';
import {
  blocoErro, blocoVazio, cardEstabelecimento, escapar, esqueletos, icone, iniciarMenu,
  parametroDaUrl, plural, visualCategoria,
} from './comum.js';

/** Quantos cards aparecem de cada vez (o restante vem no botão "Mostrar mais"). */
const POR_PAGINA = 60;

const campoBusca = document.getElementById('busca');
const filtroCategorias = document.getElementById('filtro-categorias');
const resumo = document.getElementById('resumo');
const lista = document.getElementById('lista-estabelecimentos');
const areaMais = document.getElementById('mais-resultados');
const botaoMais = document.getElementById('botao-mais');

const filtros = {
  busca: parametroDaUrl('busca') ?? '',
  categoriaId: parametroDaUrl('categoria') ?? '',
};
let categorias = [];
let resultados = [];
let exibidos = 0;
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

  botaoMais.addEventListener('click', () => mostrarMais());

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
  const todos = `
    <button type="button" class="chip" data-categoria="" aria-pressed="${filtros.categoriaId === ''}">
      ${icone('layout-grid')} Todos
    </button>`;
  const chips = categorias.map((categoria) => {
    const visual = visualCategoria(categoria);
    const selecionada = String(categoria.id) === filtros.categoriaId;
    return `
      <button type="button" class="chip" style="--cor-categoria:${visual.cor}"
              data-categoria="${categoria.id}" aria-pressed="${selecionada}">
        ${icone(visual.icone)} ${escapar(categoria.nome)}
        <span class="chip__total">${categoria.totalEstabelecimentos}</span>
      </button>`;
  });
  filtroCategorias.innerHTML = todos + chips.join('');
}

async function carregarEstabelecimentos() {
  const consulta = ++ultimaConsulta;
  atualizarUrl();
  lista.setAttribute('aria-busy', 'true');
  lista.innerHTML = esqueletos(6, 'card');
  areaMais.hidden = true;
  resumo.textContent = 'Carregando...';

  try {
    const encontrados = await api.listarEstabelecimentos({
      busca: filtros.busca,
      categoriaId: filtros.categoriaId,
    });
    if (consulta !== ultimaConsulta) return; // uma pesquisa mais recente já foi feita

    resultados = encontrados;
    exibidos = 0;
    // sem resultados, a mensagem do estado vazio já explica a situação
    resumo.textContent = resultados.length ? descreverResultado(resultados.length) : '';
    if (resultados.length) {
      lista.innerHTML = '';
      mostrarMais();
    } else {
      lista.innerHTML = estadoVazio();
    }
  } catch (erro) {
    if (consulta !== ultimaConsulta) return;
    resumo.textContent = '';
    lista.innerHTML = blocoErro(erro.message);
  } finally {
    if (consulta === ultimaConsulta) lista.removeAttribute('aria-busy');
  }
}

function mostrarMais() {
  const proximos = resultados.slice(exibidos, exibidos + POR_PAGINA);
  lista.insertAdjacentHTML('beforeend', proximos.map(cardEstabelecimento).join(''));
  exibidos += proximos.length;
  const restantes = resultados.length - exibidos;
  areaMais.hidden = restantes <= 0;
  botaoMais.innerHTML = `${icone('list')} Mostrar mais ${Math.min(restantes, POR_PAGINA)} de ${restantes}`;
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
      titulo: 'Nenhum estabelecimento no guia ainda',
      texto: 'Os estabelecimentos de Capitão Poço aparecem aqui depois de conferidos pela administração.',
    });
  }
  return blocoVazio({
    icone: 'search',
    titulo: 'Nenhum resultado encontrado',
    texto: 'Tente outro nome, um tipo de serviço (ex.: farmácia) ou escolha outra categoria.',
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
