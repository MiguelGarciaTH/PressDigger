package arquivo.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthController(@Value("${google.client.id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @PostMapping("/google/callback")
    public ResponseEntity<?> handleGoogleLogin(
            @RequestBody Map<String, String> body,
            HttpSession session
    ) {
        try {
            String credential = body.get("credential");
            if (credential == null || credential.isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "Missing credential"));
            }

            // Verify the Google JWT token
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            // Store user in session
            Map<String, Object> user = Map.of(
                    "googleId", googleId,
                    "email", email,
                    "name", name,
                    "picture", picture
            );
            session.setAttribute("user", user);

            return ResponseEntity.ok(user);

        } catch (Exception e) {
            // Log internally but do NOT expose internal error details to the client
            log.error("Google authentication failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Authentication failed"));
        }
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUser(HttpSession session) {
        Object user = session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(user);
    }


    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }
}