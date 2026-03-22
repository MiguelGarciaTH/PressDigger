export type Lang = "en" | "pt"

export const translations = {
  en: {
    // Common
    loading: "Loading…",
    saving: "Saving…",
    save: "Save",
    cancel: "Cancel",
    edit: "Edit",
    back: "Back",
    article: "article",
    articles: "articles",
    failedToLoad: "Failed to load collections",
    noCollectionsFound: "No collections found",

    // Sidebar
    openMenu: "Open menu",
    closeMenu: "Close menu",
    signIn: "Sign in",
    signOut: "Sign out",
    signInToView: "Sign in to view",
    navSearch: "Search",
    navTextEditor: "Text Editor",
    navPublic: "Public",
    navPrivate: "Private",
    navJournalists: "Journalists",
    panelJournalists: "Journalists",
    panelPublic: "Public Collections",
    panelPrivate: "My Collections",
    comingSoon: "Coming soon",
    noCollectionsFoundPanel: "No collections found",

    // SearchPage
    searchPlaceholder: "Search newspapers…",
    go: "Go",
    searching: "Searching…",
    textEditor: "Text Editor",
    selectAtLeastOneSite: "You need to select at least one site",

    // AI Narrative
    articleSearch: "Article Search",
    aiDigest: "IA Narrative",
    digestPlaceholder: "Ask about a topic to generate a narrative…",
    generateDigest: "Narrate",
    digestGenerating: "Generating…",
    usesLeftThisWeek: (n: number) => `${n} of 5 uses left this week`,
    digestLimitReached: "Weekly limit reached",
    digestResetsOn: (d: string) => `Available again on ${d}`,
    digestLoginRequired: "Sign in to use AI Digest",
    digestNotEnoughArticles: "Not enough relevant articles found. Try broadening your search or date range.",
    digestReferences: "References",
    digestNoResult: "No narrative was generated.",

    // ResultsPage
    searchArticlesPlaceholder: "Search articles...",
    noArticlesInCollection: "No articles in this collection.",
    noResultsFor: (q: string) => `No results for "${q}".`,
    resultsFor: "Results for",
    top: "Top",
    fit: "Fit",
    noteLabel: "Your note",
    noImage: "No image",
    noSummary: "No summary available.",
    ocrButtonLabel: "📝 Text",
    extractedText: "Extracted Text",
    extractingText: "Extracting text from image...",
    extractingNote: "This may take 10-30 seconds on first use",
    noTextFound: "No text found.",
    copyToClipboard: "📋 Copy to Clipboard",
    close: "Close",
    openInArchive: "Open in archive",

    // EditorSearchPage
    editorSearchTitle: "Editor Search",
    searchingDots: "Searching...",
    paragraph: "Paragraph",
    results: "results",
    editorPlaceholder: "Start typing your search query... (minimum 4 words per paragraph)",
    editorTip: "💡 Tip: Each paragraph is searched independently. Type at least 4 words per paragraph.",
    view: "View",
    loadingMore: "Loading more...",
    scrollForMore: "Scroll for more",

    // JournalistsPage
    journalists: "Journalists",
    noJournalistsFound: "No journalists found",

    // PublicCollectionsPage
    publicCollections: "Public Collections",

    // PrivateCollectionsPage
    myCollections: "My Collections",
    noCollectionsYet: "No collections yet",
    deleteCollection: "Delete collection",
    collectionNamePlaceholder: "Collection name",
    descriptionOptionalPlaceholder: "Description (optional)",
    newCollection: "New collection",

    // BookmarkButton
    saveToCollection: "Save to collection",
    noCollectionsYetShort: "No collections yet",

    // AnnotationButton
    viewAnnotation: "View annotation",
    addAnnotation: "Add annotation",
    yourAnnotation: "Your annotation",
    note: "Note",
    writeANote: "Write a note about this article…",

    // DateRangeFilter
    dateRange: "Date range",
    from: "From",
    to: "To",
    apply: "Apply",
    reset: "Reset",
    filterByDateRange: "Filter by date range",

    // SiteFilter
    filterBySite: "Filter by site",

    // AboutPage
    navAbout: "About",
    aboutTitle: "About PressDigger",
    aboutDescription: "PressDigger is a research tool built on top of Arquivo.pt, Portugal's web archive. It allows journalists, researchers, and curious minds to search, explore, and analyse thousands of archived newspaper articles spanning decades of Portuguese press coverage.",
    aboutStatsTitle: "What's inside",
    aboutArticles: "Articles",
    aboutPersons: "Persons",
    aboutAuthors: "Authors",
    aboutSites: "News Sites",
    aboutPublicCollections: "Public Collections",
    aboutPrivateCollections: "Private Collections",
    aboutSitesBox: "Indexed News Sites",
    aboutPersonsBox: "Persons",
    aboutArchitectureTitle: "Architecture",
    aboutArchitectureDescription: "PressDigger is composed of a backend data pipeline that crawls and indexes archived news from Arquivo.pt, a Spring Boot REST API, and this React frontend.",
    aboutLoadMore: "Loading…",
    aboutNoMorePersons: "No more persons",
    aboutExpand: "Show",
    aboutCollapse: "Hide",
    aboutCrawlerTitle: "news-crawler",
    aboutCrawlerDesc: "On startup, ArquivoCrawler generates the full set of search URLs by combining every Site × every Person × every monthly date interval from January 1996 to today, forming requests against the Arquivo.pt text search API. If URLs have already been generated in a previous run, only the unprocessed ones are loaded from the database, making the crawler resumable. The list is shuffled before processing to distribute load evenly across sites, people and time windows. For each URL, the crawler fetches the Arquivo.pt response and follows any pagination (next_page) until all result pages are collected. Before processing, each batch is deduplicated by computing a hash of the normalised title + site name, checked first against an in-memory Bloom filter and then against the database to avoid redundant work. Each candidate article is then filtered through three gates: it must be a news article (opinion columns, editorials and commentary are discarded), it must have a valid URL, and it must be complete (title, archive link, extracted text link and screenshot link all present). Articles that pass all checks are serialised as JSON and published to a Kafka topic for downstream processing by the image and text processors. Processed URLs are marked in the database so a restart resumes from where it left off.",
    aboutImageTitle: "news-image",
    aboutImageDesc: "On each Kafka message received from the crawler, ImageProcessorListener extracts the articleHash and derives the deterministic output filenames (original/{hash}.png and small/{hash}.png). If the original file already exists on disk the message is acknowledged and forwarded immediately, skipping all expensive processing. Otherwise, the article screenshot is downloaded from the linkToScreenshot URL using a URLConnection with configurable connect and read timeouts, retrying up to three times with exponential backoff on failure. The downloaded image passes through two quality gates: first, ImageBlankDetector samples the pixel grid to detect uniformly coloured (blank) images, discarding any where more than 20% of sampled pixels deviate from a reference colour within a per-channel tolerance; second, ImageTextDetector sends the image to the external OCR service and discards any image containing fewer than 250 recognised words, filtering out screenshots without readable article content. Images that pass both gates are written to disk as the full-size original, and a thumbnail is generated by first cropping to the upper portion of the image (top half of the height, capped at width + width/2 pixels) and then resizing to a square using Thumbnailator at a configurable output quality. Finally, the original article payload is enriched with originalImagePath and smallImagePath and published to the next Kafka topic for the text processor to consume.",
    aboutTextTitle: "news-text",
    aboutTextDesc: "On each Kafka message received from the image processor, TextProcessorListener first validates that linkToExtractedText is present, then fetches the raw article text from that URL via an HTTP GET with a 20-second timeout. Before spending any OpenAI credits, the raw text is passed through a relevance pre-screen that counts occurrences of the target person's full name (minimum 3 matches) or last name (minimum 5 matches) — articles that don't meet the threshold are discarded immediately. Text that passes the pre-screen is sent to OpenAI with a structured Portuguese-language prompt instructing the model to produce a factual 3–5 paragraph summary, extract the publication date in ISO 8601 format, and return a publishedDateConfidence score between 0 and 1, while strictly ignoring reader comments, opinion columns and editorial meta-text. The raw JSON returned by OpenAI is sanitised (trailing commas removed) before parsing. A second OpenAI call then acts as a strict binary relevance classifier, verifying that the generated summary is genuinely about the target person as its main subject — if the person's name doesn't appear or they are only a secondary mention, the article is rejected. In parallel, AuthorExtractor attempts to identify the article's author by first applying a regex pattern over byline keywords (por, autor, escrito por) in the first 2 000 characters of the text, then calling the external spaCy NER service to detect person-name entities, scoring and ranking all candidates by proximity, frequency and position. The final enriched payload — title, summary, published date, date confidence, author, image paths and archive link — is then published to the embeddings processor Kafka topic.",
    aboutEmbeddingsTitle: "news-embeddings",
    aboutEmbeddingsDesc: "On each Kafka message received from the text processor, TextEmbeddingListener looks up the Site by ID and saves the fully enriched Article to the database — including title, summary, published date, date confidence, archive link, image paths and the resolved or created Author record. Once the article is persisted, its summary is sent to the YAKE service to extract up to 5 Portuguese key-phrases (up to 4 words each); each keyword is created or retrieved from the Keyword table and stored alongside its relevance score in ArticleKeywordScore, forming the basis for public keyword-based collections. The article summary is then split into overlapping chunks of 3 sentences with a 1-sentence overlap using a Portuguese-locale BreakIterator, trailing punctuation is stripped from each chunk, and each chunk is sent to OpenAI (text-embedding-3-small) to produce a 1024-dimensional vector. Each chunk and its vector are saved as an ArticleChunkMedium row in PostgreSQL with the pgvector extension, enabling efficient cosine-similarity search at query time. The module also ships a TextEmbeddingBackfill component that runs on startup (when enabled) and retroactively generates the same chunks and embeddings for any articles already in the database that are missing them, making it safe to re-run or upgrade the embedding model without reprocessing the entire pipeline.",
    aboutRestTitle: "news-rest",
    aboutRestDesc: "scribe-news-rest is the Spring Boot service that exposes all functionality to the frontend. Article search (POST /articles/search) accepts a text query plus optional filters for site, date range and page, normalises the input, generates a 1024-dimensional embedding via OpenAI text-embedding-3-small, and runs a hybrid query against PostgreSQL that combines pgvector cosine-similarity over the ArticleChunkMedium table with a full-text search on the original input, returning a ranked paginated result. Narrative generation (POST /articles/narrative) is an authenticated, rate-limited feature: the same embedding + hybrid search retrieves up to 20 of the most relevant articles (at least 3 are required), their summaries, dates and IDs are serialised to JSON and sent to OpenAI gpt-4o with a structured journalistic prompt that instructs the model to produce a coherent Portuguese-language narrative with inline citation markers ([1], [2], …); the response is parsed and the markers are resolved back to full article objects before being returned. Each user's narrative usage is tracked within a configurable sliding time window, and requests exceeding the limit receive a 429 Too Many Requests response with the reset timestamp. Image streaming (GET /images/{size}/{filename}) reads files directly from the configured disk path and returns them with 365-day Cache-Control headers. The remaining controllers handle public collections (auto-generated from keyword scores), private collections (user-created, authenticated), article annotations (per-user text notes on articles), authors, sites and persons lookups. Authentication is handled via Google OAuth2 through Spring Security, with session cookies configured for cross-origin use under the press-digger.com domain.",
  },

  pt: {
    // Common
    loading: "A carregar…",
    saving: "A guardar…",
    save: "Guardar",
    cancel: "Cancelar",
    edit: "Editar",
    back: "Voltar",
    article: "artigo",
    articles: "artigos",
    failedToLoad: "Erro ao carregar coleções",
    noCollectionsFound: "Nenhuma coleção encontrada",

    // Sidebar
    openMenu: "Abrir menu",
    closeMenu: "Fechar menu",
    signIn: "Entrar",
    signOut: "Sair",
    signInToView: "Inicie sessão para ver",
    navSearch: "Pesquisa",
    navTextEditor: "Editor de Texto",
    navPublic: "Públicas",
    navPrivate: "Privadas",
    navJournalists: "Jornalistas",
    panelJournalists: "Jornalistas",
    panelPublic: "Coleções Públicas",
    panelPrivate: "As Minhas Coleções",
    comingSoon: "Em breve",
    noCollectionsFoundPanel: "Nenhuma coleção encontrada",

    // SearchPage
    searchPlaceholder: "Pesquisar jornais…",
    go: "Ir",
    searching: "A pesquisar…",
    textEditor: "Editor de Texto",
    selectAtLeastOneSite: "Precisa de selecionar pelo menos um site",

    // AI Narrative
    articleSearch: "Pesquisa de Artigos",
    aiDigest: "Narrativa IA",
    digestPlaceholder: "Pergunte sobre um tema para gerar uma narrativa…",
    generateDigest: "Narrar",
    digestGenerating: "A gerar…",
    usesLeftThisWeek: (n: number) => `${n} de 5 utilizações restantes esta semana`,
    digestLimitReached: "Limite semanal atingido",
    digestResetsOn: (d: string) => `Disponível novamente em ${d}`,
    digestLoginRequired: "Inicie sessão para usar o AI Digest",
    digestNotEnoughArticles: "Não foram encontrados artigos suficientes. Tente alargar a pesquisa ou o intervalo de datas.",
    digestReferences: "Referências",
    digestNoResult: "Nenhuma narrativa foi gerada.",

    // ResultsPage
    searchArticlesPlaceholder: "Pesquisar artigos...",
    noArticlesInCollection: "Sem artigos nesta coleção.",
    noResultsFor: (q: string) => `Sem resultados para "${q}".`,
    resultsFor: "Resultados para",
    top: "Topo",
    fit: "Ajustar",
    noteLabel: "A sua nota",
    noImage: "Sem imagem",
    noSummary: "Sem resumo disponível.",
    ocrButtonLabel: "📝 Texto",
    extractedText: "Texto Extraído",
    extractingText: "A extrair texto da imagem...",
    extractingNote: "Pode demorar 10-30 segundos na primeira utilização",
    noTextFound: "Nenhum texto encontrado.",
    copyToClipboard: "📋 Copiar para a Área de Transferência",
    close: "Fechar",
    openInArchive: "Abrir no arquivo",

    // EditorSearchPage
    editorSearchTitle: "Pesquisa por Texto",
    searchingDots: "A pesquisar...",
    paragraph: "Parágrafo",
    results: "resultados",
    editorPlaceholder: "Comece a escrever a sua pesquisa... (mínimo 4 palavras por parágrafo)",
    editorTip: "💡 Dica: Cada parágrafo é pesquisado de forma independente. Escreva pelo menos 4 palavras por parágrafo.",
    view: "Ver",
    loadingMore: "A carregar mais...",
    scrollForMore: "Deslize para ver mais",

    // JournalistsPage
    journalists: "Jornalistas",
    noJournalistsFound: "Nenhum jornalista encontrado",

    // PublicCollectionsPage
    publicCollections: "Coleções Públicas",

    // PrivateCollectionsPage
    myCollections: "As Minhas Coleções",
    noCollectionsYet: "Ainda sem coleções",
    deleteCollection: "Eliminar coleção",
    collectionNamePlaceholder: "Nome da coleção",
    descriptionOptionalPlaceholder: "Descrição (opcional)",
    newCollection: "Nova coleção",

    // BookmarkButton
    saveToCollection: "Guardar na coleção",
    noCollectionsYetShort: "Ainda sem coleções",

    // AnnotationButton
    viewAnnotation: "Ver anotação",
    addAnnotation: "Adicionar anotação",
    yourAnnotation: "A sua anotação",
    note: "Nota",
    writeANote: "Escreva uma nota sobre este artigo…",

    // DateRangeFilter
    dateRange: "Intervalo de datas",
    from: "De",
    to: "Até",
    apply: "Aplicar",
    reset: "Repor",
    filterByDateRange: "Filtrar por intervalo de datas",

    // SiteFilter
    filterBySite: "Filtrar por site",

    // AboutPage
    navAbout: "Sobre",
    aboutTitle: "Sobre o PressDigger",
    aboutDescription: "O PressDigger é uma ferramenta de investigação construída sobre o Arquivo.pt, o arquivo web de Portugal. Permite a jornalistas, investigadores e curiosos pesquisar, explorar e analisar milhares de artigos de jornais arquivados ao longo de décadas de imprensa portuguesa.",
    aboutStatsTitle: "O que está dentro",
    aboutArticles: "Artigos",
    aboutPersons: "Pessoas",
    aboutAuthors: "Autores",
    aboutSites: "Sites de Notícias",
    aboutPublicCollections: "Coleções Públicas",
    aboutPrivateCollections: "Coleções Privadas",
    aboutSitesBox: "Sites Indexados",
    aboutPersonsBox: "Pessoas",
    aboutArchitectureTitle: "Arquitetura",
    aboutArchitectureDescription: "O PressDigger é composto por um pipeline de dados que recolhe e indexa notícias arquivadas do Arquivo.pt, uma API REST em Spring Boot, e este frontend em React.",
    aboutLoadMore: "A carregar…",
    aboutNoMorePersons: "Sem mais pessoas",
    aboutExpand: "Mostrar",
    aboutCollapse: "Ocultar",
    aboutCrawlerTitle: "news-crawler",
    aboutCrawlerDesc: "Ao iniciar, o ArquivoCrawler gera o conjunto completo de URLs de pesquisa combinando cada Site × cada Pessoa × cada intervalo de datas mensais desde janeiro de 1996 até hoje, formando pedidos para a API de pesquisa de texto do Arquivo.pt. Se os URLs já foram gerados numa execução anterior, apenas os não processados são carregados da base de dados, tornando o crawler retomável. A lista é embaralhada antes do processamento para distribuir a carga de forma uniforme entre sites, pessoas e janelas temporais. Para cada URL, o crawler obtém a resposta do Arquivo.pt e segue a paginação (next_page) até recolher todas as páginas de resultados. Antes do processamento, cada lote é deduplicado calculando um hash do título normalizado + nome do site, verificado primeiro num filtro de Bloom em memória e depois na base de dados para evitar trabalho redundante. Cada artigo candidato é filtrado por três critérios: deve ser um artigo de notícias (colunas de opinião, editoriais e comentários são descartados), deve ter um URL válido, e deve estar completo (título, ligação de arquivo, ligação para texto extraído e ligação para screenshot presentes). Os artigos que passam todas as verificações são serializados como JSON e publicados num tópico Kafka para processamento posterior pelos processadores de imagem e texto. Os URLs processados são marcados na base de dados para que um reinício retome de onde parou.",
    aboutImageTitle: "news-image",
    aboutImageDesc: "Em cada mensagem Kafka recebida do crawler, o ImageProcessorListener extrai o articleHash e deriva os nomes de ficheiro de saída determinísticos (original/{hash}.png e small/{hash}.png). Se o ficheiro original já existir no disco, a mensagem é confirmada e reencaminhada imediatamente, ignorando todo o processamento dispendioso. Caso contrário, o screenshot do artigo é descarregado a partir do URL linkToScreenshot usando uma URLConnection com timeouts de ligação e leitura configuráveis, tentando novamente até três vezes com recuo exponencial em caso de falha. A imagem descarregada passa por dois controlos de qualidade: primeiro, o ImageBlankDetector amostra a grelha de pixels para detetar imagens uniformemente coloridas (em branco), descartando aquelas em que mais de 20% dos pixels amostrados se desviam de uma cor de referência dentro de uma tolerância por canal; segundo, o ImageTextDetector envia a imagem para o serviço OCR externo e descarta qualquer imagem com menos de 250 palavras reconhecidas, filtrando screenshots sem conteúdo de artigo legível. As imagens que passam ambos os controlos são escritas no disco como original em tamanho completo, e uma miniatura é gerada recortando primeiro para a parte superior da imagem (metade superior da altura, limitado a largura + largura/2 pixels) e depois redimensionando para um quadrado usando Thumbnailator com qualidade de saída configurável. Por fim, o payload original do artigo é enriquecido com originalImagePath e smallImagePath e publicado no próximo tópico Kafka para o processador de texto consumir.",
    aboutTextTitle: "news-text",
    aboutTextDesc: "Em cada mensagem Kafka recebida do processador de imagens, o TextProcessorListener valida primeiro que o linkToExtractedText está presente, depois obtém o texto bruto do artigo a partir desse URL via HTTP GET com um timeout de 20 segundos. Antes de gastar créditos OpenAI, o texto bruto é submetido a uma pré-triagem de relevância que conta as ocorrências do nome completo da pessoa alvo (mínimo 3 correspondências) ou apelido (mínimo 5 correspondências) — os artigos que não cumprem o limiar são descartados imediatamente. O texto que passa a pré-triagem é enviado para o OpenAI com um prompt estruturado em português instruindo o modelo a produzir um resumo factual de 3 a 5 parágrafos, extrair a data de publicação no formato ISO 8601, e devolver uma pontuação publishedDateConfidence entre 0 e 1, ignorando estritamente comentários de leitores, colunas de opinião e meta-texto editorial. O JSON bruto devolvido pelo OpenAI é saneado (vírgulas finais removidas) antes da análise. Uma segunda chamada ao OpenAI funciona como classificador binário estrito de relevância, verificando que o resumo gerado é genuinamente sobre a pessoa alvo como sujeito principal — se o nome da pessoa não aparecer ou for apenas uma menção secundária, o artigo é rejeitado. Em paralelo, o AuthorExtractor tenta identificar o autor do artigo aplicando primeiro um padrão regex sobre palavras-chave de assinatura (por, autor, escrito por) nos primeiros 2 000 caracteres do texto, depois chamando o serviço NER spaCy externo para detetar entidades de nomes de pessoas, pontuando e classificando todos os candidatos por proximidade, frequência e posição. O payload final enriquecido — título, resumo, data de publicação, confiança da data, autor, caminhos de imagens e ligação de arquivo — é então publicado no tópico Kafka do processador de embeddings.",
    aboutEmbeddingsTitle: "news-embeddings",
    aboutEmbeddingsDesc: "Em cada mensagem Kafka recebida do processador de texto, o TextEmbeddingListener procura o Site por ID e guarda o Artigo completamente enriquecido na base de dados — incluindo título, resumo, data de publicação, confiança da data, ligação de arquivo, caminhos de imagens e o registo de Autor resolvido ou criado. Uma vez que o artigo é persistido, o seu resumo é enviado para o serviço YAKE para extrair até 5 expressões-chave em português (até 4 palavras cada); cada palavra-chave é criada ou obtida da tabela Keyword e armazenada juntamente com o seu score de relevância em ArticleKeywordScore, formando a base para coleções públicas baseadas em palavras-chave. O resumo do artigo é então dividido em partes sobrepostas de 3 frases com sobreposição de 1 frase usando um BreakIterator de localidade portuguesa, a pontuação final é removida de cada parte, e cada parte é enviada para o OpenAI (text-embedding-3-small) para produzir um vetor de 1024 dimensões. Cada parte e o seu vetor são guardados como uma linha ArticleChunkMedium no PostgreSQL com a extensão pgvector, permitindo uma pesquisa eficiente por semelhança de cosseno no momento da consulta. O módulo também inclui um componente TextEmbeddingBackfill que é executado no arranque (quando ativado) e gera retroativamente os mesmos fragmentos e embeddings para quaisquer artigos já na base de dados que não os tenham, tornando segura a re-execução ou a atualização do modelo de embedding sem reprocessar todo o pipeline.",
    aboutRestTitle: "news-rest",
    aboutRestDesc: "O scribe-news-rest é o serviço Spring Boot que expõe toda a funcionalidade ao frontend. A pesquisa de artigos (POST /articles/search) aceita uma consulta de texto mais filtros opcionais de site, intervalo de datas e página, normaliza a entrada, gera um embedding de 1024 dimensões via OpenAI text-embedding-3-small, e executa uma consulta híbrida contra o PostgreSQL que combina semelhança de cosseno pgvector sobre a tabela ArticleChunkMedium com pesquisa de texto integral sobre a entrada original, devolvendo um resultado paginado e ordenado. A geração de narrativa (POST /articles/narrative) é uma funcionalidade autenticada e com limite de taxa: o mesmo embedding + pesquisa híbrida obtém até 20 dos artigos mais relevantes (são necessários pelo menos 3), os seus resumos, datas e IDs são serializados em JSON e enviados para o OpenAI gpt-4o com um prompt jornalístico estruturado que instrui o modelo a produzir uma narrativa coerente em português com marcadores de citação inline ([1], [2], …); a resposta é analisada e os marcadores são resolvidos de volta para objetos de artigo completos antes de serem devolvidos. O uso de narrativas de cada utilizador é rastreado numa janela de tempo deslizante configurável, e os pedidos que excedem o limite recebem uma resposta 429 Too Many Requests com o timestamp de reinício. O streaming de imagens (GET /images/{size}/{filename}) lê ficheiros diretamente do caminho de disco configurado e devolve-os com cabeçalhos Cache-Control de 365 dias. Os restantes controladores tratam de coleções públicas (geradas automaticamente a partir de pontuações de palavras-chave), coleções privadas (criadas pelo utilizador, autenticadas), anotações de artigos (notas de texto por utilizador sobre artigos), autores, sites e pesquisa de pessoas. A autenticação é gerida via Google OAuth2 através do Spring Security, com cookies de sessão configurados para uso entre origens no domínio press-digger.com.",
  },
} as const satisfies Record<Lang, Record<string, unknown>>

export type Translations = typeof translations.en
