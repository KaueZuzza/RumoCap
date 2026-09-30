/**
 * Área administrativa do RumoCap.
 * Todas as alterações (revisão, cadastro, edição, arquivamento, exclusão, categoria,
 * localização e imagem) são enviadas para a API e gravadas no PostgreSQL.
 *
 * Este módulo cuida da sessão, das abas, da lista de estabelecimentos, do formulário
 * e das categorias. A revisão fica em admin-revisao.js; importações e avisos, em admin-fontes.js.
 */
import { requisitar } from './api.js';
import {
  CENTRO_CAPITAO_POCO, adicionarCamadaBase, avatar, avisar, blocoErro, blocoVazio, escapar, formatarData, icone,
  iconeMarcador, normalizar, plural, seloCategoria, visualCategoria,
} from './comum.js';
import { criarRevisao, etiquetaDaSituacao, nomeCurtoDaFonte, textoDeBusca } from './admin-revisao.js';
import { criarAvisos, criarImportacao } from './admin-fontes.js';

const CHAVE_SESSAO = 'rumocap.admin';
const TAMANHO_MAXIMO = 5 * 1024 * 1024;
const LADO_MAXIMO_IMAGEM = 1200;
const TIPOS_DE_IMAGEM = ['image/png', 'image/jpeg', 'image/webp'];
const POR_PAGINA = 50;

const $ = (seletor) => document.querySelector(seletor);

const formulario = $('#form-estabelecimento');
const campos = formulario.elements;
const dialogoFormulario = $('#dialogo-estabelecimento');

const estado = {
  credencial: lerSessao(),
  categorias: [],
  estabelecimentos: [], // todos os cadastros, em qualquer situação
  resumo: null,
  editando: null, // estabelecimento aberto no formulário (null = novo cadastro)
  imagemNova: null, // arquivo escolhido e ainda não enviado
  removerImagem: false,
  urlPrevia: null,
  exibidos: POR_PAGINA,
};

let mapaFormulario = null;
let marcadorFormulario = null;

/** O que os outros módulos da administração podem usar. */
const contexto = {
  estado,
  chamarApi,
  confirmar,
  abrirFormulario,
  atualizarNaLista,
  removerDaLista,
  recarregarDados,
  atualizarResumo,
  opcoesDeCategoria,
  executarAcao,
  excluirEstabelecimento,
  renderizarListas,
};

const revisao = criarRevisao(contexto);
const importacao = criarImportacao(contexto);
const avisos = criarAvisos(contexto);

configurarEventos();
if (estado.credencial) {
  retomarSessao();
} else {
  mostrarLogin();
}

function configurarEventos() {
  $('#form-login').addEventListener('submit', entrar);
  $('#botao-sair').addEventListener('click', () => sair());

  document.querySelectorAll('[role="tab"]').forEach((aba) => {
    aba.addEventListener('click', () => selecionarAba(aba));
    aba.addEventListener('keydown', navegarEntreAbas);
  });

  for (const filtro of ['#filtro-texto', '#filtro-situacao', '#filtro-fonte', '#filtro-categoria']) {
    $(filtro).addEventListener(filtro === '#filtro-texto' ? 'input' : 'change', () => {
      estado.exibidos = POR_PAGINA;
      renderizarEstabelecimentos();
    });
  }
  $('#estabelecimentos-botao-mais').addEventListener('click', () => {
    estado.exibidos += POR_PAGINA;
    renderizarEstabelecimentos();
  });
  $('#botao-novo').addEventListener('click', () => abrirFormulario(null));
  $('#lista-estabelecimentos').addEventListener('click', aoClicarEmEstabelecimento);

  formulario.addEventListener('submit', salvarEstabelecimento);
  dialogoFormulario.querySelectorAll('[data-fechar]').forEach((botao) => {
    botao.addEventListener('click', () => dialogoFormulario.close());
  });
  dialogoFormulario.addEventListener('close', limparImagemTemporaria);
  campos.descricao.addEventListener('input', atualizarContador);
  campos.categoriaId.addEventListener('change', atualizarIconeDoMarcador);
  for (const campo of [campos.latitude, campos.longitude]) {
    campo.addEventListener('paste', colarCoordenadas);
    campo.addEventListener('change', sincronizarMarcadorComCampos);
  }
  $('#botao-minha-localizacao').addEventListener('click', usarMinhaLocalizacao);
  $('#botao-limpar-localizacao').addEventListener('click', limparLocalizacao);
  $('#botao-escolher-imagem').addEventListener('click', () => $('#campo-imagem').click());
  $('#campo-imagem').addEventListener('change', aoEscolherImagem);
  $('#botao-remover-imagem').addEventListener('click', marcarRemocaoDaImagem);

  $('#form-categoria').addEventListener('submit', criarCategoria);
  $('#lista-categorias-admin').addEventListener('click', aoClicarEmCategoria);

  document.querySelectorAll('[data-cancelar]').forEach((botao) => {
    botao.addEventListener('click', () => botao.closest('dialog').close('cancelar'));
  });
}

/* ============================== Sessão ============================== */

function lerSessao() {
  try {
    return sessionStorage.getItem(CHAVE_SESSAO);
  } catch {
    return null;
  }
}

function gravarSessao(valor) {
  try {
    if (valor) {
      sessionStorage.setItem(CHAVE_SESSAO, valor);
    } else {
      sessionStorage.removeItem(CHAVE_SESSAO);
    }
  } catch {
    // navegação privada: a sessão vale enquanto a página estiver aberta
  }
}

