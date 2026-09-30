import { api } from './api.js';
import {
  blocoErro, cardEstabelecimento, CENTRO_CAPITAO_POCO, escapar, esqueletos, icone, iconeMarcador, iniciarMenu,
  visualCategoria,
} from './comum.js';
import { iniciarAbertura } from './abertura.js';

const QUANTIDADE_DESTAQUES = 6;

iniciarMenu();
const mapaCapa = criarMapaCapa();
// A tela de carregamento sai quando categorias e destaques terminam de carregar.
iniciarAbertura(Promise.allSettled([carregarCategorias(), carregarDestaques()]));

async function carregarCategorias() {
  const lista = document.getElementById('lista-categorias');
  lista.innerHTML = esqueletos(9, 'categoria');

  try {
    const categorias = await api.listarCategorias();
    lista.innerHTML = categorias.map((categoria) => {
      const visual = visualCategoria(categoria);
      const total = categoria.totalEstabelecimentos;
      return `
        <a class="categoria" style="--cor-categoria:${visual.cor}"
           href="estabelecimentos.html?categoria=${encodeURIComponent(categoria.id)}">
          <span class="categoria__icone">${icone(visual.icone)}</span>
          <span class="categoria__texto">
            <span class="categoria__nome">${escapar(categoria.nome)}</span>
            <span class="categoria__total">${total}<span class="visualmente-oculto">
              ${total === 1 ? 'estabelecimento' : 'estabelecimentos'}</span></span>
          </span>
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
    marcarNoMapa(estabelecimentos);
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

/* ---------- Mapa da capa ---------- */

/** Mapa de ruas do centro da cidade (OpenStreetMap), só para ver; o mapa completo fica em mapa.html. */
function criarMapaCapa() {
  const figura = document.getElementById('capa-mapa');
  if (typeof L === 'undefined') {
    figura.hidden = true; // Leaflet não carregou: a capa fica só com o texto
    return null;
  }
  const mapa = L.map('mapa-capa', {
    zoomControl: false,
    scrollWheelZoom: false,
    dragging: false,
    touchZoom: false,
    doubleClickZoom: false,
    boxZoom: false,
    keyboard: false,
  }).setView(CENTRO_CAPITAO_POCO, 15);
  mapa.attributionControl.setPrefix(false);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    className: 'camada-ruas',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
  }).addTo(mapa);
  return mapa;
}

/** Marca os estabelecimentos que têm localização e enquadra todos eles. */
function marcarNoMapa(estabelecimentos) {
  if (!mapaCapa) {
    return;
  }
  const localizados = estabelecimentos.filter((item) => item.latitude != null && item.longitude != null);
  const pontos = localizados.map((item) => {
    const posicao = [item.latitude, item.longitude];
    L.marker(posicao, { icon: iconeMarcador(item.categoria), title: item.nome, keyboard: false })
      .on('click', () => { window.location.href = `estabelecimento.html?id=${encodeURIComponent(item.id)}`; })
      .addTo(mapaCapa);
    return posicao;
  });
  if (pontos.length > 1) {
    mapaCapa.fitBounds(pontos, { padding: [40, 40], maxZoom: 16 });
  } else if (pontos.length === 1) {
    mapaCapa.setView(pontos[0], 16);
  }
}
