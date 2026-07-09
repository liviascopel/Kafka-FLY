package br.ufes.soe.service.user;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.domain.user.Usuario;
import br.ufes.soe.repository.user.UsuarioRepository;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class TimeDistanceService {

    private static final String ORS_URL =
            "https://api.openrouteservice.org/v2/directions/driving-car";

    private final UsuarioRepository usuarioRepository;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImZjNTMxOTY0ZTI3NjRjZTU5ODY0YTYwNWE0ZDQ0MTk0IiwiaCI6Im11cm11cjY0In0=";

    public TimeDistanceService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public int calculaTempoEnderecoAteAeroporto(
            Long usuarioId,
            Double latitudeAeroporto,
            Double longitudeAeroporto
    ) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Double latitudeUsuario = usuario.getEndereco().getLatitude();
        Double longitudeUsuario = usuario.getEndereco().getLongitude();

        if (latitudeUsuario == null || longitudeUsuario == null) {
            throw new RuntimeException("Usuário não possui coordenadas cadastradas");
        }

        try {
            String jsonBody = String.format(java.util.Locale.US, """
                    {
                      "coordinates": [
                        [%f, %f],
                        [%f, %f]
                      ]
                    }
                    """,
                    longitudeUsuario, latitudeUsuario,
                    longitudeAeroporto, latitudeAeroporto
            );

            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(ORS_URL)
                    .addHeader("Authorization", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request.newBuilder().post(body).build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Erro ao consultar OpenRouteService: " + response.code());
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);

                double durationSeconds = root
                        .get("routes")
                        .get(0)
                        .get("summary")
                        .get("duration")
                        .asDouble();

                return (int) Math.ceil(durationSeconds / 60.0);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular rota até o aeroporto", e);
        }
    }
} 
