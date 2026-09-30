/**
 * Abas "Importar dados" (levantamento nas fontes públicas) e "Avisos"
 * (relatos de "informação incorreta" enviados pelos visitantes).
 */
import { avisar, blocoErro, escapar, formatarData, icone, plural } from './comum.js';

const FORMATO_HORA = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'America/Belem',
});

function dataEHora(iso) {
  if (!iso) return '';
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? '' : FORMATO_HORA.format(data);
}

/* ============================== Importação ============================== */

export function criarImportacao(contexto) {
  const { chamarApi, recarregarDados } = contexto;
  const listaFontes = document.getElementById('lista-fontes');
  const historico = document.getElementById('historico-importacoes');
  const emAndamento = new Set();

  listaFontes.addEventListener('click', (evento) => {
    const botao = evento.target.closest('[data-importar]');
    if (botao) importar(botao.dataset.importar);
  });

  async function carregar() {
    if (!listaFontes.innerHTML) {
      listaFontes.innerHTML = `<div class="carregando">${icone('loader-circle', 'girando')} Carregando...</div>`;
    }
    try {
      const [fontes, execucoes] = await Promise.all([
        chamarApi('/admin/importacoes/fontes'),
        chamarApi('/admin/importacoes'),
      ]);
      listaFontes.innerHTML = fontes.map(cartaoDaFonte).join('');
      historico.innerHTML = tabelaDoHistorico(execucoes, fontes);
    } catch (erro) {
      listaFontes.innerHTML = blocoErro(erro.message);
    }
  }

  function cartaoDaFonte(fonte) {
    const ultima = fonte.ultimaImportacao;
    const rodando = emAndamento.has(fonte.chave);
    return `
      <article class="cartao-fonte">
        <div class="cartao-fonte__cabecalho">
          <span class="cartao-fonte__icone">${icone(fonte.chave === 'cnes' ? 'stethoscope' : fonte.chave === 'osm' ? 'map' : 'database')}</span>
          <div>
            <h3>${escapar(fonte.nome)}</h3>
            <p class="cartao-fonte__licenca">${escapar(fonte.licenca)} ·
              <a href="${escapar(fonte.endereco)}" target="_blank" rel="noopener">sobre a fonte ${icone('external-link')}</a></p>
          </div>
        </div>
        <p class="cartao-fonte__descricao">${escapar(fonte.descricao)}</p>
        <p class="cartao-fonte__cadastros"><strong>${fonte.cadastros}</strong> ${fonte.cadastros === 1 ? 'cadastro veio' : 'cadastros vieram'} desta fonte</p>
        ${ultima ? resumoDaImportacao(ultima) : '<p class="cartao-fonte__nunca">Ainda não importada.</p>'}
        <button type="button" class="botao botao--primario" data-importar="${fonte.chave}" ${rodando ? 'disabled' : ''}>
          ${rodando ? `${icone('loader-circle', 'girando')} Importando...` : `${icone('refresh-cw')} ${ultima ? 'Importar de novo' : 'Importar agora'}`}
        </button>
      </article>`;
  }

  function resumoDaImportacao(importacao) {
    if (importacao.situacao === 'FALHOU') {
      return `
        <div class="resultado-importacao resultado-importacao--falha">
          <strong>${icone('circle-alert')} Última tentativa falhou (${dataEHora(importacao.iniciadaEm)})</strong>
          <p>${escapar(importacao.mensagem ?? '')}</p>
        </div>`;
    }
    if (importacao.situacao === 'EM_ANDAMENTO') {
      return `<div class="resultado-importacao"><strong>${icone('loader-circle', 'girando')} Importação em andamento...</strong></div>`;
    }
    const numeros = [
      ['novos', importacao.novos, 'novos aguardando revisão'],
      ['atualizados', importacao.atualizados, 'completados'],
      ['sem alteração', importacao.semAlteracao, 'sem alteração'],
      ['duplicados', importacao.possiveisDuplicados, 'possíveis duplicados'],
      ['sem localização', importacao.localizacaoPendente, 'com localização pendente'],
      ['recusados', importacao.jaRecusados, 'já recusados (ignorados)'],
    ].filter(([, valor]) => valor > 0);
    return `
      <div class="resultado-importacao">
        <strong>${icone('circle-check')} Última importação: ${dataEHora(importacao.iniciadaEm)}</strong>
        <p>${plural(importacao.encontrados, 'local encontrado', 'locais encontrados')} na fonte.</p>
        ${numeros.length ? `<ul class="resultado-importacao__numeros">${numeros
          .map(([, valor, rotulo]) => `<li><strong>${valor}</strong> ${rotulo}</li>`).join('')}</ul>` : ''}
        ${importacao.ignorados ? `
          <details class="resultado-importacao__descartes">
            <summary>${plural(importacao.ignorados, 'registro descartado', 'registros descartados')} pelos filtros</summary>
            <ul>${importacao.detalhes.map((linha) => `<li>${escapar(linha)}</li>`).join('')}</ul>
          </details>` : ''}
      </div>`;
  }

  function tabelaDoHistorico(execucoes, fontes) {
    if (execucoes.length === 0) {
      return '<p class="campo__ajuda">Nenhuma importação feita ainda.</p>';
    }
    const nomes = new Map(fontes.map((fonte) => [fonte.chave, fonte.nome]));
    return `
      <table class="tabela tabela--compacta">
        <thead><tr><th scope="col">Quando</th><th scope="col">Fonte</th><th scope="col">Resultado</th></tr></thead>
        <tbody>${execucoes.map((execucao) => `
          <tr>
            <td data-rotulo="Quando">${dataEHora(execucao.iniciadaEm)}</td>
            <td data-rotulo="Fonte">${escapar(nomes.get(execucao.fonte) ?? execucao.fonte)}</td>
            <td data-rotulo="Resultado">${execucao.situacao === 'FALHOU'
              ? `<span class="etiqueta etiqueta--perigo">${icone('circle-alert')} Falhou</span> ${escapar(execucao.mensagem ?? '')}`
              : `<span class="etiqueta etiqueta--sucesso">${icone('circle-check')} Concluída</span>
                 ${execucao.novos} novos, ${execucao.atualizados} completados, ${execucao.possiveisDuplicados} possíveis duplicados`}</td>
          </tr>`).join('')}</tbody>
      </table>`;
  }

  async function importar(chave) {
    if (emAndamento.has(chave)) return;
    emAndamento.add(chave);
    await carregar();
    try {
      const resultado = await chamarApi(`/admin/importacoes/${chave}`, { metodo: 'POST' });
      if (resultado.situacao === 'FALHOU') {
        avisar(`A importação não foi concluída: ${resultado.mensagem}`, 'erro');
      } else {
        avisar(`Importação concluída: ${plural(resultado.novos, 'novo local', 'novos locais')} para revisar.`);
        await recarregarDados();
      }
    } catch (erro) {
      avisar(erro.message, 'erro');
    } finally {
      emAndamento.delete(chave);
      await carregar();
    }
  }

  return { carregar };
}

