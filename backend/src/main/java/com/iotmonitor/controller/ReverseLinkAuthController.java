package com.iotmonitor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.KeyAgreement;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/reverselink")
public class ReverseLinkAuthController {

    // Store the derived AES key temporarily for this session (legacy QR pairing)
    public static byte[] derivedAesKey = null;

    // Email-based auth: emails that have been granted access via /reverselink/discover
    public static final Set<String> authorizedEmails = ConcurrentHashMap.newKeySet();

    private PrivateKey serverPrivateKey;
    private PublicKey serverPublicKey;

    public ReverseLinkAuthController() throws Exception {
        // Generate ephemeral ECDH keypair for the server on startup
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        this.serverPrivateKey = kp.getPrivate();
        this.serverPublicKey = kp.getPublic();
    }

    @GetMapping("/qr-payload")
    public ResponseEntity<?> getQrPayload() {
        // Android scans this to know the PC's public key
        String base64PublicKey = Base64.getEncoder().encodeToString(serverPublicKey.getEncoded());
        return ResponseEntity.ok(Map.of("publicKey", base64PublicKey));
    }

    @PostMapping("/pair")
    public ResponseEntity<?> pairDevice(@RequestBody Map<String, String> payload) {
        try {
            // Android sends its public key
            String clientPublicKeyStr = payload.get("clientPublicKey");
            byte[] clientKeyBytes = Base64.getDecoder().decode(clientPublicKeyStr);

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(clientKeyBytes);
            PublicKey clientPublicKey = keyFactory.generatePublic(x509KeySpec);

            // Execute Diffie-Hellman Key Agreement
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(serverPrivateKey);
            keyAgreement.doPhase(clientPublicKey, true);

            // Derive the shared secret (AES Key)
            derivedAesKey = keyAgreement.generateSecret();
            
            // To be secure, we usually hash the secret with SHA-256 to ensure exactly 256-bit AES key lengths.
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            derivedAesKey = sha256.digest(derivedAesKey);

            return ResponseEntity.ok(Map.of("status", "success", "message", "Keys Derived Validly"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
