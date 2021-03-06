package act.app.util;

import act.Act;
import act.app.App;
import act.conf.AppConfig;
import org.apache.commons.codec.Charsets;
import org.osgl.exception.UnexpectedException;
import org.osgl.logging.Logger;
import org.osgl.util.Codec;
import org.osgl.util.Crypto;
import org.osgl.util.Token;

import java.security.InvalidKeyException;

public class AppCrypto {

    private static Logger logger = App.logger;
    
    private String secret;
    
    public AppCrypto(AppConfig config) {
        secret = config.secret();
    }

    public String sign(String message) {
        return Crypto.sign(message, secret.getBytes(Charsets.UTF_8));
    }

    public String passwordHash(String message) {
        return Crypto.passwordHash(message, Crypto.HashType.SHA256);
    }

    public String encrypt(String message) {
        try {
            return Crypto.encryptAES(message, secret);
        } catch (UnexpectedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvalidKeyException) {
                logger.error("Cannot encrypt/decrypt! Please download Java Crypto Extension pack from Oracle: http://www.oracle.com/technetwork/java/javase/tech/index-jsp-136007.html");
                if (Act.isDev()) {
                    logger.warn("Application will keep running with no encrypt/decrypt facilities in Dev mode");
                    return Codec.encodeBase64(message);
                }
            }
            throw e;
        }
    }

    public String decrypt(String message) {
        try {
            return Crypto.decryptAES(message, secret);
        } catch (UnexpectedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvalidKeyException) {
                logger.error("Cannot encrypt/decrypt! Please download Java Crypto Extension pack from Oracle: http://www.oracle.com/technetwork/java/javase/tech/index-jsp-136007.html");
                if (Act.isDev()) {
                    logger.warn("Application will keep running with no encrypt/decrypt facilities in Dev mode");
                    return new String(Codec.decodeBase64(message));
                }
            }
            throw e;
        }
    }

    public String generateToken(String id, String... payload) {
        return Token.generateToken(secret, id, payload);
    }

    public String generateToken(Token.Life expiration, String id, String ... payload) {
        return Token.generateToken(secret, expiration, id, payload);
    }

    public String generateToken(int seconds, String id, String ... payload) {
        return Token.generateToken(secret, seconds, id, payload);
    }

    public Token parseToken(String tokenString) {
        return Token.parseToken(secret, tokenString);
    }

}