/* ============================== Avisos ============================== */

export function criarAvisos(contexto) {
  const { chamarApi, abrirFormulario, atualizarResumo, confirmar, estado } = contexto;
  const lista = document.getElementById('lista-avisos');
  const mostrarResolvidos = document.getElementById('avisos-resolvidos');

  mostrarResolvidos.addEventListener('change', () => carregar());
  lista.addEventListener('click', async (evento) => {
    const botao = evento.target.closest('button[data-acao]');
    if (!botao) return;
    const id = Number(botao.dataset.id);
    if (botao.dataset.acao === 'abrir') {
      const item = estado.estabelecimentos.find((estabelecimento) => estabelecimento.id === Number(botao.dataset.estabelecimento));
      abrirFormulario(item ?? { id: Number(botao.dataset.estabelecimento) });
    } else if (botao.dataset.acao === 'resolver') {
      await acao(() => chamarApi(`/admin/relatos/${id}/resolver`, { metodo: 'POST' }), 'Aviso marcado como resolvido.');
    } else if (botao.dataset.acao === 'excluir') {
      const confirmado = await confirmar({
        titulo: 'Excluir aviso',
        texto: 'O aviso será apagado. Esta ação não pode ser desfeita.',
        botao: 'Excluir',
      });
      if (confirmado) {
        await acao(() => chamarApi(`/admin/relatos/${id}`, { metodo: 'DELETE' }), 'Aviso excluído.');
      }
    }
  });

  async function acao(executar, mensagem) {
    try {
      await executar();
      avisar(mensagem);
      await carregar();
      atualizarResumo();
    } catch (erro) {
      avisar(erro.message, 'erro');
    }
  }

  async function carregar() {
    lista.innerHTML = `<div class="carregando">${icone('loader-circle', 'girando')} Carregando...</div>`;
    try {
      const avisos = await chamarApi(`/admin/relatos?resolvidos=${mostrarResolvidos.checked}`);
      lista.innerHTML = avisos.length
        ? avisos.map(cartao).join('')
        : `<div class="vazio"><span class="vazio__icone">${icone('circle-check')}</span>
             <h3>${mostrarResolvidos.checked ? 'Nenhum aviso resolvido' : 'Nenhum aviso para conferir'}</h3>
             <p>Os visitantes podem avisar sobre dados errados pelo botão "Informação incorreta?" na página de cada estabelecimento.</p></div>`;
    } catch (erro) {
      lista.innerHTML = blocoErro(erro.message);
    }
  }

  function cartao(aviso) {
    return `
      <article class="cartao-aviso${aviso.resolvido ? ' cartao-aviso--resolvido' : ''}">
        <div class="cartao-aviso__topo">
          <strong>${escapar(aviso.estabelecimentoNome || 'Estabelecimento removido')}</strong>
          <span>${dataEHora(aviso.criadoEm)}${aviso.resolvido ? ` · resolvido em ${formatarData(aviso.resolvidoEm)}` : ''}</span>
        </div>
        <p class="cartao-aviso__mensagem">${escapar(aviso.mensagem)}</p>
        ${aviso.contato ? `<p class="cartao-aviso__contato">${icone('user')} Contato informado: ${escapar(aviso.contato)}</p>` : ''}
        <div class="cartao-aviso__acoes">
          <button type="button" class="botao botao--secundario botao--pequeno" data-acao="abrir" data-id="${aviso.id}"
                  data-estabelecimento="${aviso.estabelecimentoId}">${icone('pencil')} Corrigir o cadastro</button>
          ${aviso.resolvido ? '' : `<button type="button" class="botao botao--primario botao--pequeno" data-acao="resolver"
                  data-id="${aviso.id}">${icone('check')} Marcar como resolvido</button>`}
          <button type="button" class="botao botao--fantasma botao--pequeno botao--excluir" data-acao="excluir"
                  data-id="${aviso.id}">${icone('trash-2')} Excluir</button>
        </div>
      </article>`;
  }

  return { carregar };
}
