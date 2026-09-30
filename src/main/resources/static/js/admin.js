/**
 * Área administrativa do RumoCap.
 * Todas as alterações (cadastro, edição, exclusão, categoria, localização e imagem)
 * são enviadas para a API e gravadas no PostgreSQL.
 */
import { requisitar } from './api.js';
import {
  CENTRO_CAPITAO_POCO, adicionarCamadaBase, avatar, avisar, blocoErro, blocoVazio, escapar, icone,
  iconeMarcador, normalizar, plural, seloCategoria, visualCategoria,
} from './comum.js';

const CHAVE_SESSAO = 'rumocap.admin';
const TAMANHO_MAXIMO = 5 * 1024 * 1024;
const LADO_MAXIMO_IMAGEM = 1200;
const TIPOS_DE_IMAGEM = ['image/png', 'image/jpeg', 'image/webp'];

const $ = (seletor) => document.querySelector(seletor);

const formulario = $('#form-estabelecimento');
const campos = formulario.elements;
const dialogoFormulario = $('#dialogo-estabelecimento');

const estado = {
  credencial: lerSessao(),
  categorias: [],
  estabelecimentos: [],
  editando: null, // estabelecimento aberto no formulário (null = novo cadastro)
  imagemNova: null, // arquivo escolhido e ainda não enviado
  removerImagem: false,
  urlPrevia: null,
};

let mapaFormulario = null;
let marcadorFormulario = null;

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

  $('#filtro-texto').addEventListener('input', renderizarEstabelecimentos);
  $('#filtro-categoria').addEventListener('change', renderizarEstabelecimentos);
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
  }
  try {
    const [categorias, estabelecimentos] = await Promise.all([
      chamarApi('/categorias'),
      chamarApi('/estabelecimentos'),
    ]);
    estado.categorias = categorias;
    estado.estabelecimentos = estabelecimentos;
    renderizarCategorias();
    renderizarEstabelecimentos();
  } catch (erro) {
    lista.innerHTML = blocoErro(erro.message);
  }
}

/* ============================== Abas ============================== */

