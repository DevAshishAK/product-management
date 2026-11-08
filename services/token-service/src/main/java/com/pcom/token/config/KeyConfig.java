package com.pcom.token.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class KeyConfig {
    private static final Logger log = LoggerFactory.getLogger(KeyConfig.class);

    @Bean
    RSAKey signingKey(TokenProperties props) throws Exception {
        RSAPublicKey pub;
        RSAPrivateKey priv;
        if (StringUtils.hasText(props.getPrivateKey()) && StringUtils.hasText(props.getPublicKey())) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(decode(props.getPrivateKey())));
            pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(decode(props.getPublicKey())));
        } else if (props.isGenerateKeyIfMissing()) {
            log.warn("No JWT signing key configured - generating an EPHEMERAL key pair (dev only)");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            pub = (RSAPublicKey) pair.getPublic();
            priv = (RSAPrivateKey) pair.getPrivate();
        } else {
            throw new IllegalStateException("app.token.private-key / public-key are required (see scripts/generate-keys.sh)");
        }
        try {
            return new RSAKey.Builder(pub).privateKey(priv).keyIDFromThumbprint().build();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Local decoder replaces the JWKS-based one so this service validates its own tokens without HTTP calls. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    }

    private static byte[] decode(String pem) {
        return Base64.getDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
    }
}
