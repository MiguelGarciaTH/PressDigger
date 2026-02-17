package arquivo.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class OpenIAIntegration {

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
            Você é um classificador binário.
            
            OBJETIVO:
            Determinar se o resumo fornecido é principalmente sobre a pessoa indicada.
            
            ENTRADAS:
            - "personName": nome completo da pessoa a verificar
            - "summary": resumo factual da notícia
            
            CRITÉRIO DE DECISÃO:
            
            Responda true se:
            - A pessoa for o sujeito principal do acontecimento descrito.
            - O resumo girar principalmente em torno das ações, declarações ou situação dessa pessoa.
            - A notícia tratar diretamente dessa pessoa como figura central.
            
            Responda false se:
            - A pessoa for apenas mencionada de forma secundária.
            - A pessoa aparecer apenas em contexto histórico ou comparativo.
            - O foco principal da notícia for outro indivíduo, entidade ou evento.
            - O nome aparecer apenas uma vez sem relevância central.
            
            REGRAS:
            - Seja rigoroso.
            - Não faça inferências externas.
            - Baseie-se apenas no texto do resumo.
            - Ignore conhecimento externo.
            - Não explique sua decisão.
            
            FORMATAÇÃO:
            Responda SOMENTE com JSON válido no formato:
            { "isAboutPerson": true }
            
            ou
            
            { "isAboutPerson": false }
            """;

    public OpenIAIntegration(String apiKey) {
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

        String input = promptIsAbout +
                "\n\npersonName: " + personName +
                "\n\nsummary:\n" + summary;

        final ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4o-mini")
                .temperature(0.0)
                .input(input)
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

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);

        return node.get("isAboutPerson").asBoolean();
    }

}