function credencialBasic(usuario, senha) {
  const bytes = new TextEncoder().encode(`${usuario}:${senha}`);
  return `Basic ${btoa(String.fromCharCode(...bytes))}`;
}

async function entrar(evento) {
  evento.preventDefault();
  const botao = evento.submitter ?? $('#form-login [type="submit"]');
  const erro = $('#erro-login');
  erro.hidden = true;
  const credencial = credencialBasic($('#login-usuario').value.trim(), $('#login-senha').value);

  botao.disabled = true;
  try {
    const sessao = await requisitar('/admin/sessao', { autorizacao: credencial });
    estado.credencial = credencial;
    gravarSessao(credencial);
    $('#login-senha').value = '';
    await abrirPainel(sessao.usuario);
  } catch (falha) {
    erro.textContent = falha.status === 401 ? 'Usuário ou senha incorretos.' : falha.message;
    erro.hidden = false;
  } finally {
    botao.disabled = false;
  }
}

async function retomarSessao() {
  try {
    const sessao = await requisitar('/admin/sessao', { autorizacao: estado.credencial });
    await abrirPainel(sessao.usuario);
  } catch {
    sair();
  }
}

function sair(mensagem) {
  estado.credencial = null;
  gravarSessao(null);
  document.querySelectorAll('dialog[open]').forEach((dialogo) => dialogo.close());
  mostrarLogin(mensagem);
}

function mostrarLogin(mensagem) {
  $('#painel').hidden = true;
  $('#acoes-sessao').hidden = true;
  $('#tela-login').hidden = false;
  const erro = $('#erro-login');
  erro.textContent = mensagem ?? '';
  erro.hidden = !mensagem;
  $('#login-usuario').focus();
}

async function abrirPainel(usuario) {
  $('#tela-login').hidden = true;
  $('#painel').hidden = false;
  $('#acoes-sessao').hidden = false;
  $('#usuario-logado').innerHTML = `${icone('user')} ${escapar(usuario)}`;
  await recarregarDados();
  // com locais aguardando revisão, a administração começa por eles
  selecionarAba(document.getElementById(estado.resumo?.pendentes ? 'aba-revisao' : 'aba-estabelecimentos'));
}

/** Requisição autenticada. Se o login deixar de valer, volta para a tela de entrada. */
async function chamarApi(caminho, opcoes = {}) {
  try {
    return await requisitar(caminho, { ...opcoes, autorizacao: estado.credencial });
  } catch (erro) {
    if (erro.status === 401) {
      sair('Sua sessão expirou. Entre novamente.');
    }
    throw erro;
  }
}

async function recarregarDados() {
  const lista = $('#lista-estabelecimentos');
  if (estado.estabelecimentos.length === 0) {
    lista.innerHTML = `<div class="carregando">${icone('loader-circle', 'girando')} Carregando...</div>`;
    $('#lista-revisao').innerHTML = `<div class="carregando">${icone('loader-circle', 'girando')} Carregando...</div>`;
  }
  try {
    const [categorias, estabelecimentos] = await Promise.all([
      chamarApi('/categorias'),
      chamarApi('/admin/estabelecimentos'),
      atualizarResumo(),
    ]);
    estado.categorias = categorias;
    estado.estabelecimentos = estabelecimentos;
    renderizarTudo();
  } catch (erro) {
    lista.innerHTML = blocoErro(erro.message);
    $('#lista-revisao').innerHTML = blocoErro(erro.message);
  }
}

function renderizarTudo() {
  renderizarCategorias();
  renderizarEstabelecimentos();
  revisao.renderizar();
}

/** Números do painel (aguardando revisão, no guia, duplicados e avisos). */
async function atualizarResumo() {
  try {
    estado.resumo = await chamarApi('/admin/resumo');
  } catch {
    return;
  }
  const resumo = estado.resumo;
  $('#total-revisao').textContent = resumo.pendentes;
  $('#total-estabelecimentos').textContent = resumo.publicados;
  $('#total-avisos').textContent = resumo.relatosAbertos;
  $('#resumo-admin').innerHTML = [
    numero('clipboard-check', resumo.pendentes, 'aguardando revisão', 'aba-revisao'),
    numero('store', resumo.publicados, 'no guia', 'aba-estabelecimentos'),
    numero('copy', resumo.possiveisDuplicados, 'possíveis duplicados', 'aba-revisao'),
    numero('map-pin-off', resumo.publicadosSemLocalizacao, 'no guia sem localização', 'aba-estabelecimentos'),
    numero('message-square-warning', resumo.relatosAbertos, resumo.relatosAbertos === 1 ? 'aviso a conferir' : 'avisos a conferir', 'aba-avisos'),
  ].join('');
}

function numero(nomeIcone, valor, rotulo, aba) {
  return `
    <button type="button" class="resumo-admin__item" data-ir-para="${aba}">
      ${icone(nomeIcone)}
      <span><strong>${valor}</strong> ${escapar(rotulo)}</span>
    </button>`;
}

$('#resumo-admin').addEventListener('click', (evento) => {
  const botao = evento.target.closest('[data-ir-para]');
  if (botao) {
    selecionarAba(document.getElementById(botao.dataset.irPara));
  }
});

/** Substitui um estabelecimento na lista local (depois de salvar, aprovar etc.). */
function atualizarNaLista(atualizado) {
  const indice = estado.estabelecimentos.findIndex((item) => item.id === atualizado.id);
  if (indice >= 0) {
    estado.estabelecimentos[indice] = { ...estado.estabelecimentos[indice], ...atualizado, dadosFonte: undefined };
  } else {
    estado.estabelecimentos.push(atualizado);
  }
  renderizarEstabelecimentos();
  revisao.renderizar();
}

