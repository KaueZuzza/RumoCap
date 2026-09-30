/**
 * Funções compartilhadas pelas páginas do RumoCap: ícones, categorias, cards,
 * contatos clicáveis e configuração do mapa.
 */

/** Centro de Capitão Poço (PA), usado quando ainda não há marcadores. */
export const CENTRO_CAPITAO_POCO = [-1.7447, -47.0638];

/** DDD de Capitão Poço, usado quando o telefone é cadastrado sem DDD. */
const DDD_PADRAO = '91';

const SPRITE_ICONES = 'img/icones.svg';

export function icone(nome, classe = '') {
  const classes = classe ? `icone ${classe}` : 'icone';
  return `<svg class="${classes}" aria-hidden="true" focusable="false"><use href="${SPRITE_ICONES}#${nome}"></use></svg>`;
}

const ENTIDADES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/** Protege textos vindos da API antes de inseri-los no HTML. */
export function escapar(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, (caractere) => ENTIDADES[caractere]);
}

/** Remove acentos e maiúsculas ("Saúde" vira "saude"). */
export function normalizar(texto) {
  return String(texto ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

export function parametroDaUrl(nome) {
  return new URLSearchParams(window.location.search).get(nome);
}

export function plural(quantidade, singular, pluralTexto) {
  return `${quantidade} ${quantidade === 1 ? singular : pluralTexto}`;
}

/* ---------- Aparência das categorias ---------- */

const VISUAL_CATEGORIAS = {
  'alimentacao': { icone: 'utensils', cor: '#c2410c' },
  'saude': { icone: 'stethoscope', cor: '#b91c1c' },
  'mercados': { icone: 'shopping-basket', cor: '#15803d' },
  'moda e beleza': { icone: 'shirt', cor: '#be185d' },
  'tecnologia': { icone: 'smartphone', cor: '#1d4ed8' },
  'servicos': { icone: 'briefcase', cor: '#6d28d9' },
  'casa e construcao': { icone: 'hammer', cor: '#b45309' },
  'automotivo': { icone: 'car', cor: '#0e7490' },
  'outros': { icone: 'layout-grid', cor: '#475569' },
};

/** Cores para categorias criadas depois pela área administrativa. */
const CORES_EXTRAS = ['#0f766e', '#7e22ce', '#9a3412', '#0369a1', '#be123c', '#4d7c0f'];

/** Ícone e cor de uma categoria. Categorias novas recebem o ícone "tag". */
export function visualCategoria(categoria) {
  const conhecido = VISUAL_CATEGORIAS[normalizar(categoria?.nome)];
  if (conhecido) {
    return conhecido;
  }
  const indice = Math.abs(Number(categoria?.id) || 0) % CORES_EXTRAS.length;
  return { icone: 'tag', cor: CORES_EXTRAS[indice] };
}

/* ---------- Componentes de HTML ---------- */

export function avatar(estabelecimento, tamanho = '') {
  const visual = visualCategoria(estabelecimento.categoria);
  const classes = tamanho ? `avatar avatar--${tamanho}` : 'avatar';
  const conteudo = estabelecimento.imagemUrl
    ? `<img src="${escapar(estabelecimento.imagemUrl)}" alt="" loading="lazy">`
    : icone(visual.icone);
  return `<span class="${classes}" style="--cor-categoria:${visual.cor}">${conteudo}</span>`;
}

export function seloCategoria(categoria, comLink = false) {
  const visual = visualCategoria(categoria);
  const conteudo = escapar(categoria.nome);
  if (comLink) {
    return `<a class="selo" style="--cor-categoria:${visual.cor}"
               href="estabelecimentos.html?categoria=${encodeURIComponent(categoria.id)}">${conteudo}</a>`;
  }
  return `<span class="selo" style="--cor-categoria:${visual.cor}">${conteudo}</span>`;
}

/** Card com as informações principais de um estabelecimento. */
export function cardEstabelecimento(estabelecimento) {
  const endereco = estabelecimento.endereco
    ? escapar(estabelecimento.endereco)
    : 'Endereço não informado';
  const descricao = estabelecimento.descricao
    ? `<p class="card__descricao">${escapar(estabelecimento.descricao)}</p>`
    : '';

  return `
    <article class="card">
      <a class="card__link" href="estabelecimento.html?id=${encodeURIComponent(estabelecimento.id)}">
        <div class="card__topo">
          ${avatar(estabelecimento)}
          <div class="card__titulo">
            <h3 class="card__nome">${escapar(estabelecimento.nome)}</h3>
            ${seloCategoria(estabelecimento.categoria)}
          </div>
        </div>
        ${descricao}
        <div class="card__rodape">
          <span class="card__endereco">${icone('map-pin')}<span>${endereco}</span></span>
          <span class="card__mais">Detalhes ${icone('chevron-right')}</span>
        </div>
      </a>
    </article>`;
}

export function esqueletos(quantidade, tipo) {
  return Array.from({ length: quantidade }, () => `<div class="esqueleto esqueleto--${tipo}"></div>`).join('');
}

export function blocoVazio({ icone: nomeIcone, titulo, texto, acao = '' }) {
  return `
    <div class="vazio">
      <span class="vazio__icone">${icone(nomeIcone)}</span>
      <h2>${escapar(titulo)}</h2>
      <p>${escapar(texto)}</p>
      ${acao}
    </div>`;
}

export function blocoErro(mensagem) {
  return `
    <div class="alerta" role="alert">
      ${icone('circle-alert')}
      <div>
        <strong>Não foi possível carregar as informações.</strong>
        <p>${escapar(mensagem)}</p>
      </div>
    </div>`;
}

/** Menu recolhível do celular. */
export function iniciarMenu() {
  const botao = document.querySelector('.menu-botao');
  const menu = document.getElementById('menu-principal');
  if (!botao || !menu) {
    return;
  }
  botao.addEventListener('click', () => {
    const aberto = menu.classList.toggle('aberto');
    botao.setAttribute('aria-expanded', String(aberto));
    botao.setAttribute('aria-label', aberto ? 'Fechar menu' : 'Abrir menu');
    botao.innerHTML = icone(aberto ? 'x' : 'menu');
  });
}

/** Mensagem rápida no canto da tela. */
export function avisar(mensagem, tipo = 'sucesso') {
  let area = document.getElementById('avisos');
  if (!area) {
    area = document.createElement('div');
    area.id = 'avisos';
    area.className = 'avisos';
    area.setAttribute('role', 'status');
    area.setAttribute('aria-live', 'polite');
    document.body.append(area);
  }
  const aviso = document.createElement('div');
  aviso.className = `aviso aviso--${tipo}`;
  aviso.innerHTML = `${icone(tipo === 'erro' ? 'circle-alert' : 'circle-check')}<span>${escapar(mensagem)}</span>`;
  area.append(aviso);
  setTimeout(() => aviso.remove(), tipo === 'erro' ? 6000 : 3500);
}

/* ---------- Contatos ---------- */

/**
 * Transforma o texto do campo "contato" em uma lista de contatos clicáveis.
 * Cada linha é um contato. Exemplos aceitos:
 *   Telefone: (91) 0000-0000   -> link "tel:"
 *   WhatsApp: (91) 90000-0000  -> link para o WhatsApp
 *   @perfil ou link do Instagram, e-mail, site
 */
export function interpretarContatos(texto) {
  if (!texto) {
    return [];
  }
  return texto
    .split(/\r?\n|;|\s\|\s|\s\/\s/)
    .map((parte) => parte.trim())
    .filter(Boolean)
    .map(interpretarContato);
}

function interpretarContato(original) {
  let rotulo = '';
  let valor = original;
  if (!/^(https?:\/\/|www\.)/i.test(original)) {
    const partes = original.match(/^([\p{L} .-]{2,24}):\s*(.+)$/u);
    if (partes) {
      rotulo = partes[1].trim();
      valor = partes[2].trim();
    }
  }
  const pista = normalizar(`${rotulo} ${original}`);

  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor)) {
    return { tipo: 'email', icone: 'mail', rotulo: rotulo || 'E-mail', texto: valor, href: `mailto:${valor}` };
  }

  if (/^(https?:\/\/|www\.)/i.test(valor)) {
    const link = valor.startsWith('www.') ? `https://${valor}` : valor;
    const textoLink = link.replace(/^https?:\/\/(www\.)?/i, '').replace(/\/$/, '');
    if (/instagram\.com/i.test(link)) {
      return { tipo: 'instagram', icone: 'instagram', rotulo: 'Instagram', texto: textoLink, href: link };
    }
    if (/facebook\.com|fb\.com/i.test(link)) {
      return { tipo: 'facebook', icone: 'facebook', rotulo: 'Facebook', texto: textoLink, href: link };
    }
    if (/wa\.me|whatsapp\.com/i.test(link)) {
      return { tipo: 'whatsapp', icone: 'whatsapp', rotulo: 'WhatsApp', texto: 'Conversar no WhatsApp', href: link };
    }
    return { tipo: 'site', icone: 'globe', rotulo: rotulo || 'Site', texto: textoLink, href: link };
  }

  const perfil = valor.match(/^@([\w.]{1,30})$/) || (/insta|face/.test(pista) && valor.match(/^([\w.]{1,30})$/));
  if (perfil) {
    const usuario = perfil[1];
    if (/face/.test(pista)) {
      return { tipo: 'facebook', icone: 'facebook', rotulo: 'Facebook', texto: usuario, href: `https://facebook.com/${usuario}` };
    }
    return { tipo: 'instagram', icone: 'instagram', rotulo: 'Instagram', texto: `@${usuario}`, href: `https://instagram.com/${usuario}` };
  }

  const telefone = interpretarTelefone(valor);
  if (telefone) {
    if (/whats|wpp|zap/.test(pista) && telefone.whatsapp) {
      return {
        tipo: 'whatsapp', icone: 'whatsapp', rotulo: 'WhatsApp',
        texto: telefone.formatado, href: telefone.whatsapp, hrefLigacao: telefone.href,
      };
    }
    return {
      tipo: 'telefone', icone: 'phone', rotulo: rotulo || 'Telefone',
      texto: telefone.formatado, href: telefone.href, hrefLigacao: telefone.href,
    };
  }

  return { tipo: 'texto', icone: 'info', rotulo, texto: valor, href: null };
}

