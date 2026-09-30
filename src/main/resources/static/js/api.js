/**
 * Comunicação com a API REST do RumoCap.
 * Todas as informações exibidas no site vêm daqui: nenhum estabelecimento fica fixo no JavaScript.
 */

const BASE = '/api';

export class ErroApi extends Error {
  constructor(status, mensagem, campos = {}) {
    super(mensagem);
    this.status = status;
    this.campos = campos;
  }
}

/**
 * Faz uma requisição à API e devolve o JSON da resposta.
 *
 * @param {string} caminho  ex.: "/estabelecimentos"
 * @param {object} opcoes   metodo, corpo (objeto enviado como JSON), arquivo (File) e autorizacao
 */
export async function requisitar(caminho, { metodo = 'GET', corpo, arquivo, autorizacao } = {}) {
  const cabecalhos = { Accept: 'application/json' };
  if (autorizacao) {
    cabecalhos.Authorization = autorizacao;
  }

  let body;
  if (arquivo) {
    body = new FormData();
    body.append('arquivo', arquivo, arquivo.name || 'imagem');
  } else if (corpo !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
    body = JSON.stringify(corpo);
  }

  let resposta;
  try {
    resposta = await fetch(BASE + caminho, { method: metodo, headers: cabecalhos, body });
  } catch {
    throw new ErroApi(0, 'Não foi possível conectar ao servidor. Verifique se a aplicação está em execução.');
  }

  const texto = await resposta.text();
  let dados = null;
  if (texto) {
    try {
      dados = JSON.parse(texto);
    } catch {
      dados = null;
    }
  }

  if (!resposta.ok) {
    throw new ErroApi(resposta.status, dados?.mensagem || mensagemPadrao(resposta.status), dados?.campos || {});
  }
  return dados;
}

function mensagemPadrao(status) {
  if (status === 401) return 'Acesso restrito. Faça login novamente.';
  if (status === 404) return 'Registro não encontrado.';
  if (status === 413) return 'O arquivo enviado é grande demais.';
  if (status >= 500) return 'O servidor encontrou um erro. Tente novamente em instantes.';
  return 'Não foi possível concluir a operação.';
}

function parametros(filtros = {}) {
  const busca = new URLSearchParams();
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      busca.set(chave, valor);
    }
  }
  const texto = busca.toString();
  return texto ? `?${texto}` : '';
}

/** Consultas públicas usadas pelas páginas do site. */
export const api = {
  listarCategorias: () => requisitar('/categorias'),
  listarEstabelecimentos: (filtros) => requisitar('/estabelecimentos' + parametros(filtros)),
  buscarEstabelecimento: (id) => requisitar(`/estabelecimentos/${encodeURIComponent(id)}`),
  listarMarcadores: (filtros) => requisitar('/estabelecimentos/mapa' + parametros(filtros)),
  /** "Informação incorreta?": aviso enviado pelo visitante para a administração conferir. */
  enviarRelato: (id, dados) => requisitar(`/estabelecimentos/${encodeURIComponent(id)}/relatos`, { metodo: 'POST', corpo: dados }),
};
