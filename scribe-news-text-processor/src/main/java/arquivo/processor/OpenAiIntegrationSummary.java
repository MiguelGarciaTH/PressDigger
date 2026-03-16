package arquivo.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class OpenAiIntegrationSummary {

    private final OpenAIClient client;

    private final String promptSummary = """
            Você é um assistente que recebe o texto bruto (apenas texto) de uma página de notícia em Português.
            O texto pode incluir título, subtítulos, corpo da notícia, legendas, colunas laterais, rodapés,
            secções de comentários de leitores, caixas editoriais e textos meta.
            
            OBJETIVO:
            Extrair e resumir apenas o conteúdo jornalístico factual principal da notícia.
            
            TAREFAS (faça tudo em português):
            
            1) Extraia a data de publicação, se houver.
               - Coloque em ISO 8601 (YYYY-MM-DD) em "publishedDate".
               - Só inclua se estiver explicitamente indicada no conteúdo jornalístico.
               - Se houver dúvida, use null.
            
            2) Produza um resumo factual do acontecimento noticiado em "summary".
               - 3 a 5 parágrafos.
               - Cada parágrafo deve ter 2 a 4 frases curtas e diretas.
               - Cada frase deve expressar uma única ideia principal.
               - Prefira frases claras e separadas por ponto final. Evite frases longas ligadas por vírgulas.
               - Evite linguagem opinativa, especulativa ou interpretativa.
            
            3) Forneça "publishDateConfidence": número entre 0 e 1
               - 1.0 = data explícita e inequívoca
               - 0.5 = data inferida com contexto forte
               - 0.0 = nenhuma data confiável encontrada
            
            4) Formate a saída SOMENTE como JSON com as chaves:
            { "publishedDate": ..., "publishedDateConfidence": ..., "summary": ... }
            
            REGRAS DE FILTRAGEM (OBRIGATÓRIAS):
            Ignore completamente:
            • Comentários, opiniões ou reações de leitores
            • Secções “comentários”, “opinião dos leitores”, “cartas ao diretor”
            • Texto do provedor do leitor ou colunas sobre o próprio jornal
            • Discussões sobre títulos, escolhas editoriais ou reações ao artigo
            
            Não inclua no resumo:
            • Referências a leitores ou comentários
            • Referências ao jornal, ao artigo ou ao ato de publicação
            • Frases como “o artigo diz”, “a notícia relata”, “segundo o jornal”
            
            Inclua apenas:
            • Factos
            • Acontecimentos
            • Datas
            • Locais
            • Declarações atribuídas
            • Contexto relevante para entender o evento
            
            REGRAS DE ESCRITA DO RESUMO:
            - Escreva como descrição direta dos acontecimentos.
            - Use linguagem factual, simples e informativa.
            - Evite orações excessivamente longas (máx. ~25 palavras por frase).
            - Evite conjunções encadeadas (como “enquanto”, “onde”, “o que”, “sendo que”) sempre que possível.
            - Prefira várias frases curtas em vez de uma frase longa.
            
            FORMATAÇÃO:
            - Separe parágrafos com "\\n".
            - NÃO use quebras de linha reais dentro do JSON.
            - Produza JSON válido.
            - Não inclua qualquer texto fora do JSON.
            """;


    private final String promptIsAbout = """
            Você é um classificador binário ESTRITO.
            
            OBJETIVO:
            Determinar se o resumo fornecido é principalmente sobre a pessoa indicada.
            
            REGRA OBRIGATÓRIA PRIMÁRIA:
            Se o nome da pessoa NÃO aparecer explicitamente no resumo, responda false IMEDIATAMENTE.
            Não faça inferências. Não assuma conexões. Não use conhecimento externo.
            
            CRITÉRIO DE DECISÃO (apenas se o nome aparecer):
            
            Responda true SOMENTE se TODAS estas condições forem verdadeiras:
            1. O nome completo ou sobrenome distintivo da pessoa aparece no resumo
            2. A pessoa é o sujeito principal do acontecimento (não apenas mencionada)
            3. O resumo descreve principalmente ações, declarações ou situações dessa pessoa
            4. A notícia trata diretamente dessa pessoa como figura central
            
            Responda false se QUALQUER destas for verdadeira:
            1. O nome da pessoa não aparece no resumo
            2. A pessoa é mencionada apenas de forma secundária ou contextual
            3. A pessoa aparece apenas em comparação histórica
            4. O foco principal é outro indivíduo, entidade ou evento
            5. O nome aparece uma vez sem ser o tema central
            6. A pessoa é apenas citada como fonte ou comentarista
            
            REGRAS DE PROCESSAMENTO:
            - Seja EXTREMAMENTE rigoroso
            - Baseie-se EXCLUSIVAMENTE no texto do resumo fornecido
            - IGNORE completamente conhecimento externo ou contexto histórico
            - Em caso de DÚVIDA, responda false
            - Não explique, não justifique
            
            FORMATO DE ENTRADA E SAÍDA:
            Você receberá um JSON com:
            {
              "personName": "nome completo da pessoa",
              "summary": "texto do resumo"
            }
            
            Responda SOMENTE com JSON válido:
            { "isAboutPerson": true }
            ou
            { "isAboutPerson": false }
            
            Não inclua nenhum texto adicional fora do JSON.
            """;

    public OpenAiIntegrationSummary(String apiKey) {
        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    public String summarizeText(String text) {
        final ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4o-mini")
                .input(promptSummary + "\n\n" + text)
                .build();

        final Response response = client.responses().create(params);

        return response.output().getFirst().message().get().content().getFirst().asOutputText().text().replaceAll("^```json\\s*|```\\s*$", "").trim();
    }

    public boolean isAbout(String summary, String personName) throws Exception {
        // Pre-check: if name doesn't appear at all in summary, return false immediately
        if (!containsPersonName(summary, personName)) {
            return false;
        }

        // Construct structured JSON input
        ObjectMapper mapper = new ObjectMapper();
        String inputJson = mapper.writeValueAsString(
            mapper.createObjectNode()
                .put("personName", personName)
                .put("summary", summary)
        );

        String fullPrompt = promptIsAbout + "\n\nINPUT:\n" + inputJson;

        final ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4o-mini")
                .temperature(0.0)
                .input(fullPrompt)
                .build();

        final Response response = client.responses().create(params);

        String json = response.output()
                .getFirst()
                .message()
                .get()
                .content()
                .getFirst()
                .asOutputText()
                .text()
                .replaceAll("^```json\\s*|```\\s*$", "")
                .trim();

        JsonNode node = mapper.readTree(json);

        return node.get("isAboutPerson").asBoolean();
    }

    /**
     * Quick pre-check to see if person name appears in summary.
     * Handles partial matches (e.g., "Costa" matching "António Costa").
     */
    private boolean containsPersonName(String summary, String personName) {
        if (summary == null || personName == null) {
            return false;
        }

        String summaryLower = summary.toLowerCase();
        String nameLower = personName.toLowerCase();

        // Check if full name appears
        if (summaryLower.contains(nameLower)) {
            return true;
        }

        // Check if last name appears (assuming Western name format)
        String[] nameParts = nameLower.split("\\s+");
        if (nameParts.length > 1) {
            String lastName = nameParts[nameParts.length - 1];
            // Only match if last name is distinctive (more than 3 chars)
            if (lastName.length() > 3 && summaryLower.contains(lastName)) {
                return true;
            }
        }

        return false;
    }

}
