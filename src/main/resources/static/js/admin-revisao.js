/**
 * Revisão de estabelecimentos: locais encontrados nas fontes públicas que
 * aguardam a conferência da administração. Cada card mostra o que a fonte
 * trouxe (e o que não trouxe), os alertas da importação e as ações:
 * Aprovar, Editar, Recusar e Excluir, além de mesclar possíveis duplicados.
 */
import { avisar, escapar, icone, normalizar, plural, seloCategoria } from './comum.js';

const POR_PAGINA = 30;

/** "IBGE - CNEFE (Censo 2022)" -> "IBGE (Censo 2022)"; "CNES (Ministério da Saúde)" -> "CNES". */
export function nomeCurtoDaFonte(fonte) {
  if (!fonte) return '';
  if (fonte.startsWith('IBGE')) return 'IBGE · Censo 2022';
  if (fonte.startsWith('CNES')) return 'CNES · Ministério da Saúde';
  return fonte;
}

/** Texto usado nas pesquisas da administração (nome, endereço, telefone e categoria). */
export function textoDeBusca(item) {
  return normalizar([item.nome, item.endereco, item.telefone, item.whatsapp, item.categoria?.nome, item.descricao]
    .filter(Boolean).join(' | '));
}

export function etiquetaDaSituacao(item) {
  if (item.situacao === 'PENDENTE') {
    return `<span class="etiqueta etiqueta--atencao">${icone('clock')} Aguardando revisão</span>`;
  }
  if (item.situacao === 'RECUSADO') {
    return `<span class="etiqueta etiqueta--perigo">${icone('ban')} Recusado</span>`;
  }
  if (!item.ativo) {
    return `<span class="etiqueta etiqueta--neutra">${icone('archive')} Arquivado</span>`;
  }
  return `<span class="etiqueta etiqueta--sucesso">${icone('circle-check')} No guia</span>`;
}

/** Bairro ou localidade: a parte do endereço depois do " - ". */
function localidade(item) {
  const partes = String(item.endereco ?? '').split(' - ');
  return partes.length > 1 ? partes[partes.length - 1].trim() : '';
}