function removerDaLista(id) {
  estado.estabelecimentos = estado.estabelecimentos.filter((item) => item.id !== id);
  renderizarListas();
}

/** Redesenha a revisão e a lista geral (depois de ações em lote). */
function renderizarListas() {
  renderizarEstabelecimentos();
  revisao.renderizar();
}

/* ============================== Abas ============================== */

function selecionarAba(aba) {
  if (!aba) return;
  document.querySelectorAll('[role="tab"]').forEach((outra) => {
    const ativa = outra === aba;
    outra.setAttribute('aria-selected', String(ativa));
    outra.tabIndex = ativa ? 0 : -1;
    document.getElementById(outra.getAttribute('aria-controls')).hidden = !ativa;
  });
  if (aba.id === 'aba-importar') importacao.carregar();
  if (aba.id === 'aba-avisos') avisos.carregar();
}

function navegarEntreAbas(evento) {
  if (evento.key !== 'ArrowRight' && evento.key !== 'ArrowLeft') {
    return;
  }
  const abas = [...document.querySelectorAll('[role="tab"]')];
  const passo = evento.key === 'ArrowRight' ? 1 : -1;
  const proxima = abas[(abas.indexOf(evento.currentTarget) + passo + abas.length) % abas.length];
  selecionarAba(proxima);
  proxima.focus();
}

/* ============================== Estabelecimentos ============================== */

