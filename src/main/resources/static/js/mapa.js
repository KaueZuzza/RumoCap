import { api } from './api.js';
import {
  CENTRO_CAPITAO_POCO, adicionarBotoesMapa, adicionarCamadaBase, criarGrupoDeMarcadores, escapar, icone,
  iconeMarcador, iniciarMenu, normalizar, parametroDaUrl, plural, seloCategoria, visualCategoria,
} from './comum.js';
import { carregarMunicipio } from './municipio.js';

/** Retângulo do município (oeste, norte, leste, sul), usado se o limite do IBGE não carregar. */
const LIMITES_MUNICIPIO = [-47.5176, -1.5379, -46.9272, -2.5936];
const NOMINATIM = 'https://nominatim.openstreetmap.org/search';
const MAXIMO_NA_LISTA = 40;

const contagem = document.getElementById('contagem-mapa');
const filtro = document.getElementById('filtro-mapa');
const aviso = document.getElementById('aviso-mapa');
const formularioBusca = document.getElementById('busca-mapa');
const campoBusca = document.getElementById('texto-busca-mapa');
const botaoLimpar = document.getElementById('limpar-busca-mapa');
const resultados = document.getElementById('resultados-mapa');
const municipio = document.getElementById('municipio');
const municipioCorpo = document.getElementById('municipio-corpo');

const celular = window.matchMedia('(max-width: 860px)').matches;

const mapa = L.map('mapa', { zoomControl: false }).setView(CENTRO_CAPITAO_POCO, 15);
L.control.zoom({ position: 'bottomright', zoomInTitle: 'Aproximar', zoomOutTitle: 'Afastar' }).addTo(mapa);
adicionarCamadaBase(mapa, { posicaoSeletor: celular ? 'bottomleft' : 'topright' });
adicionarBotoesMapa(mapa, [
  { icone: 'compass', titulo: 'Centralizar em Capitão Poço', acao: () => mapa.setView(CENTRO_CAPITAO_POCO, 15) },
  { icone: 'locate-fixed', titulo: 'Mostrar minha localização', acao: mostrarMinhaLocalizacao },
], celular ? 'bottomright' : 'topright');

// Os estabelecimentos próximos são agrupados; ao aproximar o mapa, os grupos se abrem.
const camadaMarcadores = criarGrupoDeMarcadores().addTo(mapa);
const camadaApoio = L.layerGroup().addTo(mapa); // resultado de endereço e "você está aqui"
let limiteMunicipio = null;

let marcadores = [];           // todos os estabelecimentos do guia com localização (vêm da API)
const pinos = new Map();       // id -> marcador do Leaflet
let categorias = [];
let categoriaSelecionada = '';
let termoDaBusca = '';
let idsDaBusca = null;         // null = sem busca; Set = ids encontrados pela API
let semLocalizacaoNaBusca = 0; // encontrados pela busca, mas sem posição no mapa
let ultimaBusca = 0;
let ultimosEnderecos = [];

iniciarMenu();
iniciarBusca();
carregar();
mostrarMunicipio();

/* ============================ Estabelecimentos ============================ */

async function carregar() {
  contagem.textContent = 'Carregando...';
  let listaCategorias;
  try {
    // Os marcadores vêm da API: somente estabelecimentos do guia com latitude e longitude.
    [marcadores, listaCategorias] = await Promise.all([
      api.listarMarcadores(),
      api.listarCategorias().catch(() => null),
    ]);
  } catch (erro) {
    contagem.textContent = '';
    mostrarAviso('circle-alert', 'Não foi possível carregar o mapa.', erro.message);
    return;
  }

  for (const marcador of marcadores) {
    const pino = L.marker([marcador.latitude, marcador.longitude], {
      icon: iconeMarcador(marcador.categoria),
      title: marcador.nome,
      alt: marcador.nome,
    });
    pino.bindPopup(conteudoPopup(marcador));
    pinos.set(marcador.id, pino);
  }

  categorias = listaCategorias ?? categoriasDosMarcadores();
  filtro.addEventListener('click', (evento) => {
    const chip = evento.target.closest('[data-categoria]');
    if (!chip) return;
    categoriaSelecionada = chip.dataset.categoria;
    atualizar(true);
  });

  const destacado = marcadores.find((marcador) => String(marcador.id) === parametroDaUrl('id'));
  const buscaInicial = parametroDaUrl('busca');
  if (buscaInicial) {
    campoBusca.value = buscaInicial;
    await buscarEstabelecimentos(buscaInicial);
  } else {
    atualizar(!destacado);
  }
  if (destacado) {
    abrirMarcador(destacado.id);
  }
}