export function criarRevisao(contexto) {
  const { estado, chamarApi, confirmar, abrirFormulario, atualizarNaLista, atualizarResumo } = contexto;
  const $ = (seletor) => document.querySelector(seletor);

  const lista = $('#lista-revisao');
  const resumo = $('#revisao-resumo');
  const selecionarTodos = $('#revisao-todos');
  const selecionados = new Set();
  let exibidos = POR_PAGINA;
  let visiveis = [];

  for (const [seletor, evento] of [['#revisao-busca', 'input'], ['#revisao-fonte', 'change'],
    ['#revisao-categoria', 'change'], ['#revisao-alerta', 'change'], ['#revisao-localidade', 'change']]) {
    $(seletor).addEventListener(evento, () => {
      exibidos = POR_PAGINA;
      selecionados.clear();
      renderizar();
    });
  }
  $('#revisao-botao-mais').addEventListener('click', () => {
    exibidos += POR_PAGINA;
    renderizar();
  });
  selecionarTodos.addEventListener('change', () => {
    for (const item of visiveis) {
      if (selecionarTodos.checked) selecionados.add(item.id); else selecionados.delete(item.id);
    }
    lista.querySelectorAll('[data-selecionar]').forEach((caixa) => { caixa.checked = selecionarTodos.checked; });
    atualizarBarraDeLote();
  });
  $('#lote-aprovar').addEventListener('click', () => executarEmLote('aprovar'));
  $('#lote-recusar').addEventListener('click', () => executarEmLote('recusar'));

  lista.addEventListener('change', (evento) => {
    const caixa = evento.target.closest('[data-selecionar]');
    if (caixa) {
      const id = Number(caixa.dataset.selecionar);
      if (caixa.checked) selecionados.add(id); else selecionados.delete(id);
      atualizarBarraDeLote();
      return;
    }
    const seletor = evento.target.closest('[data-categoria-rapida]');
    if (seletor) {
      trocarCategoria(Number(seletor.dataset.categoriaRapida), Number(seletor.value), seletor);
    }
  });
  lista.addEventListener('click', aoClicar);

  /* ---------------------------------------------------------------- */

  function pendentes() {
    return estado.estabelecimentos.filter((item) => item.situacao === 'PENDENTE');
  }

  function filtrar() {
    const termo = normalizar($('#revisao-busca').value);
    const fonte = $('#revisao-fonte').value;
    const categoriaId = $('#revisao-categoria').value;
    const alerta = $('#revisao-alerta').value;
    const bairro = $('#revisao-localidade').value;
    return pendentes()
      .filter((item) => !fonte || (item.fonteId ?? 'manual').startsWith(`${fonte}:`))
      .filter((item) => !categoriaId || String(item.categoria.id) === categoriaId)
      .filter((item) => !bairro || localidade(item) === bairro)
      .filter((item) => {
        switch (alerta) {
          case 'duplicado': return Boolean(item.duplicadoDe);
          case 'sem-localizacao': return item.latitude == null;
          case 'com-contato': return Boolean(item.telefone || item.whatsapp || item.site);
          case 'sem-alertas': return !item.duplicadoDe && item.latitude != null;
          default: return true;
        }
      })
      .filter((item) => !termo || textoDeBusca(item).includes(termo))
      .sort((a, b) => (Number(Boolean(b.duplicadoDe)) - Number(Boolean(a.duplicadoDe)))
        || a.nome.localeCompare(b.nome, 'pt-BR'));
  }

  function renderizar() {
    atualizarOpcoes();
    const todos = pendentes();
    const filtrados = filtrar();
    visiveis = filtrados.slice(0, exibidos);

    // seleção só dos que continuam aguardando revisão
    for (const id of [...selecionados]) {
      if (!todos.some((item) => item.id === id)) selecionados.delete(id);
    }

    if (todos.length === 0) {
      resumo.textContent = '';
      $('#barra-lote').hidden = true;
      $('#revisao-mais').hidden = true;
      lista.innerHTML = `
        <div class="vazio">
          <span class="vazio__icone">${icone('circle-check')}</span>
          <h3>Nada aguardando revisão</h3>
          <p>Para buscar estabelecimentos nas fontes públicas, use a aba "Importar dados".</p>
        </div>`;
      return;
    }

    $('#barra-lote').hidden = false;
    resumo.textContent = filtrados.length === todos.length
      ? `${plural(todos.length, 'local aguardando revisão', 'locais aguardando revisão')}`
      : `${plural(filtrados.length, 'local', 'locais')} de ${todos.length} aguardando revisão`;

    lista.innerHTML = visiveis.length
      ? visiveis.map(cartao).join('')
      : `<div class="vazio"><span class="vazio__icone">${icone('search')}</span><h3>Nenhum resultado</h3>
           <p>Nenhum local aguardando revisão corresponde aos filtros escolhidos.</p></div>`;

    const restantes = filtrados.length - visiveis.length;
    $('#revisao-mais').hidden = restantes <= 0;
    $('#revisao-botao-mais').innerHTML = `${icone('list')} Mostrar mais ${Math.min(restantes, POR_PAGINA)} de ${restantes}`;
    atualizarBarraDeLote();
  }

  /** Mantém as opções dos filtros de categoria e de bairro atualizadas. */
  function atualizarOpcoes() {
    const categoria = $('#revisao-categoria');
    const valorCategoria = categoria.value;
    categoria.innerHTML = contexto.opcoesDeCategoria(valorCategoria, 'Todas as categorias');

    const contagem = new Map();
    for (const item of pendentes()) {
      const nome = localidade(item);
      if (nome) contagem.set(nome, (contagem.get(nome) ?? 0) + 1);
    }
    const seletor = $('#revisao-localidade');
    const valor = seletor.value;
    const opcoes = [...contagem.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'pt-BR'));
    seletor.innerHTML = `<option value="">Todos os bairros e localidades</option>${opcoes
      .map(([nome, total]) => `<option value="${escapar(nome)}"${nome === valor ? ' selected' : ''}>${escapar(nome)} (${total})</option>`)
      .join('')}`;
  }

  function atualizarBarraDeLote() {
    const total = selecionados.size;
    $('#revisao-selecionados').textContent = total
      ? `${plural(total, 'selecionado', 'selecionados')}`
      : 'Nenhum selecionado';
    $('#lote-aprovar').disabled = total === 0;
    $('#lote-recusar').disabled = total === 0;
    selecionarTodos.checked = visiveis.length > 0 && visiveis.every((item) => selecionados.has(item.id));
  }

  /** Card de revisão: o que foi encontrado na fonte, alertas e ações. */
  function cartao(item) {
    const nome = escapar(item.nome);
    const linkFonte = item.urlFonte
      ? ` · <a href="${escapar(item.urlFonte)}" target="_blank" rel="noopener">ver na fonte ${icone('external-link')}</a>`
      : '';
    const localizacao = item.latitude != null
      ? `${icone('check')} Encontrada · <a href="https://www.openstreetmap.org/?mlat=${item.latitude}&mlon=${item.longitude}#map=18/${item.latitude}/${item.longitude}"
           target="_blank" rel="noopener">ver no mapa ${icone('external-link')}</a>`
      : `${icone('map-pin-off')} Pendente: a fonte não trouxe uma posição confiável`;

    const duplicado = item.duplicadoDe ? `
      <div class="alerta-revisao alerta-revisao--duplicado">
        ${icone('copy')}
        <div>
          <strong>Possível duplicado de "${escapar(item.duplicadoDe.nome)}"</strong>
          <span>${escapar(item.duplicadoDe.motivo)} · ${escapar(nomeCurtoDaFonte(item.duplicadoDe.fonte))}
            · ${item.duplicadoDe.situacao === 'PENDENTE' ? 'também aguardando revisão' : item.duplicadoDe.situacao === 'APROVADO' ? 'já está no guia' : 'recusado'}</span>
          ${item.duplicadoDe.endereco ? `<span>${icone('map-pin')} ${escapar(item.duplicadoDe.endereco)}</span>` : ''}
          <div class="alerta-revisao__acoes">
            ${item.duplicadoDe.situacao !== 'RECUSADO' ? `<button type="button" class="botao botao--secundario botao--pequeno" data-acao="mesclar" data-id="${item.id}">
              ${icone('git-merge')} Mesclar com este cadastro</button>` : ''}
            <button type="button" class="botao botao--fantasma botao--pequeno" data-acao="abrir-semelhante" data-id="${item.duplicadoDe.id}">
              ${icone('pencil')} Abrir o outro cadastro</button>
          </div>
        </div>
      </div>` : '';

    const observacoes = (item.observacoes ?? []).filter((texto) => !texto.startsWith('Possível duplicado'));
    const listaObservacoes = observacoes.length
      ? `<ul class="observacoes-revisao">${observacoes.map((texto) => `<li>${icone('info')}<span>${escapar(texto)}</span></li>`).join('')}</ul>`
      : '';

    return `
      <article class="cartao-revisao${selecionados.has(item.id) ? ' cartao-revisao--selecionado' : ''}" data-id="${item.id}">
        <div class="cartao-revisao__topo">
          <label class="cartao-revisao__selecao">
            <input type="checkbox" data-selecionar="${item.id}" ${selecionados.has(item.id) ? 'checked' : ''}>
            <span class="visualmente-oculto">Selecionar ${nome}</span>
          </label>
          <div class="cartao-revisao__titulo">
            <h3>${nome}</h3>
            <p class="cartao-revisao__meta">
              ${etiquetaDaSituacao(item)}
              <span>Fonte: ${escapar(nomeCurtoDaFonte(item.fonte))}${linkFonte}</span>
            </p>
          </div>
          <label class="cartao-revisao__categoria">
            <span>Categoria</span>
            <select class="seletor" data-categoria-rapida="${item.id}" aria-label="Categoria de ${nome}">
              ${contexto.opcoesDeCategoria(item.categoria.id, null)}
            </select>
          </label>
        </div>

        <dl class="dados-revisao">
          ${dado('Endereço', item.endereco)}
          ${dado('Telefone', item.telefone)}
          ${dado('WhatsApp', item.whatsapp)}
          ${dado('Site ou rede social', item.site)}
          ${dado('Horário', item.horario)}
          <div class="dados-revisao__item ${item.latitude != null ? 'dados-revisao__item--ok' : 'dados-revisao__item--falta'}">
            <dt>Localização</dt><dd>${localizacao}</dd>
          </div>
        </dl>

        ${item.descricao ? `<p class="cartao-revisao__descricao">${seloCategoria(item.categoria)} ${escapar(item.descricao)}</p>` : ''}
        ${duplicado}
        ${listaObservacoes}

        <div class="cartao-revisao__acoes">
          <button type="button" class="botao botao--primario botao--pequeno" data-acao="aprovar" data-id="${item.id}">
            ${icone('check')} Aprovar</button>
          <button type="button" class="botao botao--secundario botao--pequeno" data-acao="editar" data-id="${item.id}">
            ${icone('pencil')} Editar</button>
          <button type="button" class="botao botao--secundario botao--pequeno" data-acao="recusar" data-id="${item.id}">
            ${icone('ban')} Recusar</button>
          <button type="button" class="botao botao--fantasma botao--pequeno botao--excluir" data-acao="excluir" data-id="${item.id}">
            ${icone('trash-2')} Excluir</button>
        </div>
      </article>`;
  }

  function dado(rotulo, valor) {
    const encontrado = Boolean(valor);
    return `
      <div class="dados-revisao__item ${encontrado ? 'dados-revisao__item--ok' : 'dados-revisao__item--falta'}">
        <dt>${rotulo}</dt>
        <dd>${encontrado ? `${icone('check')} ${escapar(valor)}` : `${icone('x')} Não encontrado`}</dd>
      </div>`;
  }

  async function aoClicar(evento) {
    const botao = evento.target.closest('button[data-acao]');
    if (!botao) return;
    const id = Number(botao.dataset.id);
    const item = estado.estabelecimentos.find((estabelecimento) => estabelecimento.id === id);
    switch (botao.dataset.acao) {
      case 'aprovar':
      case 'recusar':
        contexto.executarAcao(botao.dataset.acao, item, botao);
        break;
      case 'editar':
        abrirFormulario(item);
        break;
      case 'excluir':
        contexto.excluirEstabelecimento(item);
        break;
      case 'mesclar':
        mesclar(item, botao);
        break;
      case 'abrir-semelhante':
        abrirFormulario(item ?? { id });
        break;
      default:
    }
  }

  async function mesclar(item, botao) {
    const confirmado = await confirmar({
      titulo: 'Mesclar com o cadastro existente',
      texto: `As informações que faltam em "${item.duplicadoDe.nome}" serão completadas com as de "${item.nome}", `
        + 'que será recusado (e não volta nas próximas importações). Nada do cadastro existente é sobrescrito.',
      botao: 'Mesclar',
      icone: 'git-merge',
      perigo: false,
    });
    if (!confirmado) return;
    botao.disabled = true;
    try {
      const destino = await chamarApi(`/admin/estabelecimentos/${item.id}/mesclar`, { metodo: 'POST' });
      atualizarNaLista({ ...item, situacao: 'RECUSADO' });
      atualizarNaLista(destino);
      avisar(`Mesclado com "${destino.nome}".`);
      atualizarResumo();
    } catch (erro) {
      botao.disabled = false;
      avisar(erro.message, 'erro');
    }
  }

  /** Troca rápida de categoria direto no card (salva na hora). */
  async function trocarCategoria(id, categoriaId, seletor) {
    const item = estado.estabelecimentos.find((estabelecimento) => estabelecimento.id === id);
    if (!item || !categoriaId) return;
    seletor.disabled = true;
    try {
      const salvo = await chamarApi(`/estabelecimentos/${id}`, {
        metodo: 'PUT',
        corpo: {
          nome: item.nome,
          categoriaId,
          descricao: item.descricao,
          endereco: item.endereco,
          telefone: item.telefone,
          whatsapp: item.whatsapp,
          site: item.site,
          contato: item.contato,
          horario: item.horario,
          latitude: item.latitude,
          longitude: item.longitude,
          localizacaoValidada: item.localizacaoValidada,
        },
      });
      atualizarNaLista(salvo);
      avisar(`Categoria de "${item.nome}" alterada para ${salvo.categoria.nome}.`);
    } catch (erro) {
      seletor.value = String(item.categoria.id);
      avisar(erro.message, 'erro');
    } finally {
      seletor.disabled = false;
    }
  }

  async function executarEmLote(acao) {
    const ids = [...selecionados];
    if (ids.length === 0) return;
    const aprovar = acao === 'aprovar';
    const confirmado = await confirmar({
      titulo: aprovar ? 'Aprovar selecionados' : 'Recusar selecionados',
      texto: aprovar
        ? `${plural(ids.length, 'estabelecimento será publicado', 'estabelecimentos serão publicados')} no guia. `
          + 'Confira antes se os nomes e as categorias estão corretos.'
        : `${plural(ids.length, 'estabelecimento será recusado', 'estabelecimentos serão recusados')} e não voltarão nas próximas importações.`,
      botao: aprovar ? `Aprovar ${ids.length}` : `Recusar ${ids.length}`,
      icone: aprovar ? 'check' : 'ban',
      perigo: !aprovar,
    });
    if (!confirmado) return;
    try {
      let alterados = 0;
      for (let inicio = 0; inicio < ids.length; inicio += 500) {
        const lote = ids.slice(inicio, inicio + 500);
        const resposta = await chamarApi('/admin/estabelecimentos/lote', { metodo: 'POST', corpo: { acao, ids: lote } });
        alterados += resposta.alterados;
      }
      for (const id of ids) {
        const item = estado.estabelecimentos.find((estabelecimento) => estabelecimento.id === id);
        if (item) {
          item.situacao = aprovar ? 'APROVADO' : 'RECUSADO';
          if (aprovar) item.duplicadoDe = null;
        }
      }
      selecionados.clear();
      contexto.renderizarListas();
      avisar(aprovar ? `${plural(alterados, 'estabelecimento aprovado', 'estabelecimentos aprovados')}.`
        : `${plural(alterados, 'estabelecimento recusado', 'estabelecimentos recusados')}.`);
      atualizarResumo();
    } catch (erro) {
      avisar(erro.message, 'erro');
    }
  }

  return { renderizar };
}