function filtrarEstabelecimentos() {
  const termo = normalizar($('#filtro-texto').value);
  const situacao = $('#filtro-situacao').value;
  const fonte = $('#filtro-fonte').value;
  const categoriaId = $('#filtro-categoria').value;
  return estado.estabelecimentos
    .filter((item) => !situacao || atendeSituacao(item, situacao))
    .filter((item) => !fonte || fonteDoItem(item) === fonte)
    .filter((item) => !categoriaId || String(item.categoria.id) === categoriaId)
    .filter((item) => !termo || textoDeBusca(item).includes(termo))
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

function atendeSituacao(item, situacao) {
  if (situacao === 'PUBLICADO') return item.situacao === 'APROVADO' && item.ativo;
  if (situacao === 'ARQUIVADO') return item.situacao === 'APROVADO' && !item.ativo;
  return item.situacao === situacao;
}

function fonteDoItem(item) {
  return item.fonteId ? item.fonteId.split(':')[0] : 'manual';
}

function renderizarEstabelecimentos() {
  const lista = $('#lista-estabelecimentos');
  const resumo = $('#resumo-estabelecimentos');
  const mais = $('#estabelecimentos-mais');

  if (estado.estabelecimentos.length === 0) {
    resumo.textContent = '';
    mais.hidden = true;
    lista.innerHTML = blocoVazio({
      icone: 'store',
      titulo: 'Nenhum estabelecimento cadastrado',
      texto: 'Importe os dados das fontes públicas na aba "Importar dados" ou cadastre manualmente.',
      acao: `<button type="button" class="botao botao--primario" data-acao="novo">${icone('plus')} Novo estabelecimento</button>`,
    });
    return;
  }

  const filtrados = filtrarEstabelecimentos();
  resumo.textContent = plural(filtrados.length, 'estabelecimento', 'estabelecimentos');
  if (filtrados.length === 0) {
    mais.hidden = true;
    lista.innerHTML = blocoVazio({
      icone: 'search',
      titulo: 'Nenhum resultado',
      texto: 'Nenhum estabelecimento corresponde aos filtros escolhidos. Confira também a situação selecionada.',
    });
    return;
  }

  const pagina = filtrados.slice(0, estado.exibidos);
  lista.innerHTML = `
    <table class="tabela">
      <thead>
        <tr>
          <th scope="col">Estabelecimento</th>
          <th scope="col">Categoria</th>
          <th scope="col">Situação</th>
          <th scope="col">Localização</th>
          <th scope="col"><span class="visualmente-oculto">Ações</span></th>
        </tr>
      </thead>
      <tbody>${pagina.map(linhaDaTabela).join('')}</tbody>
    </table>`;
  const restantes = filtrados.length - pagina.length;
  mais.hidden = restantes <= 0;
  $('#estabelecimentos-botao-mais').innerHTML = `${icone('list')} Mostrar mais ${Math.min(restantes, POR_PAGINA)} de ${restantes}`;
}

function linhaDaTabela(item) {
  const nome = escapar(item.nome);
  const localizacao = item.latitude == null
    ? `<span class="situacao situacao--pendente">${icone('map-pin-off')} Pendente</span>`
    : item.localizacaoValidada
      ? `<span class="situacao situacao--ok">${icone('shield-check')} Conferida</span>`
      : `<span class="situacao">${icone('map-pin')} Da fonte</span>`;
  const endereco = item.endereco ? escapar(item.endereco) : 'Endereço não informado';
  const publico = item.situacao === 'APROVADO' && item.ativo;

  const acoes = [
    publico ? `<a class="botao botao--fantasma botao--icone" href="estabelecimento.html?id=${item.id}" target="_blank"
                  rel="noopener" title="Ver no site" aria-label="Ver ${nome} no site">${icone('external-link')}</a>` : '',
    botaoDeAcao('editar', item, 'pencil', 'Editar'),
    item.situacao === 'PENDENTE' || item.situacao === 'RECUSADO' ? botaoDeAcao('aprovar', item, 'check', 'Aprovar') : '',
    item.situacao === 'APROVADO' && item.ativo ? botaoDeAcao('arquivar', item, 'archive', 'Arquivar (tirar do guia sem apagar)') : '',
    item.situacao === 'APROVADO' && !item.ativo ? botaoDeAcao('reativar', item, 'archive-restore', 'Reativar (voltar para o guia)') : '',
    item.situacao !== 'PENDENTE' ? botaoDeAcao('revisar', item, 'rotate-ccw', 'Voltar para a revisão') : '',
    botaoDeAcao('excluir', item, 'trash-2', 'Excluir', 'botao--excluir'),
  ].join('');

  return `
    <tr>
      <td data-rotulo="Estabelecimento">
        <div class="tabela__estab">
          ${avatar(item, 'pequeno')}
          <div class="tabela__texto">
            <span class="tabela__nome">${nome}</span>
            <span class="tabela__sub">${endereco}</span>
            <span class="tabela__sub">${escapar(nomeCurtoDaFonte(item.fonte))}${item.relatosAbertos ? ` · ${icone('message-square-warning')} ${plural(item.relatosAbertos, 'aviso', 'avisos')}` : ''}</span>
          </div>
        </div>
      </td>
      <td data-rotulo="Categoria">${seloCategoria(item.categoria)}</td>
      <td data-rotulo="Situação">${etiquetaDaSituacao(item)}</td>
      <td data-rotulo="Localização">${localizacao}</td>
      <td class="tabela__acoes">${acoes}</td>
    </tr>`;
}

function botaoDeAcao(acao, item, nomeIcone, titulo, classeExtra = '') {
  return `<button type="button" class="botao botao--fantasma botao--icone ${classeExtra}" data-acao="${acao}"
                  data-id="${item.id}" title="${titulo}" aria-label="${titulo}: ${escapar(item.nome)}">${icone(nomeIcone)}</button>`;
}

async function aoClicarEmEstabelecimento(evento) {
  const botao = evento.target.closest('button[data-acao]');
  if (!botao) {
    return;
  }
  const id = Number(botao.dataset.id);
  const item = estado.estabelecimentos.find((estabelecimento) => estabelecimento.id === id);
  switch (botao.dataset.acao) {
    case 'novo':
      abrirFormulario(null);
      break;
    case 'editar':
      abrirFormulario(item);
      break;
    case 'excluir':
      excluirEstabelecimento(item);
      break;
    default:
      executarAcao(botao.dataset.acao, item, botao);
  }
}

/** Aprovar, arquivar, reativar ou devolver para a revisão. */
async function executarAcao(acao, item, botao) {
  if (!item) return;
  const mensagens = {
    aprovar: 'Aprovado: já aparece no guia.',
    arquivar: 'Arquivado: saiu do guia, mas continua cadastrado.',
    reativar: 'Reativado: voltou para o guia.',
    revisar: 'Devolvido para a revisão.',
  };
  if (botao) botao.disabled = true;
  try {
    const atualizado = await chamarApi(`/admin/estabelecimentos/${item.id}/${acao}`, { metodo: 'POST' });
    atualizarNaLista(atualizado);
    avisar(`${item.nome}: ${mensagens[acao] ?? 'alteração salva.'}`);
    atualizarResumo();
  } catch (erro) {
    avisar(erro.message, 'erro');
  } finally {
    if (botao) botao.disabled = false;
  }
}

async function excluirEstabelecimento(item) {
  if (!item) {
    return;
  }
  const importado = Boolean(item.fonteId);
  const confirmado = await confirmar({
    titulo: 'Excluir estabelecimento',
    texto: `"${item.nome}" será apagado do banco, junto com a imagem. Esta ação não pode ser desfeita.`
      + (importado ? ' Como veio de uma fonte pública, ele pode voltar na próxima importação: para impedir isso, use "Recusar".' : ''),
    botao: 'Excluir',
  });
  if (!confirmado) {
    return;
  }
  try {
    await chamarApi(`/estabelecimentos/${item.id}`, { metodo: 'DELETE' });
    removerDaLista(item.id);
    avisar('Estabelecimento excluído.');
    atualizarResumo();
  } catch (erro) {
    avisar(erro.message, 'erro');
  }
}

/* ============================== Formulário ============================== */

function opcoesDeCategoria(selecionada, textoVazio) {
  const opcoes = estado.categorias.map((categoria) => {
    const marcada = String(categoria.id) === String(selecionada) ? ' selected' : '';
    return `<option value="${categoria.id}"${marcada}>${escapar(categoria.nome)}</option>`;
  });
  return `${textoVazio === null ? '' : `<option value="">${textoVazio}</option>`}${opcoes.join('')}`;
}

async function abrirFormulario(estabelecimento) {
  let completo = estabelecimento ?? null;
  if (estabelecimento?.id) {
    try {
      completo = await chamarApi(`/admin/estabelecimentos/${estabelecimento.id}`);
    } catch (erro) {
      avisar(erro.message, 'erro');
      return;
    }
  }
  estado.editando = completo;
  estado.imagemNova = null;
  estado.removerImagem = false;
  formulario.reset();
  limparErros();

  $('#titulo-formulario').textContent = completo ? 'Editar estabelecimento' : 'Novo estabelecimento';
  campos.categoriaId.innerHTML = opcoesDeCategoria(completo?.categoria.id ?? '', 'Selecione...');
  if (completo) {
    campos.nome.value = completo.nome;
    campos.descricao.value = completo.descricao ?? '';
    campos.endereco.value = completo.endereco ?? '';
    campos.telefone.value = completo.telefone ?? '';
    campos.whatsapp.value = completo.whatsapp ?? '';
    campos.site.value = completo.site ?? '';
    campos.contato.value = completo.contato ?? '';
    campos.horario.value = completo.horario ?? '';
    campos.latitude.value = completo.latitude ?? '';
    campos.longitude.value = completo.longitude ?? '';
    campos.localizacaoValidada.checked = Boolean(completo.localizacaoValidada);
  }
  mostrarFonteNoFormulario(completo);
  $('#botao-salvar-aprovar').hidden = !(completo && completo.situacao === 'PENDENTE');
  atualizarContador();
  mostrarPrevia(completo?.imagemUrl ?? null);

  dialogoFormulario.showModal();
  prepararMapa();
  campos.nome.focus();
}

/** Origem do registro e alertas da importação, para a conferência. */
function mostrarFonteNoFormulario(item) {
  const fonte = $('#fonte-formulario');
  const observacoes = $('#observacoes-formulario');
  if (!item) {
    fonte.hidden = true;
    observacoes.hidden = true;
    return;
  }
  const link = item.urlFonte
    ? ` · <a href="${escapar(item.urlFonte)}" target="_blank" rel="noopener">ver na fonte ${icone('external-link')}</a>`
    : '';
  fonte.innerHTML = `${etiquetaDaSituacao(item)} Fonte: ${escapar(item.fonte)}${link}`
    + (item.tipoFonte ? ` · <span class="dialogo__tipo">${escapar(item.tipoFonte)}</span>` : '')
    + (item.atualizadoEm ? ` · atualizado em ${formatarData(item.atualizadoEm)}` : '');
  fonte.hidden = false;

  const linhas = [...(item.observacoes ?? [])];
  if (item.duplicadoDe) {
    linhas.unshift(`Possível duplicado de "${item.duplicadoDe.nome}" (${item.duplicadoDe.fonte}): ${item.duplicadoDe.motivo}.`);
  }
  observacoes.innerHTML = linhas.length
    ? `<strong>${icone('info')} Observações da importação</strong><ul>${linhas.map((linha) => `<li>${escapar(linha)}</li>`).join('')}</ul>`
    : '';
  observacoes.hidden = linhas.length === 0;
}

function atualizarContador() {
  $('#contador-descricao').textContent = campos.descricao.value.length;
}

async function salvarEstabelecimento(evento) {
  evento.preventDefault();
  limparErros();
  const dados = lerFormulario();
  if (!dados) {
    return;
  }
  const aprovarAoSalvar = evento.submitter?.value === 'aprovar';
  const eraNovo = !estado.editando;

  if (eraNovo && !(await confirmarSemDuplicados(dados))) {
    return;
  }

  const botoes = [$('#botao-salvar'), $('#botao-salvar-aprovar')];
  botoes.forEach((botao) => { botao.disabled = true; });

  let salvo;
  try {
    salvo = estado.editando
      ? await chamarApi(`/estabelecimentos/${estado.editando.id}`, { metodo: 'PUT', corpo: dados })
      : await chamarApi('/estabelecimentos', { metodo: 'POST', corpo: dados });
  } catch (erro) {
    mostrarErroDoServidor(erro);
    botoes.forEach((botao) => { botao.disabled = false; });
    return;
  }
  // a partir daqui, salvar de novo edita o registro em vez de criar outro
  estado.editando = salvo;

  try {
    if (estado.imagemNova) {
      salvo = await chamarApi(`/estabelecimentos/${salvo.id}/imagem`, { metodo: 'POST', arquivo: estado.imagemNova });
    } else if (estado.removerImagem && salvo.imagemUrl) {
      salvo = await chamarApi(`/estabelecimentos/${salvo.id}/imagem`, { metodo: 'DELETE' });
    }
    if (aprovarAoSalvar) {
      salvo = await chamarApi(`/admin/estabelecimentos/${salvo.id}/aprovar`, { metodo: 'POST' });
    }
  } catch (erro) {
    $('#titulo-formulario').textContent = 'Editar estabelecimento';
    mostrarErroGeral(`Os dados foram salvos, mas houve um problema na etapa seguinte: ${erro.message}`);
    botoes.forEach((botao) => { botao.disabled = false; });
    atualizarNaLista(salvo);
    return;
  }

  botoes.forEach((botao) => { botao.disabled = false; });
  dialogoFormulario.close();
  atualizarNaLista(salvo);
  atualizarResumo();
  avisar(aprovarAoSalvar ? 'Alterações salvas e estabelecimento aprovado.'
    : eraNovo ? 'Estabelecimento cadastrado e publicado no guia.' : 'Alterações salvas.');
}

/** Antes de cadastrar, mostra estabelecimentos parecidos que já existem. */
async function confirmarSemDuplicados(dados) {
  let semelhantes = [];
  try {
    semelhantes = await chamarApi('/admin/estabelecimentos/verificar-duplicados', { metodo: 'POST', corpo: dados });
  } catch {
    return true; // a verificação é uma ajuda: se falhar, não impede o cadastro
  }
  if (semelhantes.length === 0) {
    return true;
  }
  return confirmar({
    titulo: 'Já existe um cadastro parecido',
    texto: 'Confira se não é o mesmo estabelecimento antes de cadastrar de novo:',
    detalhe: `<ul class="lista-semelhantes">${semelhantes.map((item) => `
      <li><strong>${escapar(item.nome)}</strong> · ${escapar(item.endereco ?? 'sem endereço')}
        <span>${escapar(item.motivo)} · ${escapar(item.fonte)}</span></li>`).join('')}</ul>`,
    botao: 'Cadastrar mesmo assim',
    icone: 'copy',
    perigo: false,
  });
}

function lerNumero(texto) {
  const valor = String(texto ?? '').trim().replace(',', '.');
  if (!valor) {
    return null;
  }
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : NaN;
}

/** Valida o formulário no navegador; a API valida novamente ao salvar. */
function lerFormulario() {
  const erros = {};
  const nome = campos.nome.value.trim();
  const categoriaId = campos.categoriaId.value;
  const latitude = lerNumero(campos.latitude.value);
  const longitude = lerNumero(campos.longitude.value);

  if (!nome) {
    erros.nome = 'Informe o nome do estabelecimento.';
  }
  if (!categoriaId) {
    erros.categoriaId = 'Selecione uma categoria.';
  }
  if (Number.isNaN(latitude) || (latitude !== null && Math.abs(latitude) > 90)) {
    erros.latitude = 'Latitude inválida: use um número entre -90 e 90.';
  }
  if (Number.isNaN(longitude) || (longitude !== null && Math.abs(longitude) > 180)) {
    erros.longitude = 'Longitude inválida: use um número entre -180 e 180.';
  }
  if (!erros.latitude && !erros.longitude && (latitude === null) !== (longitude === null)) {
    erros[latitude === null ? 'latitude' : 'longitude'] = 'Informe a latitude e a longitude juntas.';
  }

  if (Object.keys(erros).length > 0) {
    mostrarErrosDosCampos(erros);
    return null;
  }
  return {
    nome,
    categoriaId: Number(categoriaId),
    descricao: campos.descricao.value,
    endereco: campos.endereco.value,
    telefone: campos.telefone.value,
    whatsapp: campos.whatsapp.value,
    site: campos.site.value,
    contato: campos.contato.value,
    horario: campos.horario.value,
    latitude,
    longitude,
    localizacaoValidada: latitude !== null && campos.localizacaoValidada.checked,
  };
}

function mostrarErrosDosCampos(erros) {
  let primeiroCampo = null;
  const semCampo = [];
  for (const [nome, mensagem] of Object.entries(erros)) {
    const destino = formulario.querySelector(`[data-erro="${nome}"]`);
    if (destino) {
      destino.textContent = mensagem;
      destino.closest('.campo')?.classList.add('campo--erro');
      primeiroCampo ??= campos[nome];
    } else {
      semCampo.push(mensagem);
    }
  }
  if (semCampo.length > 0) {
    mostrarErroGeral(semCampo.join(' '));
  }
  primeiroCampo?.focus();
}

function mostrarErroDoServidor(erro) {
  if (erro.campos && Object.keys(erro.campos).length > 0) {
    mostrarErrosDosCampos(erro.campos);
  } else {
    mostrarErroGeral(erro.message);
  }
}

function mostrarErroGeral(mensagem) {
  const alerta = $('#erro-formulario');
  alerta.innerHTML = `${icone('circle-alert')}<div>${escapar(mensagem)}</div>`;
  alerta.hidden = false;
  alerta.scrollIntoView({ block: 'nearest' });
}

function limparErros() {
  $('#erro-formulario').hidden = true;
  formulario.querySelectorAll('[data-erro]').forEach((elemento) => { elemento.textContent = ''; });
  formulario.querySelectorAll('.campo--erro').forEach((elemento) => elemento.classList.remove('campo--erro'));
}

/* ============================== Localização ============================== */

function prepararMapa() {
  if (!mapaFormulario) {
    mapaFormulario = L.map('mapa-formulario').setView(CENTRO_CAPITAO_POCO, 15);
    adicionarCamadaBase(mapaFormulario);
    mapaFormulario.on('click', (evento) => definirLocalizacao(evento.latlng.lat, evento.latlng.lng, false));
  }
  mapaFormulario.invalidateSize();

  const posicao = coordenadasDosCampos();
  if (posicao) {
    posicionarMarcador(posicao.lat, posicao.lng);
    mapaFormulario.setView([posicao.lat, posicao.lng], 17);
  } else {
    removerMarcador();
    mapaFormulario.setView(CENTRO_CAPITAO_POCO, 15);
  }
}

function coordenadasDosCampos() {
  const lat = lerNumero(campos.latitude.value);
  const lng = lerNumero(campos.longitude.value);
  if (lat === null || lng === null || Number.isNaN(lat) || Number.isNaN(lng)) {
    return null;
  }
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return null;
  }
  return { lat, lng };
}

