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
  },
} as const satisfies Record<Lang, Record<string, unknown>>

export type Translations = typeof translations.en
