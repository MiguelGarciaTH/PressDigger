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
  },
} as const satisfies Record<Lang, Record<string, unknown>>

export type Translations = typeof translations.en
