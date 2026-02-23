package arquivo.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    @Value("${google.client.id}")
    private String clientId;

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
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
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