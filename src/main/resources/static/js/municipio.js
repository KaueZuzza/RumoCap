/**
 * Dados oficiais do município de Capitão Poço (PA), consultados na API
 * pública e gratuita do IBGE (servicodados.ibge.gov.br):
 *  - localidades: regiões às quais o município pertence;
 *  - agregados (Censo 2022): população, área e densidade;
 *  - malhas: o limite territorial (GeoJSON) desenhado no mapa.
 * O resultado fica guardado no navegador por alguns dias.
 */

/** Código oficial de Capitão Poço no IBGE. */
const CODIGO_IBGE = '1502301';
const IBGE = 'https://servicodados.ibge.gov.br/api';
const CHAVE_CACHE = 'rumocap.municipio.v1';
const VALIDADE_CACHE = 7 * 24 * 60 * 60 * 1000;

async function buscarJson(url) {
  const resposta = await fetch(url);
  if (!resposta.ok) {
    throw new Error(`IBGE respondeu ${resposta.status}`);
  }
  return resposta.json();
}

function lerCache() {
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE_CACHE));
    return salvo && Date.now() - salvo.salvoEm < VALIDADE_CACHE ? salvo.dados : null;
  } catch {
    return null;
  }
}

function gravarCache(dados) {
  try {
    localStorage.setItem(CHAVE_CACHE, JSON.stringify({ salvoEm: Date.now(), dados }));
  } catch {
    // armazenamento cheio ou bloqueado: consulta de novo na próxima visita
  }
}

function valorDoCenso(agregados, idVariavel) {
  const variavel = agregados.find((item) => item.id === idVariavel);
  const serie = variavel?.resultados?.[0]?.series?.[0]?.serie;
  const valor = serie ? Number(Object.values(serie)[0]) : NaN;
  return Number.isFinite(valor) ? valor : null;
}

/**
 * @returns {Promise<{nome, uf, regiaoImediata, regiaoIntermediaria, populacao, area, densidade, limite}>}
 *          campos ausentes ficam null quando alguma consulta falha
 */
export async function carregarMunicipio() {
  const cache = lerCache();
  if (cache) {
    return cache;
  }

  const [localidade, censo, malha] = await Promise.allSettled([
    buscarJson(`${IBGE}/v1/localidades/municipios/${CODIGO_IBGE}`),
    // 93 = população residente, 6318 = área territorial, 614 = densidade demográfica
    buscarJson(`${IBGE}/v3/agregados/4714/periodos/2022/variaveis/${encodeURIComponent('93|6318|614')}?localidades=${encodeURIComponent(`N6[${CODIGO_IBGE}]`)}`),
    buscarJson(`${IBGE}/v3/malhas/municipios/${CODIGO_IBGE}?formato=application/vnd.geo+json&qualidade=intermediaria`),
  ]);

  if (localidade.status === 'rejected' && censo.status === 'rejected' && malha.status === 'rejected') {
    throw new Error('Os dados do IBGE estão indisponíveis no momento.');
  }

  const info = localidade.value ?? {};
  const agregados = censo.value ?? [];
  const dados = {
    nome: info.nome ?? 'Capitão Poço',
    uf: info['regiao-imediata']?.['regiao-intermediaria']?.UF?.sigla ?? 'PA',
    regiaoImediata: info['regiao-imediata']?.nome ?? null,
    regiaoIntermediaria: info['regiao-imediata']?.['regiao-intermediaria']?.nome ?? null,
    populacao: valorDoCenso(agregados, '93'),
    area: valorDoCenso(agregados, '6318'),
    densidade: valorDoCenso(agregados, '614'),
    limite: malha.value ?? null,
  };

  if ([localidade, censo, malha].every((resultado) => resultado.status === 'fulfilled')) {
    gravarCache(dados);
  }
  return dados;
}