function categoriasDosMarcadores() {
  const unicas = new Map(marcadores.map((marcador) => [marcador.categoria.id, marcador.categoria]));
  return [...unicas.values()];
}

/** Estabelecimentos que passam pela busca (sem olhar a categoria). */
function daBusca() {
  return idsDaBusca ? marcadores.filter((marcador) => idsDaBusca.has(marcador.id)) : marcadores;
}

function visiveis() {
  return daBusca().filter((marcador) =>
    !categoriaSelecionada || String(marcador.categoria.id) === categoriaSelecionada);
}

/** Redesenha filtros, marcadores, contagem e lista de resultados. */
function atualizar(ajustarVisao) {
  renderizarFiltro();
  const lista = visiveis();

  camadaMarcadores.clearLayers();
  const novos = lista.map((marcador) => pinos.get(marcador.id));
  if (typeof camadaMarcadores.addLayers === 'function') {
    camadaMarcadores.addLayers(novos); // agrupamento: adiciona todos de uma vez
  } else {
    novos.forEach((pino) => pino.addTo(camadaMarcadores));
  }

  contagem.textContent = plural(lista.length, 'local no mapa', 'locais no mapa');
  atualizarAviso(lista.length);
  mostrarResultados(lista, null);
  if (ajustarVisao && lista.length > 0) {
    const limites = L.latLngBounds(lista.map((marcador) => [marcador.latitude, marcador.longitude]));
    mapa.fitBounds(limites, { padding: [70, 70], maxZoom: 17 });
  }
}

/** "Todos" e as categorias, com a quantidade de locais de cada uma (considerando a busca). */
function renderizarFiltro() {
  const base = daBusca();
  const totais = new Map();
  for (const marcador of base) {
    totais.set(marcador.categoria.id, (totais.get(marcador.categoria.id) ?? 0) + 1);
  }

  const chips = categorias.map((categoria) => {
    const visual = visualCategoria(categoria);
    return `
      <button type="button" class="chip" style="--cor-categoria:${visual.cor}" data-categoria="${categoria.id}"
              aria-pressed="${String(categoria.id) === categoriaSelecionada}">
        ${icone(visual.icone)} ${escapar(categoria.nome)} <span class="chip__total">${totais.get(categoria.id) ?? 0}</span>
      </button>`;
  });

  filtro.innerHTML = `
    <button type="button" class="chip" data-categoria="" aria-pressed="${categoriaSelecionada === ''}">
      ${icone('layout-grid')} Todos <span class="chip__total">${base.length}</span>
    </button>${chips.join('')}`;
}

function atualizarAviso(quantidadeVisivel) {
  if (marcadores.length === 0) {
    mostrarAviso('map-pin', 'Nenhum estabelecimento no mapa ainda.',
      'Os locais aparecem aqui depois de conferidos pela administração do guia.');
  } else if (quantidadeVisivel === 0 && idsDaBusca) {
    const extra = semLocalizacaoNaBusca
      ? ` ${plural(semLocalizacaoNaBusca, 'resultado ainda não tem', 'resultados ainda não têm')} localização: veja na lista de estabelecimentos.`
      : '';
    mostrarAviso('search', `Nenhum local encontrado no mapa para "${termoDaBusca}".`,
      `Tente outro nome ou tipo de serviço, ou toque em "Todos".${extra}`);
  } else if (quantidadeVisivel === 0) {
    const categoria = categorias.find((item) => String(item.id) === categoriaSelecionada);
    mostrarAviso('map-pin-off', `Nenhum local de ${categoria?.nome ?? 'esta categoria'} no mapa.`,
      'Escolha outra categoria ou volte para "Todos".');
  } else {
    aviso.hidden = true;
  }
}

function abrirMarcador(id) {
  const pino = pinos.get(id);
  if (!pino) return;
  if (!camadaMarcadores.hasLayer(pino)) {
    categoriaSelecionada = '';
    limparBusca(false);
    atualizar(false);
  }
  const abrir = () => {
    mapa.setView(pino.getLatLng(), Math.max(mapa.getZoom(), 18));
    pino.openPopup();
  };
  if (typeof camadaMarcadores.zoomToShowLayer === 'function') {
    camadaMarcadores.zoomToShowLayer(pino, () => pino.openPopup());
  } else {
    abrir();
  }
}

