import { api } from './api.js';
import {
  adicionarCamadaBase, avatar, blocoErro, blocoVazio, escapar, esqueletos, icone, iconeMarcador,
  iniciarMenu, interpretarContatos, parametroDaUrl, seloCategoria,
} from './comum.js';

const area = document.getElementById('detalhe');

iniciarMenu();
carregar();

async function carregar() {
  const id = parametroDaUrl('id');
  if (!id || !/^\d+$/.test(id)) {
    mostrarNaoEncontrado();
    return;
  }

  area.innerHTML = esqueletos(1, 'detalhe');
  try {
    const estabelecimento = await api.buscarEstabelecimento(id);
    renderizar(estabelecimento);
  } catch (erro) {
    if (erro.status === 404) {
      mostrarNaoEncontrado();
    } else {
      area.innerHTML = blocoErro(erro.message);
    }
  }
}

function renderizar(estabelecimento) {
  document.title = `${estabelecimento.nome} · RumoCap`;

  const contatos = interpretarContatos(estabelecimento.contato);
  const ligacao = contatos.find((contato) => contato.tipo === 'telefone')
    ?? contatos.find((contato) => contato.hrefLigacao);
  const whatsapp = contatos.find((contato) => contato.tipo === 'whatsapp');
  const temLocalizacao = estabelecimento.latitude != null && estabelecimento.longitude != null;

  const acoes = [
    ligacao ? `<a class="botao botao--primario" href="${escapar(ligacao.hrefLigacao)}">${icone('phone')} Ligar</a>` : '',
    whatsapp ? `<a class="botao botao--whatsapp" href="${escapar(whatsapp.href)}" target="_blank" rel="noopener">${icone('whatsapp')} WhatsApp</a>` : '',
    temLocalizacao ? `<a class="botao botao--secundario" href="${linkRota(estabelecimento)}" target="_blank" rel="noopener">${icone('navigation')} Como chegar</a>` : '',
  ].join('');

  area.innerHTML = `
    <article>
      <header class="detalhe__cabecalho">
        ${avatar(estabelecimento, 'grande')}
        <div>
          <h1 class="detalhe__titulo">${escapar(estabelecimento.nome)}</h1>
          ${seloCategoria(estabelecimento.categoria, true)}
          ${estabelecimento.descricao ? `<p class="detalhe__descricao">${escapar(estabelecimento.descricao)}</p>` : ''}
          ${acoes ? `<div class="detalhe__acoes">${acoes}</div>` : ''}
        </div>
      </header>

      <div class="detalhe__grade">
        <section class="painel" aria-labelledby="titulo-informacoes">
          <h2 class="painel__titulo" id="titulo-informacoes">Informações</h2>
          <ul class="informacoes">
            ${informacao('map-pin', 'Endereço', textoOuVazio(estabelecimento.endereco))}
            ${informacao('phone', 'Contato', listaContatos(contatos))}
            ${informacao('clock', 'Horário de funcionamento', textoOuVazio(estabelecimento.horario))}
          </ul>
        </section>

        <section class="painel" aria-labelledby="titulo-localizacao">
          <h2 class="painel__titulo" id="titulo-localizacao">Localização</h2>
          ${temLocalizacao ? blocoMapa(estabelecimento) : blocoSemLocalizacao(estabelecimento)}
        </section>
      </div>
    </article>`;

  if (temLocalizacao) {
    desenharMapa(estabelecimento);
  }
}

function informacao(nomeIcone, rotulo, valor) {
  return `
    <li class="informacao">
      <span class="informacao__icone">${icone(nomeIcone)}</span>
      <div>
        <div class="informacao__rotulo">${rotulo}</div>
        <div class="informacao__valor">${valor}</div>
      </div>
    </li>`;
}

function textoOuVazio(texto) {
  return texto ? escapar(texto) : '<span class="nao-informado">Não informado</span>';
}

function listaContatos(contatos) {
  if (contatos.length === 0) {
    return '<span class="nao-informado">Não informado</span>';
  }
  const itens = contatos.map((contato) => {
    const rotulo = contato.rotulo ? `<span class="contatos__rotulo">${escapar(contato.rotulo)}:</span>` : '';
    const externo = /^https?:/.test(contato.href ?? '') ? ' target="_blank" rel="noopener"' : '';
    const valor = contato.href
      ? `<a href="${escapar(contato.href)}"${externo}>${escapar(contato.texto)}</a>`
      : escapar(contato.texto);
    return `<li>${icone(contato.icone)} ${rotulo} ${valor}</li>`;
  });
  return `<ul class="contatos">${itens.join('')}</ul>`;
}

function blocoMapa(estabelecimento) {
  return `
    <div class="mapa-mini" id="mapa-detalhe" role="region"
         aria-label="Mapa com a localização de ${escapar(estabelecimento.nome)}"></div>
    <div class="painel__acoes">
      <a class="botao botao--secundario" href="${linkRota(estabelecimento)}" target="_blank" rel="noopener">
        ${icone('navigation')} Traçar rota
      </a>
      <a class="botao botao--fantasma" href="mapa.html?id=${encodeURIComponent(estabelecimento.id)}">
        ${icone('map')} Ver no mapa do guia
      </a>
    </div>`;
}

function blocoSemLocalizacao(estabelecimento) {
  const busca = estabelecimento.endereco
    ? `<a class="botao botao--secundario" target="_blank" rel="noopener"
          href="https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${estabelecimento.endereco}, Capitão Poço - PA`)}">
         ${icone('external-link')} Procurar o endereço no Google Maps
       </a>`
    : '';
  return `
    <div class="sem-localizacao">
      ${icone('map-pin-off')}
      <p>A localização no mapa ainda não foi cadastrada.</p>
      ${busca}
    </div>`;
}

function desenharMapa(estabelecimento) {
  const posicao = [estabelecimento.latitude, estabelecimento.longitude];
  const mapa = L.map('mapa-detalhe', { scrollWheelZoom: false }).setView(posicao, 17);
  adicionarCamadaBase(mapa);
  L.marker(posicao, { icon: iconeMarcador(estabelecimento.categoria), title: estabelecimento.nome }).addTo(mapa);
}

function linkRota(estabelecimento) {
  return `https://www.google.com/maps/dir/?api=1&destination=${estabelecimento.latitude},${estabelecimento.longitude}`;
}

function mostrarNaoEncontrado() {
  document.title = 'Estabelecimento não encontrado · RumoCap';
  area.innerHTML = blocoVazio({
    icone: 'search',
    titulo: 'Estabelecimento não encontrado',
    texto: 'O endereço acessado não corresponde a nenhum estabelecimento cadastrado.',
    acao: `<a class="botao botao--primario" href="estabelecimentos.html">${icone('list')} Ver todos os estabelecimentos</a>`,
  });
}