/** Posição escolhida pela administração no mapa: fica marcada como conferida. */
function definirLocalizacao(lat, lng, centralizar = true) {
  const latitude = Number(lat.toFixed(6));
  const longitude = Number(lng.toFixed(6));
  campos.latitude.value = latitude;
  campos.longitude.value = longitude;
  campos.localizacaoValidada.checked = true;
  for (const nome of ['latitude', 'longitude']) {
    formulario.querySelector(`[data-erro="${nome}"]`).textContent = '';
    campos[nome].closest('.campo').classList.remove('campo--erro');
  }
  posicionarMarcador(latitude, longitude);
  if (centralizar) {
    mapaFormulario.setView([latitude, longitude], Math.max(mapaFormulario.getZoom(), 17));
  }
}

function posicionarMarcador(lat, lng) {
  if (marcadorFormulario) {
    marcadorFormulario.setLatLng([lat, lng]);
    return;
  }
  marcadorFormulario = L.marker([lat, lng], {
    draggable: true,
    icon: iconeMarcador(categoriaDoFormulario()),
    title: 'Arraste para ajustar a posição',
  }).addTo(mapaFormulario);
  marcadorFormulario.on('dragend', () => {
    const posicao = marcadorFormulario.getLatLng();
    definirLocalizacao(posicao.lat, posicao.lng, false);
  });
}

