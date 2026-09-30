import { api } from './api.js';
import {
  blocoErro, cardEstabelecimento, CENTRO_CAPITAO_POCO, criarGrupoDeMarcadores, escapar, esqueletos, icone,
  iconeMarcador, iniciarMenu, plural, visualCategoria,
} from './comum.js';
import { iniciarAbertura } from './abertura.js';

const QUANTIDADE_DESTAQUES = 6;
const estadoAbertura = document.getElementById('abertura-estado');

iniciarMenu();
const mapaCapa = criarMapaCapa();
// A tela de carregamento sai quando categorias e estabelecimentos terminam de carregar
// (ou depois de alguns segundos, se a API demorar): nada fica travado.
iniciarAbertura(Promise.allSettled([carregarCategorias(), carregarEstabelecimentos()]));

function informarEtapa(texto) {
  if (estadoAbertura) {
    estadoAbertura.textContent = texto;
  }
}

async function carregarCategorias() {
  const lista = document.getElementById('lista-categorias');
  lista.innerHTML = esqueletos(9, 'categoria');
  informarEtapa('Carregando as categorias...');

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

/** Números do guia, marcadores do mapa da capa e alguns estabelecimentos sorteados. */
async function carregarEstabelecimentos() {
  const secao = document.getElementById('secao-destaques');
  informarEtapa('Carregando os estabelecimentos...');
  try {
    const estabelecimentos = await api.listarEstabelecimentos();
    informarEtapa('Preparando o mapa...');
    mostrarNumeros(estabelecimentos);
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
  } finally {
    informarEtapa('Pronto.');
  }
}

function mostrarNumeros(estabelecimentos) {
  const numeros = document.getElementById('capa-numeros');
  if (estabelecimentos.length === 0) {
    numeros.innerHTML = `${icone('clipboard-check')} Guia em organização: os estabelecimentos aparecem aqui depois de conferidos.`;
    numeros.hidden = false;
    return;
  }
  const noMapa = estabelecimentos.filter((item) => item.latitude != null).length;
  const categorias = new Set(estabelecimentos.map((item) => item.categoria.id)).size;
  numeros.innerHTML = `
    <span><strong>${estabelecimentos.length}</strong> ${estabelecimentos.length === 1 ? 'estabelecimento' : 'estabelecimentos'}</span>
    <span><strong>${noMapa}</strong> no mapa</span>
    <span><strong>${categorias}</strong> ${categorias === 1 ? 'categoria' : 'categorias'}</span>`;
  numeros.hidden = false;
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

/** Marca os estabelecimentos do guia que têm localização, agrupando os próximos. */
function marcarNoMapa(estabelecimentos) {
  if (!mapaCapa) {
    return;
  }
  const localizados = estabelecimentos.filter((item) => item.latitude != null && item.longitude != null);
  if (localizados.length === 0) {
    return;
  }
  const grupo = criarGrupoDeMarcadores({ zoomToBoundsOnClick: false, spiderfyOnMaxZoom: false });
  for (const item of localizados) {
    L.marker([item.latitude, item.longitude], { icon: iconeMarcador(item.categoria), title: item.nome, keyboard: false })
      .on('click', () => { window.location.href = `estabelecimento.html?id=${encodeURIComponent(item.id)}`; })
      .addTo(grupo);
  }
  grupo.on('clusterclick', () => { window.location.href = 'mapa.html'; });
  grupo.addTo(mapaCapa);

  // Enquadra o centro da cidade, onde fica a maior parte dos estabelecimentos.
  const centro = localizados.filter((item) =>
    Math.abs(item.latitude - CENTRO_CAPITAO_POCO[0]) < 0.02 && Math.abs(item.longitude - CENTRO_CAPITAO_POCO[1]) < 0.02);
  const pontos = (centro.length ? centro : localizados).map((item) => [item.latitude, item.longitude]);
  if (pontos.length > 1) {
    mapaCapa.fitBounds(pontos, { padding: [30, 30], maxZoom: 16 });
  } else {
    mapaCapa.setView(pontos[0], 16);
  }
  document.getElementById('legenda-mapa-capa').textContent =
    `${plural(localizados.length, 'estabelecimento', 'estabelecimentos')} no mapa`;
}