function conteudoPopup(marcador) {
  const endereco = marcador.endereco
    ? `<span class="popup__endereco">${icone('map-pin')}<span>${escapar(marcador.endereco)}</span></span>`
    : '';
  return `
    <div class="popup">
      ${seloCategoria(marcador.categoria)}
      <strong class="popup__nome">${escapar(marcador.nome)}</strong>
      ${endereco}
      <a class="popup__link" href="estabelecimento.html?id=${encodeURIComponent(marcador.id)}">
        Ver detalhes ${icone('chevron-right')}
      </a>
    </div>`;
}

function mostrarAviso(nomeIcone, titulo, texto) {
  aviso.innerHTML = `${icone(nomeIcone)}<div><strong>${escapar(titulo)}</strong><p>${escapar(texto)}</p></div>`;
  aviso.hidden = false;
}

/* ================================ Pesquisa ================================ */

/**
 * Digitando: a API procura no nome, na categoria e no endereço (com sinônimos,
 * como farmácia/drogaria) e o mapa mostra só os locais encontrados.
 * Ao buscar (Enter/botão): também procura ruas e locais no OpenStreetMap
 * (Nominatim), limitado ao município. O Nominatim só é chamado ao enviar,
 * conforme a política de uso gratuito do serviço.
 */
function iniciarBusca() {
  let espera;
  campoBusca.addEventListener('input', () => {
    botaoLimpar.hidden = !campoBusca.value;
    clearTimeout(espera);
    espera = setTimeout(() => buscarEstabelecimentos(campoBusca.value), 300);
  });
  botaoLimpar.addEventListener('click', () => {
    limparBusca(true);
    atualizar(true);
    campoBusca.focus();
  });
  formularioBusca.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    clearTimeout(espera);
    const termo = campoBusca.value.trim();
    await buscarEstabelecimentos(termo);
    if (termo.length < 2) {
      return;
    }
    mostrarResultados(visiveis(), 'carregando');
    try {
      mostrarResultados(visiveis(), await buscarEnderecos(termo));
    } catch {
      mostrarResultados(visiveis(), 'erro');
    }
  });
  resultados.addEventListener('click', (evento) => {
    const botao = evento.target.closest('[data-resultado]');
    if (!botao) return;
    const [tipo, indice] = botao.dataset.resultado.split(':');
    if (tipo === 'estabelecimento') {
      abrirMarcador(Number(indice));
    } else {
      irParaEndereco(ultimosEnderecos[Number(indice)]);
    }
    if (celular) {
      resultados.hidden = true;
    }
  });
}

async function buscarEstabelecimentos(texto) {
  const termo = texto.trim();
  const consulta = ++ultimaBusca;
  botaoLimpar.hidden = !termo;
  if (termo.length < 2) {
    if (idsDaBusca) {
      limparBusca(false);
      atualizar(true);
    }
    return;
  }
  try {
    const encontrados = await api.listarEstabelecimentos({ busca: termo });
    if (consulta !== ultimaBusca) return; // uma busca mais recente já foi feita
    termoDaBusca = termo;
    idsDaBusca = new Set(encontrados.map((item) => item.id));
    semLocalizacaoNaBusca = encontrados.filter((item) => item.latitude == null).length;
    ultimosEnderecos = [];
    atualizar(true);
  } catch (erro) {
    mostrarAviso('circle-alert', 'Não foi possível pesquisar agora.', erro.message);
  }
}

/** Volta a mostrar todos os estabelecimentos. */
function limparBusca(limparCampo) {
  idsDaBusca = null;
  termoDaBusca = '';
  semLocalizacaoNaBusca = 0;
  ultimosEnderecos = [];
  camadaApoio.clearLayers();
  if (limparCampo) {
    campoBusca.value = '';
  }
  botaoLimpar.hidden = !campoBusca.value;
}