function removerMarcador() {
  if (marcadorFormulario) {
    marcadorFormulario.remove();
    marcadorFormulario = null;
  }
}

function categoriaDoFormulario() {
  return estado.categorias.find((categoria) => String(categoria.id) === campos.categoriaId.value) ?? null;
}

function atualizarIconeDoMarcador() {
  marcadorFormulario?.setIcon(iconeMarcador(categoriaDoFormulario()));
}

function sincronizarMarcadorComCampos() {
  const posicao = coordenadasDosCampos();
  if (posicao) {
    posicionarMarcador(posicao.lat, posicao.lng);
    mapaFormulario.setView([posicao.lat, posicao.lng], Math.max(mapaFormulario.getZoom(), 16));
  } else if (!campos.latitude.value.trim() && !campos.longitude.value.trim()) {
    removerMarcador();
  }
}

function limparLocalizacao() {
  campos.latitude.value = '';
  campos.longitude.value = '';
  campos.localizacaoValidada.checked = false;
  removerMarcador();
}

function colarCoordenadas(evento) {
  const texto = evento.clipboardData?.getData('text') ?? '';
  const coordenadas = extrairCoordenadas(texto);
  if (coordenadas) {
    evento.preventDefault();
    definirLocalizacao(coordenadas.lat, coordenadas.lng, true);
    avisar('Localização preenchida a partir do texto colado.');
  }
}

