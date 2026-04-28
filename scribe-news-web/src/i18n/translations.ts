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
    searchGeneralError: "Search failed. Please try again.",

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
    digestNotEnoughArticles: "Not enough articles found for this topic. Try a broader query, a wider date range, or select more news sites.",
    digestGeneralError: "The narrative could not be generated due to an unexpected error. Please try again later.",
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
    aboutDescription: "PressDigger is a tool for journalists, researchers, and the general public to explore and analyze Portuguese politicians' news articles from Arquivo.pt. The system provides powerful search capabilities, article summarization, and semantic analysis to help users discover relevant news content efficiently.\n\nThe inspiration for PressDigger was the microfilm reader machines that appeared in several American thriller movies, where police officers and detectives would search for news articles in a large archive of microfilms. PressDigger is a modern digital version of that concept, allowing users to search and analyze news articles from the comfort of their own devices.",
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
    aboutCrawlerDesc: "The first stage of the pipeline is responsible for discovering news articles from Arquivo.pt. It systematically searches the archive by combining all known news sites, politicians, and monthly date intervals going back to 1996. The crawler handles pagination, deduplicates results using hashing and a Bloom filter, and filters out opinion pieces, editorials, and incomplete entries. Valid articles are published to a Kafka queue, feeding the image processing stage. The process is fully resumable — if interrupted, it picks up exactly where it left off.",
    aboutImageTitle: "news-image",
    aboutImageDesc: "Once an article is discovered by the crawler, this component retrieves and validates its screenshot from Arquivo.pt. Each image goes through quality checks — blank or nearly uniform images are discarded, and OCR is used to ensure the screenshot contains enough readable text. Images that pass are saved in full size and as cropped thumbnails, then forwarded along with the article metadata to the text processing stage via Kafka.",
    aboutTextTitle: "news-text",
    aboutTextDesc: "After image validation, this component fetches the article's extracted text and determines whether the article is genuinely about the target politician. A relevance check counts name occurrences before any AI processing begins. Relevant articles are sent to OpenAI for summarization, date extraction, and a second-pass relevance classification. In parallel, the author is identified using pattern matching and named entity recognition via spaCy. The enriched article — with summary, publication date, author, and image paths — is then passed to the embeddings stage.",
    aboutEmbeddingsTitle: "news-embeddings",
    aboutEmbeddingsDesc: "With the article fully processed, this component persists it to the database and prepares it for semantic search. Keywords are extracted using YAKE to power the public collections. The article summary is split into overlapping sentence chunks, and each chunk is transformed into a vector using Cohere's embedding model (embed-multilingual-v3.0). These vectors are stored in PostgreSQL with the pgvector extension, enabling fast cosine-similarity search. A backfill mechanism ensures older articles can be retroactively embedded when the model is updated.",
    aboutRestTitle: "news-rest",
    aboutRestDesc: "The REST API, built with Spring Boot, serves as the bridge between the data pipeline and the React frontend. Article search combines vector similarity with full-text search for hybrid ranked results. Authenticated users can generate AI-powered narratives that weave relevant articles into a coherent journalistic account with inline citations. The API also handles image serving, public and private collections, article annotations, author and politician lookups, all secured via Google OAuth2.",
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
    searchGeneralError: "A pesquisa falhou. Por favor, tente novamente.",

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
    digestNotEnoughArticles: "Não foram encontrados artigos suficientes sobre este tema. Tente uma pesquisa mais abrangente, um intervalo de datas mais alargado ou selecione mais sites de notícias.",
    digestGeneralError: "Não foi possível gerar a narrativa devido a um erro inesperado. Por favor, tente mais tarde.",
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
    aboutDescription: "O PressDigger é uma ferramenta para jornalistas, investigadores e o público em geral para explorar e analisar artigos de notícias sobre políticos portugueses a partir do Arquivo.pt. O sistema oferece capacidades avançadas de pesquisa, resumo de artigos e análise semântica para ajudar os utilizadores a descobrir conteúdo noticioso relevante de forma eficiente.\n\nA inspiração para o PressDigger foram as máquinas leitoras de microfilmes que apareciam em vários filmes de suspense americanos, onde polícias e detetives pesquisavam artigos de notícias em grandes arquivos de microfilmes. O PressDigger é uma versão digital moderna desse conceito, permitindo pesquisar e analisar artigos de notícias a partir do conforto dos seus próprios dispositivos.",
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
    aboutCrawlerDesc: "A primeira etapa do pipeline é responsável por descobrir artigos de notícias no Arquivo.pt. Pesquisa sistematicamente o arquivo combinando todos os sites de notícias conhecidos, políticos e intervalos de datas mensais desde 1996. O crawler gere a paginação, elimina duplicados através de hashing e de um filtro de Bloom, e descarta peças de opinião, editoriais e entradas incompletas. Os artigos válidos são publicados numa fila Kafka, alimentando a etapa de processamento de imagem. O processo é totalmente retomável — se for interrompido, recomeça exatamente onde parou.",
    aboutImageTitle: "news-image",
    aboutImageDesc: "Assim que um artigo é descoberto pelo crawler, este componente obtém e valida a captura de ecrã correspondente a partir do Arquivo.pt. Cada imagem passa por controlos de qualidade — imagens em branco ou quase uniformes são descartadas, e é utilizado OCR para garantir que a captura contém texto legível suficiente. As imagens aprovadas são guardadas em tamanho original e como miniaturas recortadas, sendo depois encaminhadas juntamente com os metadados do artigo para a etapa de processamento de texto via Kafka.",
    aboutTextTitle: "news-text",
    aboutTextDesc: "Após a validação da imagem, este componente obtém o texto extraído do artigo e determina se este é genuinamente sobre o político-alvo. Uma verificação de relevância conta as ocorrências do nome antes de qualquer processamento com IA. Os artigos relevantes são enviados para o OpenAI para sumarização, extração de data e uma segunda classificação de relevância. Em paralelo, o autor é identificado através de correspondência de padrões e reconhecimento de entidades nomeadas via spaCy. O artigo enriquecido — com resumo, data de publicação, autor e caminhos de imagens — é então passado para a etapa de embeddings.",
    aboutEmbeddingsTitle: "news-embeddings",
    aboutEmbeddingsDesc: "Com o artigo totalmente processado, este componente persiste-o na base de dados e prepara-o para pesquisa semântica. São extraídas palavras-chave através do YAKE para alimentar as coleções públicas. O resumo do artigo é dividido em fragmentos sobrepostos de frases, e cada fragmento é transformado num vetor utilizando o modelo de embeddings da Cohere (embed-multilingual-v3.0). Estes vetores são armazenados no PostgreSQL com a extensão pgvector, permitindo pesquisa rápida por semelhança de cosseno. Um mecanismo de preenchimento retroativo garante que artigos mais antigos possam ser indexados quando o modelo é atualizado.",
    aboutRestTitle: "news-rest",
    aboutRestDesc: "A API REST, construída com Spring Boot, serve de ponte entre o pipeline de dados e o frontend em React. A pesquisa de artigos combina semelhança vetorial com pesquisa de texto integral para resultados híbridos ordenados. Os utilizadores autenticados podem gerar narrativas assistidas por IA que entrelaçam artigos relevantes num relato jornalístico coerente com citações inline. A API trata também da entrega de imagens, coleções públicas e privadas, anotações de artigos, pesquisa de autores e políticos, tudo protegido via Google OAuth2.",
  },
} as const satisfies Record<Lang, Record<string, unknown>>

export type Translations = typeof translations.en
