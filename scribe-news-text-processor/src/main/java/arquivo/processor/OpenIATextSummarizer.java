package arquivo.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class OpenIATextSummarizer {

    private final OpenAIClient client;
    private final ObjectMapper mapper;

    private final String prompt = """
            Você é um assistente que recebe o texto bruto (apenas texto) de uma página de notícia em Português.
            O texto pode incluir título, subtítulos, corpo da notícia, legendas, colunas laterais, rodapés,
            secções de comentários de leitores, caixas editoriais e textos meta.
            
            Objetivo:
            Extrair e resumir **apenas o conteúdo jornalístico principal da notícia**.
            
            Tarefas (faça tudo em português):
            1) Extraia a data de publicação, se houver — coloque em ISO 8601 (YYYY-MM-DD) em "publish_date".
               - Se não houver uma data clara no conteúdo jornalístico, deixe null.
            2) Produza um resumo conciso do **conteúdo factual da notícia** com 3–5 pagráfos em "summary".
            3) Forneça "highlights": 3 bullets (máx. 10 palavras cada) com os pontos-chave factuais.
            4) Forneça "publish_date_confidence": número entre 0 e 1 (0 = não confiante, 1 = muito confiante).
            5) Formate a saída **somente** como JSON com as chaves:
               { "publish_date": ..., "publish_date_confidence": ..., "summary": ..., "highlights": [...] }
            
            Regras de filtragem (OBRIGATÓRIAS):
            - Ignore completamente:
              • Comentários, opiniões ou reações de leitores
              • Secções do tipo “comentários”, “opinião dos leitores”, “cartas ao diretor”
              • Texto do provedor do leitor, ombudsman ou colunas sobre o próprio jornal
              • Discussões sobre títulos, escolhas editoriais ou reações ao artigo
            - Não mencione leitores, comentários, o jornal, o título ou o ato de publicação no resumo.
            - Considere apenas factos, acontecimentos, declarações e contexto do evento noticiado.
            
            Regras para o resumo:
            - Escreva como se estivesse a descrever diretamente os acontecimentos.
            - NÃO refira que o texto é um artigo, coluna ou notícia.
            - NÃO use meta-linguagem jornalística (“o artigo analisa”, “o texto explica”).
            - Use linguagem factual, direta e informativa.
            - Se possível manter uma estrutura em paragráfos - estes devem ser marcados por quebra de linha (\n)
            
            Datas:
            - Prefira datas encontradas perto do topo do texto (títulos/subtítulos), mas verifique todo o texto.
            - Se a data for ambígua (ex.: “ontem”, “segunda-feira”), só infira uma data absoluta se houver
              contexto temporal claro; caso contrário, use publish_date = null e baixa confiança.
            
            Formato:
            - Produza exclusivamente JSON válido.
            - Não inclua texto explicativo fora do JSON.
            """;


    public OpenIATextSummarizer(String apiKey, ObjectMapper mapper) {
        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
        this.mapper = mapper;

    }

    public JsonNode summarizeTextWithOpenAI(String text) throws JsonProcessingException {
        final ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4o-mini")
                .input(prompt + "\n\n" + text)
                .build();

        final Response response = client.responses().create(params);


        final String responseString = response.output().getFirst().message().get().content().getFirst().asOutputText().text().replaceAll("^```json\\s*|```\\s*$", "").trim();


        return mapper.readTree(text);

    }
}
