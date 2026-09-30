import { api } from './api.js';
import {
  adicionarCamadaBase, avatar, blocoErro, blocoVazio, escapar, esqueletos, formatarData, icone, iconeDoSite,
  iconeMarcador, iniciarMenu, interpretarContatos, linkTelefone, linkWhatsapp, parametroDaUrl, seloCategoria,
  textoDoSite,
} from './comum.js';

const area = document.getElementById('detalhe');
let estabelecimentoAtual = null;

iniciarMenu();
iniciarRelato();
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
    estabelecimentoAtual = estabelecimento;
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

  const telefone = linkTelefone(estabelecimento.telefone);
  const whatsapp = linkWhatsapp(estabelecimento.whatsapp);
  const temLocalizacao = estabelecimento.latitude != null && estabelecimento.longitude != null;

  const acoes = [
    telefone ? `<a class="botao botao--primario" href="${escapar(telefone)}">${icone('phone')} Ligar</a>` : '',
    whatsapp ? `<a class="botao botao--whatsapp" href="${escapar(whatsapp)}" target="_blank" rel="noopener">${icone('whatsapp')} WhatsApp</a>` : '',
    estabelecimento.site
      ? `<a class="botao botao--secundario" href="${escapar(estabelecimento.site)}" target="_blank" rel="noopener">${icone(iconeDoSite(estabelecimento.site))} ${rotuloDoSite(estabelecimento.site)}</a>`
      : '',
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
          ${listaDeInformacoes(estabelecimento)}
        </section>

        <section class="painel" aria-labelledby="titulo-localizacao">
          <h2 class="painel__titulo" id="titulo-localizacao">Localização</h2>
          ${temLocalizacao ? blocoMapa(estabelecimento) : blocoSemLocalizacao(estabelecimento)}
        </section>
      </div>

      ${blocoFonte(estabelecimento)}
    </article>`;

  if (temLocalizacao) {
    desenharMapa(estabelecimento);
  }
}

/** Só mostra o que foi informado: campos vazios não aparecem. */
function listaDeInformacoes(estabelecimento) {
  const itens = [];
  if (estabelecimento.endereco) {
    itens.push(informacao('map-pin', 'Endereço', escapar(estabelecimento.endereco)));
  }
  if (estabelecimento.telefone) {
    itens.push(informacao('phone', 'Telefone',
      `<a href="${escapar(linkTelefone(estabelecimento.telefone))}">${escapar(estabelecimento.telefone)}</a>`));
  }
  if (estabelecimento.whatsapp) {
    const link = linkWhatsapp(estabelecimento.whatsapp);
    itens.push(informacao('whatsapp', 'WhatsApp', link
      ? `<a href="${escapar(link)}" target="_blank" rel="noopener">${escapar(estabelecimento.whatsapp)}</a>`
      : escapar(estabelecimento.whatsapp)));
  }
  if (estabelecimento.site) {
    itens.push(informacao(iconeDoSite(estabelecimento.site), 'Site ou rede social',
      `<a href="${escapar(estabelecimento.site)}" target="_blank" rel="noopener">${escapar(textoDoSite(estabelecimento.site))}</a>`));
  }
  const outros = interpretarContatos(estabelecimento.contato);
  if (outros.length) {
    itens.push(informacao('mail', 'Outros contatos', listaContatos(outros)));
  }
  if (estabelecimento.horario) {
    itens.push(informacao('clock', 'Horário de funcionamento', escapar(estabelecimento.horario)));
  }

  if (itens.length === 0) {
    return `<p class="nao-informado">Ainda não há endereço, contato ou horário confirmados para este estabelecimento.</p>`;
  }
  const faltando = [
    !estabelecimento.telefone && !estabelecimento.whatsapp ? 'contato' : '',
    !estabelecimento.horario ? 'horário' : '',
  ].filter(Boolean);
  const nota = faltando.length
    ? `<p class="painel__nota">${icone('info')} Sem ${faltando.join(' e ')} informado${faltando.length > 1 ? 's' : ''} pela fonte.</p>`
    : '';
  return `<ul class="informacoes">${itens.join('')}</ul>${nota}`;
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

function listaContatos(contatos) {
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

function rotuloDoSite(site) {
  if (/instagram\.com/i.test(site)) return 'Instagram';
  if (/facebook\.com|fb\.com/i.test(site)) return 'Facebook';
  return 'Site';
}

function blocoMapa(estabelecimento) {
  const nota = estabelecimento.localizacaoValidada
    ? `<p class="painel__nota painel__nota--ok">${icone('shield-check')} Localização conferida pela administração do guia.</p>`
    : `<p class="painel__nota">${icone('info')} Posição informada pela fonte (${escapar(estabelecimento.fonte)}); pode ter pequenas diferenças.</p>`;
  return `
    <div class="mapa-mini" id="mapa-detalhe" role="region"
         aria-label="Mapa com a localização de ${escapar(estabelecimento.nome)}"></div>
    ${nota}
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
      <strong>Localização pendente</strong>
      <p>A posição exata deste estabelecimento ainda não foi confirmada, por isso ele não aparece no mapa.</p>
      ${busca}
    </div>`;
}

/** De onde vieram as informações, quando foram atualizadas e o botão "Informação incorreta?". */
function blocoFonte(estabelecimento) {
  const link = estabelecimento.urlFonte
    ? ` · <a href="${escapar(estabelecimento.urlFonte)}" target="_blank" rel="noopener">ver registro na fonte</a>`
    : '';
  const atualizado = estabelecimento.atualizadoEm
    ? ` · Atualizado no guia em ${formatarData(estabelecimento.atualizadoEm)}`
    : '';
  return `
    <aside class="fonte-dados" aria-label="Fonte dos dados">
      <div class="fonte-dados__texto">
        <span><strong>Fonte dos dados:</strong> ${escapar(estabelecimento.fonte)}${link}${atualizado}</span>
        <p>As informações vêm de fontes públicas e podem ter mudado. Confirme com o estabelecimento antes de ir.</p>
      </div>
      <button type="button" class="botao botao--secundario" data-relatar>
        ${icone('message-square-warning')} Informação incorreta?
      </button>
    </aside>`;
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
    texto: 'O endereço acessado não corresponde a nenhum estabelecimento do guia.',
    acao: `<a class="botao botao--primario" href="estabelecimentos.html">${icone('list')} Ver todos os estabelecimentos</a>`,
  });
}

/* ================== "Informação incorreta?" ================== */

function iniciarRelato() {
  const dialogo = document.getElementById('dialogo-relato');
  const formulario = document.getElementById('form-relato');
  if (!dialogo || !formulario) {
    return;
  }
  const corpo = document.getElementById('corpo-relato');
  const rodape = document.getElementById('rodape-relato');
  const conteudoOriginal = corpo.innerHTML;

  area.addEventListener('click', (evento) => {
    if (!evento.target.closest('[data-relatar]') || !estabelecimentoAtual) return;
    corpo.innerHTML = conteudoOriginal;
    rodape.hidden = false;
    document.getElementById('titulo-relato').textContent = 'Informação incorreta?';
    dialogo.showModal();
    document.getElementById('relato-mensagem').focus();
  });
  dialogo.querySelectorAll('[data-fechar]').forEach((botao) => botao.addEventListener('click', () => dialogo.close()));

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const campos = formulario.elements;
    const mensagem = campos.mensagem.value.trim();
    limparErrosDoRelato(formulario);
    if (mensagem.length < 10) {
      mostrarErroDoCampo(formulario, 'mensagem', 'Escreva pelo menos 10 caracteres.');
      campos.mensagem.focus();
      return;
    }
    const botao = document.getElementById('botao-enviar-relato');
    botao.disabled = true;
    try {
      await api.enviarRelato(estabelecimentoAtual.id, {
        mensagem,
        contato: campos.contato.value.trim(),
        site: campos.site.value,
      });
      corpo.innerHTML = `
        <div class="sucesso-envio" role="status">
          ${icone('circle-check')}
          <h3>Obrigado pelo aviso!</h3>
          <p>A administração do guia vai conferir a informação sobre ${escapar(estabelecimentoAtual.nome)}.</p>
          <button type="button" class="botao botao--primario" data-fechar-sucesso>Fechar</button>
        </div>`;
      rodape.hidden = true;
      corpo.querySelector('[data-fechar-sucesso]').addEventListener('click', () => dialogo.close());
    } catch (erro) {
      if (erro.campos && Object.keys(erro.campos).length) {
        Object.entries(erro.campos).forEach(([nome, texto]) => mostrarErroDoCampo(formulario, nome, texto));
      } else {
        const alerta = document.getElementById('erro-relato');
        alerta.innerHTML = `${icone('circle-alert')}<div>${escapar(erro.message)}</div>`;
        alerta.hidden = false;
      }
    } finally {
      botao.disabled = false;
    }
  });
}

function mostrarErroDoCampo(formulario, nome, texto) {
  const destino = formulario.querySelector(`[data-erro="${nome}"]`);
  if (destino) {
    destino.textContent = texto;
    destino.closest('.campo')?.classList.add('campo--erro');
  }
}

function limparErrosDoRelato(formulario) {
  formulario.querySelectorAll('[data-erro]').forEach((elemento) => { elemento.textContent = ''; });
  formulario.querySelectorAll('.campo--erro').forEach((elemento) => elemento.classList.remove('campo--erro'));
  const alerta = document.getElementById('erro-relato');
  if (alerta) alerta.hidden = true;
}