function mostrarResultados(lista, enderecos) {
  if (!idsDaBusca && enderecos === null) {
    resultados.hidden = true;
    resultados.innerHTML = '';
    return;
  }
  const primeiros = lista.slice(0, MAXIMO_NA_LISTA);
  const blocoLocais = primeiros.length
    ? `<ul class="resultados__grupo">${primeiros.map((marcador) => `
        <li><button type="button" class="resultado" data-resultado="estabelecimento:${marcador.id}">
          ${icone(visualCategoria(marcador.categoria).icone)}
          <span class="resultado__texto">
            <span class="resultado__nome">${escapar(marcador.nome)}</span>
            <span class="resultado__sub">${escapar(marcador.categoria.nome)}${marcador.endereco ? ` · ${escapar(marcador.endereco)}` : ''}</span>
          </span>
        </button></li>`).join('')}</ul>
        ${lista.length > MAXIMO_NA_LISTA ? `<p class="resultados__mais">E mais ${lista.length - MAXIMO_NA_LISTA} no mapa.</p>` : ''}`
    : '<p class="resultados__nota">Nenhum estabelecimento do guia no mapa para esta busca.</p>';

  const listaCompleta = termoDaBusca
    ? `<a class="resultados__nota" href="estabelecimentos.html?busca=${encodeURIComponent(termoDaBusca)}">Ver os resultados em lista</a>`
    : '';

  let blocoEnderecos = '<p class="resultados__nota">Pressione Buscar para procurar também ruas e locais da cidade.</p>';
  if (enderecos === 'carregando') {
    blocoEnderecos = `<p class="resultados__nota">${icone('loader-circle', 'girando')} Procurando no mapa...</p>`;
  } else if (enderecos === 'erro') {
    blocoEnderecos = '<p class="resultados__nota">Não foi possível consultar o serviço de mapas agora.</p>';
  } else if (Array.isArray(enderecos)) {
    ultimosEnderecos = enderecos;
    blocoEnderecos = enderecos.length
      ? `<ul class="resultados__grupo">${enderecos.map((local, indice) => `
          <li><button type="button" class="resultado" data-resultado="endereco:${indice}">
            ${icone('map-pin')}
            <span class="resultado__texto">
              <span class="resultado__nome">${escapar(local.nome)}</span>
              <span class="resultado__sub">${escapar(local.descricao)}</span>
            </span>
          </button></li>`).join('')}</ul>`
      : '<p class="resultados__nota">Nenhuma rua ou local encontrado em Capitão Poço.</p>';
  }

  resultados.innerHTML = `
    <div><p class="mapa-painel__titulo-secao">Estabelecimentos encontrados</p>${blocoLocais}${listaCompleta}</div>
    <div><p class="mapa-painel__titulo-secao">Ruas e locais (OpenStreetMap)</p>${blocoEnderecos}</div>`;
  resultados.hidden = false;
}

async function buscarEnderecos(termo) {
  const [oeste, norte, leste, sul] = limiteMunicipio
    ? limitesDaCamada(limiteMunicipio)
    : LIMITES_MUNICIPIO;
  const parametros = new URLSearchParams({
    // O nome do município na pesquisa faz o Nominatim priorizar os endereços da cidade.
    q: `${termo}, Capitão Poço, Pará`,
    format: 'jsonv2',
    limit: '10',
    countrycodes: 'br',
    'accept-language': 'pt-BR',
    viewbox: `${oeste},${norte},${leste},${sul}`,
  });
  const resposta = await fetch(`${NOMINATIM}?${parametros}`);
  if (!resposta.ok) {
    throw new Error(`Nominatim respondeu ${resposta.status}`);
  }
  const lista = await resposta.json();
  return lista
    .filter((local) => normalizar(local.display_name).includes('capitao poco'))
    .slice(0, 6)
    .map((local) => {
      const partes = local.display_name.split(', ');
      const nome = local.name || partes[0];
      return {
        nome,
        descricao: partes.filter((parte) => parte !== nome && !IGNORAR_NO_ENDERECO.test(parte)).join(', '),
        latitude: Number(local.lat),
        longitude: Number(local.lon),
      };
    });
}

/** Partes repetitivas do endereço devolvido pelo Nominatim. */
const IGNORAR_NO_ENDERECO = /^(Pará|Região Norte|Brasil|\d{5}-?\d{3})$/;

function limitesDaCamada(camada) {
  const limites = camada.getBounds();
  return [limites.getWest(), limites.getNorth(), limites.getEast(), limites.getSouth()];
}

