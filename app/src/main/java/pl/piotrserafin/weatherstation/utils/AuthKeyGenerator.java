package pl.piotrserafin.weatherstation.utils;

import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import timber.log.Timber;

public class AuthKeyGenerator {

    private static final String DEFAULT_KEYSTORE = "AndroidKeyStore";
    private static final String DEFAULT_ALIAS = "Cloud IoT Authentication";

    public static final List<String> SUPPORTED_KEY_ALGORITHMS = Collections.unmodifiableList(
            Arrays.asList(KeyProperties.KEY_ALGORITHM_RSA, KeyProperties.KEY_ALGORITHM_EC));
    public static final String DEFAULT_KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_RSA;

    private static final int KEY_SIZE_RSA = 2048;
    private static final int KEY_SIZE_EC = 256;

    private final String keystoreName;
    private final String keyAlias;
    private final String keyAlgorithm;

    private Certificate certificate;
    private PrivateKey privateKey;

    public AuthKeyGenerator(String keyAlgorithm) throws GeneralSecurityException, IOException {
        this(DEFAULT_KEYSTORE, DEFAULT_ALIAS, keyAlgorithm);
    }


    public AuthKeyGenerator(String keystoreName, String keyAlias, String keyAlgorithm)
            throws GeneralSecurityException, IOException {
        if (keyAlgorithm == null) {
            keyAlgorithm = DEFAULT_KEY_ALGORITHM;
        }
        if (!SUPPORTED_KEY_ALGORITHMS.contains(keyAlgorithm)) {
            throw new IllegalArgumentException("Invalid key algorithm " + keyAlgorithm +
                    ". Supported are " + SUPPORTED_KEY_ALGORITHMS);
        }
        this.keystoreName = keystoreName;
        this.keyAlias = keyAlias;
        this.keyAlgorithm = keyAlgorithm;
        initialize();
    }

    public void initialize() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(keystoreName);
        ks.load(null);

        certificate = ks.getCertificate(keyAlias);
        if (certificate == null) {
            // generate key
            Timber.w("No auth certificate found for Cloud IoT Core. Generating new certificate and key pair");
            generateAuthenticationKey();
            certificate = ks.getCertificate(keyAlias);
        }

        Timber.i("Loaded certificate: %s", keyAlias);

        if (certificate instanceof X509Certificate) {
            X509Certificate x509Certificate = (X509Certificate) certificate;
            Timber.d("Subject: %s", x509Certificate.getSubjectX500Principal().toString());
            Timber.d("Issuer: %s", x509Certificate.getIssuerX500Principal().toString());
            Timber.d("Signature: %s", Base64.encodeToString(x509Certificate.getSignature(),
                    Base64.DEFAULT));
        }

        Key key = ks.getKey(keyAlias, null);
        privateKey = (PrivateKey) key;

        if (isInSecureHardware()) {
            Timber.i("Private key is in secure hardware");
        } else {
            Timber.w("Private key is NOT in secure hardware");
        }

        exportPublicKey();
    }

    private boolean isInSecureHardware() {
        try {
            KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), keystoreName);
            KeyInfo keyInfo = factory.getKeySpec(privateKey, KeyInfo.class);
            return keyInfo.isInsideSecureHardware();
        } catch (GeneralSecurityException e) {
            Timber.w("Could not determine if private key is in secure hardware or not");
        }
        return false;
    }

    public KeyPair getKeyPair() {
        return new KeyPair(certificate.getPublicKey(), privateKey);
    }

    /**
     * Generate a new key pair entry in the Android Keystore by using the KeyPairGenerator API.
     * This creates both a KeyPair and a self-signed certificate, both with the same alias,
     * using the {@link #keyAlgorithm} provided.
     */
    private void generateAuthenticationKey() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlgorithm, keystoreName);
        KeyGenParameterSpec.Builder specBuilder =
                new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                        .setCertificateSubject(new X500Principal("CN=unused"))
                        .setDigests(KeyProperties.DIGEST_SHA256);

        if (keyAlgorithm.equals(KeyProperties.KEY_ALGORITHM_RSA)) {
            specBuilder.setKeySize(KEY_SIZE_RSA)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);
        } else if (keyAlgorithm.equals(KeyProperties.KEY_ALGORITHM_EC)) {
            specBuilder.setKeySize(KEY_SIZE_EC);
        }
        kpg.initialize(specBuilder.build());

        kpg.generateKeyPair();
    }

    private void exportPublicKey() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            try {
                File outFile = new File(Environment.getExternalStorageDirectory(),
                        "cloud_iot_auth_certificate.pem");
                try (FileOutputStream os = new FileOutputStream(outFile)) {
                    writeCertificatePEM(os);
                }
            } catch (GeneralSecurityException | IOException e) {
                if (e instanceof FileNotFoundException &&
                        e.getMessage().contains("Permission denied")) {
                    Timber.e(e,"Unable to export certificate. Grant WRITE permission or install with 'adb install -g'");
                } else {
                    Timber.e(e, "Unable to export certificate");
                }
            }
        }
    }

    private void writeCertificatePEM(OutputStream os) throws GeneralSecurityException, IOException {
        os.write("-----BEGIN CERTIFICATE-----\n".getBytes());
        os.write(Base64.encode(certificate.getEncoded(), Base64.DEFAULT));
        os.write("-----END CERTIFICATE-----\n".getBytes());
    }
}