function selecionarAba(aba) {
  document.querySelectorAll('[role="tab"]').forEach((outra) => {
    const ativa = outra === aba;
    outra.setAttribute('aria-selected', String(ativa));
    outra.tabIndex = ativa ? 0 : -1;
    document.getElementById(outra.getAttribute('aria-controls')).hidden = !ativa;
  });
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

function renderizarEstabelecimentos() {
  const lista = $('#lista-estabelecimentos');
  const termo = normalizar($('#filtro-texto').value);
  const categoriaId = $('#filtro-categoria').value;
  $('#total-estabelecimentos').textContent = estado.estabelecimentos.length;

  if (estado.estabelecimentos.length === 0) {
    lista.innerHTML = blocoVazio({
      icone: 'store',
      titulo: 'Nenhum estabelecimento cadastrado',
      texto: 'Cadastre o primeiro estabelecimento com os dados reais levantados.',
      acao: `<button type="button" class="botao botao--primario" data-acao="novo">${icone('plus')} Novo estabelecimento</button>`,
    });
    return;
  }

  const filtrados = estado.estabelecimentos.filter((estabelecimento) =>
    (!categoriaId || String(estabelecimento.categoria.id) === categoriaId)
    && (!termo || normalizar(estabelecimento.nome).includes(termo)));

  if (filtrados.length === 0) {
    lista.innerHTML = blocoVazio({
      icone: 'search',
      titulo: 'Nenhum resultado',
      texto: 'Nenhum estabelecimento corresponde aos filtros escolhidos.',
    });
    return;
  }

  lista.innerHTML = `
    <table class="tabela">
      <thead>
        <tr>
          <th scope="col">Estabelecimento</th>
          <th scope="col">Categoria</th>
          <th scope="col">Localização</th>
          <th scope="col"><span class="visualmente-oculto">Ações</span></th>
        </tr>
      </thead>
      <tbody>${filtrados.map(linhaDaTabela).join('')}</tbody>
    </table>`;
}

function linhaDaTabela(estabelecimento) {
  const nome = escapar(estabelecimento.nome);
  const localizacao = estabelecimento.latitude != null
    ? `<span class="situacao situacao--ok">${icone('map-pin')} No mapa</span>`
    : `<span class="situacao situacao--pendente">${icone('map-pin-off')} Sem localização</span>`;
  const endereco = estabelecimento.endereco ? escapar(estabelecimento.endereco) : 'Endereço não informado';

  return `
    <tr>
      <td data-rotulo="Estabelecimento">
        <div class="tabela__estab">
          ${avatar(estabelecimento, 'pequeno')}
          <div class="tabela__texto">
            <span class="tabela__nome">${nome}</span>
            <span class="tabela__sub">${endereco}</span>
          </div>
        </div>
      </td>
      <td data-rotulo="Categoria">${seloCategoria(estabelecimento.categoria)}</td>
      <td data-rotulo="Localização">${localizacao}</td>
      <td class="tabela__acoes">
        <a class="botao botao--fantasma botao--icone" href="estabelecimento.html?id=${estabelecimento.id}"
           target="_blank" rel="noopener" title="Ver no site" aria-label="Ver ${nome} no site">${icone('external-link')}</a>
        <button type="button" class="botao botao--fantasma botao--icone" data-acao="editar"
                data-id="${estabelecimento.id}" title="Editar" aria-label="Editar ${nome}">${icone('pencil')}</button>
        <button type="button" class="botao botao--fantasma botao--icone botao--excluir" data-acao="excluir"
                data-id="${estabelecimento.id}" title="Excluir" aria-label="Excluir ${nome}">${icone('trash-2')}</button>
      </td>
    </tr>`;
}

function aoClicarEmEstabelecimento(evento) {
  const botao = evento.target.closest('button[data-acao]');
  if (!botao) {
    return;
  }
  const id = Number(botao.dataset.id);
  if (botao.dataset.acao === 'novo') {
    abrirFormulario(null);
  } else if (botao.dataset.acao === 'editar') {
    abrirFormulario(estado.estabelecimentos.find((estabelecimento) => estabelecimento.id === id));
  } else if (botao.dataset.acao === 'excluir') {
    excluirEstabelecimento(id);
  }
}

async function excluirEstabelecimento(id) {
  const estabelecimento = estado.estabelecimentos.find((item) => item.id === id);
  if (!estabelecimento) {
    return;
  }
  const confirmado = await confirmar({
    titulo: 'Excluir estabelecimento',
    texto: `"${estabelecimento.nome}" será removido do guia, junto com a imagem. Esta ação não pode ser desfeita.`,
    botao: 'Excluir',
  });
  if (!confirmado) {
    return;
  }
  try {
    await chamarApi(`/estabelecimentos/${id}`, { metodo: 'DELETE' });
    avisar('Estabelecimento excluído.');
    await recarregarDados();
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
  return `<option value="">${textoVazio}</option>${opcoes.join('')}`;
}

function abrirFormulario(estabelecimento) {
  estado.editando = estabelecimento ?? null;
  estado.imagemNova = null;
  estado.removerImagem = false;
  formulario.reset();
  limparErros();

  $('#titulo-formulario').textContent = estabelecimento ? 'Editar estabelecimento' : 'Novo estabelecimento';
  campos.categoriaId.innerHTML = opcoesDeCategoria(estabelecimento?.categoria.id ?? '', 'Selecione...');
  if (estabelecimento) {
    campos.nome.value = estabelecimento.nome;
    campos.descricao.value = estabelecimento.descricao ?? '';
    campos.endereco.value = estabelecimento.endereco ?? '';
    campos.contato.value = estabelecimento.contato ?? '';
    campos.horario.value = estabelecimento.horario ?? '';
    campos.latitude.value = estabelecimento.latitude ?? '';
    campos.longitude.value = estabelecimento.longitude ?? '';
  }
  atualizarContador();
  mostrarPrevia(estabelecimento?.imagemUrl ?? null);

  dialogoFormulario.showModal();
  prepararMapa();
  campos.nome.focus();
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

  const botao = $('#botao-salvar');
  const eraNovo = !estado.editando;
  botao.disabled = true;

  let salvo;
  try {
    salvo = estado.editando
      ? await chamarApi(`/estabelecimentos/${estado.editando.id}`, { metodo: 'PUT', corpo: dados })
      : await chamarApi('/estabelecimentos', { metodo: 'POST', corpo: dados });
  } catch (erro) {
    mostrarErroDoServidor(erro);
    botao.disabled = false;
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
  } catch (erro) {
    $('#titulo-formulario').textContent = 'Editar estabelecimento';
    mostrarErroGeral(`Os dados foram salvos, mas a imagem não pôde ser atualizada: ${erro.message}`);
    botao.disabled = false;
    await recarregarDados();
    return;
  }

  botao.disabled = false;
  dialogoFormulario.close();
  avisar(eraNovo ? 'Estabelecimento cadastrado.' : 'Alterações salvas.');
  await recarregarDados();
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
    contato: campos.contato.value,
    horario: campos.horario.value,
    latitude,
    longitude,
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

function definirLocalizacao(lat, lng, centralizar = true) {
  const latitude = Number(lat.toFixed(6));
  const longitude = Number(lng.toFixed(6));
  campos.latitude.value = latitude;
  campos.longitude.value = longitude;
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
    /[?&](?:q|query|ll|destination)=(-?\d{1,2}\.\d+),\s*(-?\d{1,3}\.\d+)/,
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
    const emUso = categoria.totalEstabelecimentos > 0;
    const exclusao = emUso
      ? 'disabled title="Não é possível excluir: há estabelecimentos nesta categoria"'
      : 'title="Excluir"';
    return `
      <li class="categoria-item">
        <span class="categoria-item__icone" style="--cor-categoria:${visual.cor}">${icone(visual.icone)}</span>
        <div class="categoria-item__texto">
          <strong>${nome}</strong>
          <span>${plural(categoria.totalEstabelecimentos, 'estabelecimento', 'estabelecimentos')}</span>
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

function confirmar({ titulo, texto, botao = 'Confirmar' }) {
  const dialogo = $('#dialogo-confirmacao');
  $('#titulo-confirmacao').textContent = titulo;
  $('#texto-confirmacao').textContent = texto;
  $('#botao-confirmar').textContent = botao;
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