function interpretarTelefone(texto) {
  const limpo = texto.replace(/\(?\s*(whats\s*app|wpp|zap)\s*\)?/gi, '').trim();
  if (!/^\+?[\d\s().-]{8,}$/.test(limpo)) {
    return null;
  }
  let numeros = limpo.replace(/\D/g, '');

  // 0800, 0300, 4004, 3003...: números nacionais, sem DDD e sem WhatsApp
  if (/^0[38]00\d{6,8}$/.test(numeros) || /^[34]00\d{5}$/.test(numeros)) {
    return { formatado: limpo, href: `tel:${numeros}`, whatsapp: null };
  }

  numeros = numeros.replace(/^0+/, '');
  if (/^55\d{10,11}$/.test(numeros)) {
    numeros = numeros.slice(2);
  }
  if (numeros.length === 8 || numeros.length === 9) {
    numeros = DDD_PADRAO + numeros;
  }
  if (numeros.length !== 10 && numeros.length !== 11) {
    return null;
  }

  const ddd = numeros.slice(0, 2);
  const local = numeros.slice(2);
  return {
    formatado: `(${ddd}) ${local.slice(0, -4)}-${local.slice(-4)}`,
    href: `tel:+55${numeros}`,
    whatsapp: `https://wa.me/55${numeros}`,
  };
}