function irParaEndereco(local) {
  if (!local) return;
  camadaApoio.clearLayers();
  L.marker([local.latitude, local.longitude], {
    icon: L.divIcon({
      className: 'marcador marcador--local',
      html: `<span class="marcador__pino">${icone('map-pin')}</span>`,
      iconSize: [36, 44],
      iconAnchor: [18, 44],
      popupAnchor: [0, -40],
    }),
    title: local.nome,
  })
    .bindPopup(`<div class="popup"><strong class="popup__nome">${escapar(local.nome)}</strong>
      <span class="popup__endereco">${escapar(local.descricao)}</span></div>`)
    .addTo(camadaApoio)
    .openPopup();
  mapa.setView([local.latitude, local.longitude], 17);
}

/* ============================ Minha localização ============================ */

function mostrarMinhaLocalizacao() {
  if (!navigator.geolocation) {
    mostrarAviso('map-pin-off', 'Localização indisponível.', 'Este navegador não informa a localização.');
    return;
  }
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      camadaApoio.clearLayers();
      const posicao = [coords.latitude, coords.longitude];
      L.circle(posicao, { radius: coords.accuracy, weight: 1, color: '#0ea5e9', fillOpacity: 0.1 }).addTo(camadaApoio);
      L.circleMarker(posicao, { radius: 8, weight: 3, color: '#fff', fillColor: '#0ea5e9', fillOpacity: 1 })
        .bindPopup('<div class="popup"><strong class="popup__nome">Você está aqui</strong></div>')
        .addTo(camadaApoio);
      mapa.setView(posicao, 17);
    },
    () => mostrarAviso('map-pin-off', 'Não foi possível obter sua localização.',
      'Verifique se o navegador tem permissão para acessar a localização.'),
    { enableHighAccuracy: true, timeout: 10000 },
  );
}

/* ======================= Município (dados do IBGE) ======================= */

async function mostrarMunicipio() {
  if (!celular) {
    municipio.open = true;
  }
  try {
    const dados = await carregarMunicipio();
    if (dados.limite) {
      limiteMunicipio = L.geoJSON(dados.limite, {
        interactive: false,
        style: { className: 'limite-municipio', color: '#f97316', weight: 2.5, dashArray: '8 6', fillColor: '#14b8a6', fillOpacity: 0.04 },
      }).addTo(mapa);
      limiteMunicipio.bringToBack();
    }
    municipioCorpo.innerHTML = htmlMunicipio(dados);
    municipioCorpo.querySelector('[data-ver-limite]')?.addEventListener('click', () => {
      mapa.fitBounds(limiteMunicipio.getBounds(), { padding: [30, 30] });
    });
  } catch (erro) {
    municipioCorpo.innerHTML = `<p class="municipio__fonte">${escapar(erro.message)}</p>`;
  }
}

const numero = new Intl.NumberFormat('pt-BR');

function htmlMunicipio(dados) {
  const itens = [];
  if (dados.populacao !== null) {
    itens.push(['População (Censo 2022)', `${numero.format(dados.populacao)} habitantes`]);
  }
  if (dados.area !== null) {
    itens.push(['Área territorial', `${numero.format(Math.round(dados.area))} km²`]);
  }
  if (dados.densidade !== null) {
    itens.push(['Densidade', `${numero.format(dados.densidade)} hab/km²`]);
  }

  const regioes = [
    dados.regiaoImediata && `Imediata: ${dados.regiaoImediata}`,
    dados.regiaoIntermediaria && `Intermediária: ${dados.regiaoIntermediaria}`,
  ].filter(Boolean).join(' · ');

  return `
    <dl class="municipio__dados">
      ${itens.map(([rotulo, valor], indice) => `
        <div class="municipio__dado${indice === 0 ? ' municipio__dado--inteiro' : ''}"><dt>${escapar(rotulo)}</dt><dd>${escapar(valor)}</dd></div>`).join('')}
      ${regioes ? `<div class="municipio__dado municipio__dado--inteiro"><dt>Regiões geográficas</dt><dd>${escapar(regioes)}</dd></div>` : ''}
    </dl>
    ${dados.limite ? `
      <div class="municipio__acoes">
        <button type="button" class="botao botao--secundario" data-ver-limite>
          ${icone('maximize')} Ver limite do município
        </button>
      </div>` : ''}
    <p class="municipio__fonte">Fonte: IBGE. O limite aparece tracejado no mapa.</p>`;
}