/** Aceita "-1.7447, -47.0638" ou links do Google Maps com as coordenadas. */
function extrairCoordenadas(textoOriginal) {
  let texto = textoOriginal;
  try {
    texto = decodeURIComponent(textoOriginal);
  } catch {
    // mantém o texto original
  }
  const padroes = [
    /@(-?\d{1,2}\.\d+),\s*(-?\d{1,3}\.\d+)/,
    /!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)/,
    /[?&](?:q|query|ll|destination|mlat)=(-?\d{1,2}\.\d+)(?:,\s*|&mlon=)(-?\d{1,3}\.\d+)/,
    /^\s*\(?\s*(-?\d{1,2}\.\d+)\s*[,;]\s*(-?\d{1,3}\.\d+)\s*\)?\s*$/,
  ];
  for (const padrao of padroes) {
    const encontrado = texto.match(padrao);
    if (encontrado) {
      const lat = Number(encontrado[1]);
      const lng = Number(encontrado[2]);
      if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
        return { lat, lng };
      }
    }
  }
  return null;
}

function usarMinhaLocalizacao() {
  if (!('geolocation' in navigator)) {
    avisar('Este navegador não permite obter a localização.', 'erro');
    return;
  }
  const botao = $('#botao-minha-localizacao');
  botao.disabled = true;
  navigator.geolocation.getCurrentPosition(
    (posicao) => {
      botao.disabled = false;
      definirLocalizacao(posicao.coords.latitude, posicao.coords.longitude, true);
    },
    (erro) => {
      botao.disabled = false;
      avisar(erro.code === erro.PERMISSION_DENIED
        ? 'O navegador não permitiu acessar a localização.'
        : 'Não foi possível obter a localização atual.', 'erro');
    },
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
  );
}

/* ============================== Imagem ============================== */

function mostrarPrevia(url) {
  $('#previa-imagem').innerHTML = url
    ? `<img src="${escapar(url)}" alt="Prévia da imagem do estabelecimento">`
    : `<span>${icone('image')}Sem imagem</span>`;
  $('#botao-remover-imagem').hidden = !url;
  $('#botao-escolher-imagem').innerHTML = `${icone('upload')} ${url ? 'Trocar imagem' : 'Escolher imagem'}`;
}

async function aoEscolherImagem(evento) {
  const arquivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!arquivo) {
    return;
  }
  if (!TIPOS_DE_IMAGEM.includes(arquivo.type)) {
    avisar('Formato não suportado. Envie uma imagem PNG, JPG ou WEBP.', 'erro');
    return;
  }
  try {
    const imagem = await reduzirImagem(arquivo);
    if (imagem.size > TAMANHO_MAXIMO) {
      avisar('A imagem deve ter no máximo 5 MB.', 'erro');
      return;
    }
    limparImagemTemporaria();
    estado.imagemNova = imagem;
    estado.removerImagem = false;
    estado.urlPrevia = URL.createObjectURL(imagem);
    mostrarPrevia(estado.urlPrevia);
  } catch {
    avisar('Não foi possível ler a imagem escolhida.', 'erro');
  }
}

function marcarRemocaoDaImagem() {
  limparImagemTemporaria();
  estado.imagemNova = null;
  estado.removerImagem = true;
  mostrarPrevia(null);
}

function limparImagemTemporaria() {
  if (estado.urlPrevia) {
    URL.revokeObjectURL(estado.urlPrevia);
    estado.urlPrevia = null;
  }
}

/** Reduz fotos grandes antes do envio (lado maior de até 1200 px). */
async function reduzirImagem(arquivo) {
  if (!('createImageBitmap' in window)) {
    return arquivo;
  }
  const bitmap = await createImageBitmap(arquivo);
  const escala = Math.min(1, LADO_MAXIMO_IMAGEM / Math.max(bitmap.width, bitmap.height));
  if (escala === 1 && arquivo.size <= 1024 * 1024) {
    bitmap.close();
    return arquivo;
  }

  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * escala);
  canvas.height = Math.round(bitmap.height * escala);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();

  const blob = await new Promise((resolver) => canvas.toBlob(resolver, 'image/webp', 0.86));
  if (!blob || blob.size >= arquivo.size) {
    return arquivo;
  }
  const extensao = blob.type === 'image/webp' ? 'webp' : 'png';
  return new File([blob], `${arquivo.name.replace(/\.[^.]+$/, '')}.${extensao}`, { type: blob.type });
}