/* ---------- Mapa (Leaflet + OpenStreetMap + satélite Esri) ---------- */

const CHAVE_CAMADA = 'rumocap.mapa.camada';
const ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services';

/**
 * Camadas reais do mapa, ambas gratuitas com atribuição:
 *  - ruas: OpenStreetMap (dados abertos, ODbL);
 *  - satélite: Esri World Imagery, com nomes de ruas e lugares por cima.
 */
function criarCamadas() {
  const ruas = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    className: 'camada-ruas',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>',
  });
  const satelite = L.layerGroup([
    // Em Capitão Poço há imagens até o zoom 17; acima disso elas são ampliadas.
    L.tileLayer(`${ESRI}/World_Imagery/MapServer/tile/{z}/{y}/{x}`, {
      maxZoom: 19,
      maxNativeZoom: 17,
      attribution: 'Imagens &copy; <a href="https://www.esri.com" target="_blank" rel="noopener">Esri</a>, Maxar, Earthstar Geographics',
    }),
    L.tileLayer(`${ESRI}/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}`, { maxZoom: 19, maxNativeZoom: 18 }),
    L.tileLayer(`${ESRI}/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`, { maxZoom: 19, maxNativeZoom: 18 }),
  ]);
  return { ruas, satelite };
}

function camadaSalva() {
  try {
    return localStorage.getItem(CHAVE_CAMADA) === 'satelite' ? 'satelite' : 'ruas';
  } catch {
    return 'ruas';
  }
}