/* ============================== Categorias ============================== */

function renderizarCategorias() {
  $('#total-categorias').textContent = estado.categorias.length;

  const filtro = $('#filtro-categoria');
  filtro.innerHTML = opcoesDeCategoria(filtro.value, 'Todas as categorias');

  $('#lista-categorias-admin').innerHTML = estado.categorias.map((categoria) => {
    const visual = visualCategoria(categoria);
    const nome = escapar(categoria.nome);
    const emUso = categoria.totalCadastros > 0;
    const exclusao = emUso
      ? 'disabled title="Não é possível excluir: há estabelecimentos nesta categoria"'
      : 'title="Excluir"';
    return `
      <li class="categoria-item">
        <span class="categoria-item__icone" style="--cor-categoria:${visual.cor}">${icone(visual.icone)}</span>
        <div class="categoria-item__texto">
          <strong>${nome}</strong>
          <span>${plural(categoria.totalEstabelecimentos, 'no guia', 'no guia')} · ${plural(categoria.totalCadastros, 'cadastro', 'cadastros')} ao todo</span>
        </div>
        <div class="categoria-item__acoes">
          <button type="button" class="botao botao--fantasma botao--icone" data-acao="renomear"
                  data-id="${categoria.id}" title="Renomear" aria-label="Renomear ${nome}">${icone('pencil')}</button>
          <button type="button" class="botao botao--fantasma botao--icone botao--excluir" data-acao="excluir"
                  data-id="${categoria.id}" ${exclusao} aria-label="Excluir ${nome}">${icone('trash-2')}</button>
        </div>
      </li>`;
  }).join('');
}

async function criarCategoria(evento) {
  evento.preventDefault();
  const campo = $('#nova-categoria');
  const nome = campo.value.trim();
  if (!nome) {
    campo.focus();
    return;
  }
  try {
    await chamarApi('/categorias', { metodo: 'POST', corpo: { nome } });
    campo.value = '';
    avisar(`Categoria "${nome}" criada.`);
    await recarregarDados();
  } catch (erro) {
    avisar(erro.campos?.nome ?? erro.message, 'erro');
  }
}

function aoClicarEmCategoria(evento) {
  const botao = evento.target.closest('button[data-acao]');
  if (!botao || botao.disabled) {
    return;
  }
  const categoria = estado.categorias.find((item) => item.id === Number(botao.dataset.id));
  if (botao.dataset.acao === 'renomear') {
    renomearCategoria(categoria);
  } else if (botao.dataset.acao === 'excluir') {
    excluirCategoria(categoria);
  }
}

async function renomearCategoria(categoria) {
  const nome = await pedirTexto({ titulo: 'Renomear categoria', rotulo: 'Nome da categoria', valor: categoria.nome });
  if (nome === null || nome === categoria.nome) {
    return;
  }
  try {
    await chamarApi(`/categorias/${categoria.id}`, { metodo: 'PUT', corpo: { nome } });
    avisar('Categoria renomeada.');
    await recarregarDados();
  } catch (erro) {
    avisar(erro.campos?.nome ?? erro.message, 'erro');
  }
}

async function excluirCategoria(categoria) {
  const confirmado = await confirmar({
    titulo: 'Excluir categoria',
    texto: `A categoria "${categoria.nome}" será excluída.`,
    botao: 'Excluir',
  });
  if (!confirmado) {
    return;
  }
  try {
    await chamarApi(`/categorias/${categoria.id}`, { metodo: 'DELETE' });
    avisar('Categoria excluída.');
    await recarregarDados();
  } catch (erro) {
    avisar(erro.message, 'erro');
  }
}

/* ============================== Diálogos ============================== */

/**
 * Pede confirmação. Por padrão tem aparência de ação destrutiva (excluir);
 * com perigo: false, o botão principal fica verde.
 */
function confirmar({ titulo, texto, detalhe = '', botao = 'Confirmar', icone: nomeIcone = 'trash-2', perigo = true }) {
  const dialogo = $('#dialogo-confirmacao');
  $('#titulo-confirmacao').textContent = titulo;
  $('#texto-confirmacao').textContent = texto;
  $('#detalhe-confirmacao').innerHTML = detalhe;
  $('#icone-confirmacao').innerHTML = icone(nomeIcone);
  $('#icone-confirmacao').classList.toggle('confirmacao__icone--neutro', !perigo);
  const confirmarBotao = $('#botao-confirmar');
  confirmarBotao.textContent = botao;
  confirmarBotao.className = `botao ${perigo ? 'botao--perigo' : 'botao--primario'}`;
  dialogo.returnValue = '';
  dialogo.showModal();
  return new Promise((resolver) => {
    dialogo.addEventListener('close', () => resolver(dialogo.returnValue === 'confirmar'), { once: true });
  });
}

function pedirTexto({ titulo, rotulo, valor }) {
  const dialogo = $('#dialogo-texto');
  const campo = $('#campo-dialogo-texto');
  $('#titulo-dialogo-texto').textContent = titulo;
  $('#rotulo-dialogo-texto').textContent = rotulo;
  campo.value = valor ?? '';
  dialogo.returnValue = '';
  dialogo.showModal();
  campo.select();
  return new Promise((resolver) => {
    dialogo.addEventListener('close', () => {
      resolver(dialogo.returnValue === 'confirmar' ? campo.value.trim() : null);
    }, { once: true });
  });
}