/**
 * Adiciona o mapa de ruas/satélite e o seletor "Mapa | Satélite".
 * A última escolha fica salva no navegador.
 */
export function adicionarCamadaBase(mapa, { posicaoSeletor = 'topright' } = {}) {
  mapa.attributionControl.setPrefix('<a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>');
  const camadas = criarCamadas();
  let atual = null;
  let botoes = [];

  const selecionar = (nome) => {
    if (atual) {
      mapa.removeLayer(camadas[atual]);
    }
    atual = nome;
    camadas[nome].addTo(mapa);
    botoes.forEach((botao) => botao.setAttribute('aria-pressed', String(botao.dataset.camada === nome)));
    try {
      localStorage.setItem(CHAVE_CAMADA, nome);
    } catch {
      // sem armazenamento: apenas não lembra a escolha
    }
  };

  const Seletor = L.Control.extend({
    onAdd() {
      const caixa = L.DomUtil.create('div', 'leaflet-control controle-camadas');
      caixa.setAttribute('role', 'group');
      caixa.setAttribute('aria-label', 'Tipo de mapa');
      caixa.innerHTML = `
        <button type="button" data-camada="ruas">${icone('map')}<span>Mapa</span></button>
        <button type="button" data-camada="satelite">${icone('satellite')}<span>Satélite</span></button>`;
      botoes = [...caixa.querySelectorAll('button')];
      botoes.forEach((botao) => botao.addEventListener('click', () => selecionar(botao.dataset.camada)));
      L.DomEvent.disableClickPropagation(caixa);
      return caixa;
    },
  });
  new Seletor({ position: posicaoSeletor }).addTo(mapa);
  selecionar(camadaSalva());
  return { selecionar };
}

/** Controle com botões de ícone (ex.: centralizar, minha localização). */
export function adicionarBotoesMapa(mapa, botoes, posicao = 'topright') {
  const Controle = L.Control.extend({
    onAdd() {
      const caixa = L.DomUtil.create('div', 'leaflet-control controle-botoes');
      for (const { icone: nomeIcone, titulo, acao } of botoes) {
        const botao = L.DomUtil.create('button', '', caixa);
        botao.type = 'button';
        botao.title = titulo;
        botao.setAttribute('aria-label', titulo);
        botao.innerHTML = icone(nomeIcone);
        botao.addEventListener('click', acao);
      }
      L.DomEvent.disableClickPropagation(caixa);
      return caixa;
    },
  });
  new Controle({ position: posicao }).addTo(mapa);
}

/** Marcador no formato de pino, com a cor e o ícone da categoria. */
export function iconeMarcador(categoria) {
  const visual = visualCategoria(categoria);
  return L.divIcon({
    className: 'marcador',
    html: `<span class="marcador__pino" style="--cor-categoria:${visual.cor}">${icone(visual.icone)}</span>`,
    iconSize: [36, 44],
    iconAnchor: [18, 44],
    popupAnchor: [0, -40],
  });
}
