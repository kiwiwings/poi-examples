package de.kiwiwings.poi.dsig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Calendar;
import java.util.Date;

import org.apache.poi.poifs.crypt.CryptoFunctions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public class DummyKeystore {
    private static final SecureRandom RANDOM = new SecureRandom();


    private final KeyStore keystore;

    public DummyKeystore(String storePass) throws GeneralSecurityException, IOException {
        this(null, storePass);
    }

    public DummyKeystore(File storeFile, String storePass) throws GeneralSecurityException, IOException {
        CryptoFunctions.registerBouncyCastle();
        keystore = KeyStore.getInstance("PKCS12");
        try (InputStream fis = storeFile != null ? new FileInputStream(storeFile) : null) {
            keystore.load(fis, storePass.toCharArray());
        }
    }

    /**
     *
     * @param alias
     * @param password
     * @param keySize multiple of 1024, e.g. 1024, 2048
     * @throws GeneralSecurityException
     */
    public void addEntry(String alias, String password, int keySize, int expiryInMonths) throws GeneralSecurityException, IOException, OperatorCreationException {
        if (keystore.isKeyEntry(alias)) return;

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4), RANDOM);
        KeyPair pair = keyPairGenerator.generateKeyPair();

        Date notBefore = new Date();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, expiryInMonths);
        Date notAfter = cal.getTime();
        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature);

        X509Certificate x509 = generateCertificate(pair.getPublic(), notBefore, notAfter, pair.getPrivate(), keyUsage);

        keystore.setKeyEntry(alias, pair.getPrivate(), password.toCharArray(), new Certificate[]{x509});
    }

    public PrivateKey getKey(String alias, String keyPass) throws GeneralSecurityException {
        return (PrivateKey)keystore.getKey(alias, keyPass.toCharArray());
    }

    public X509Certificate getFirstX509(String alias) throws KeyStoreException {
        return (X509Certificate)keystore.getCertificate(alias);
    }

    public void save(File storeFile, String storePass) throws IOException, GeneralSecurityException {
        try (FileOutputStream fos = new FileOutputStream(storeFile)) {
            keystore.store(fos, storePass.toCharArray());
        }
    }

    private static X509Certificate generateCertificate(PublicKey subjectPublicKey,
        Date notBefore, Date notAfter,
        PrivateKey issuerPrivateKey,
        KeyUsage keyUsage)
        throws IOException, OperatorCreationException, CertificateException {
        final String signatureAlgorithm = "SHA1withRSA";
        final String subjectDn = "CN=Test";
        X500Name issuerName = new X500Name(subjectDn);

        RSAPublicKey rsaPubKey = (RSAPublicKey)subjectPublicKey;
        RSAKeyParameters rsaSpec = new RSAKeyParameters(false, rsaPubKey.getModulus(), rsaPubKey.getPublicExponent());

        SubjectPublicKeyInfo subjectPublicKeyInfo =
            SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(rsaSpec);

        DigestCalculator digestCalc = new JcaDigestCalculatorProviderBuilder()
            .setProvider("BC").build().get(CertificateID.HASH_SHA1);

        X509v3CertificateBuilder certificateGenerator = new X509v3CertificateBuilder(
            issuerName
            , new BigInteger(128, new SecureRandom())
            , notBefore
            , notAfter
            , new X500Name(subjectDn)
            , subjectPublicKeyInfo
        );

        X509ExtensionUtils exUtils = new X509ExtensionUtils(digestCalc);
        SubjectKeyIdentifier subKeyId = exUtils.createSubjectKeyIdentifier(subjectPublicKeyInfo);
        AuthorityKeyIdentifier autKeyId = exUtils.createAuthorityKeyIdentifier(subjectPublicKeyInfo);

        certificateGenerator.addExtension(Extension.subjectKeyIdentifier, false, subKeyId);
        certificateGenerator.addExtension(Extension.authorityKeyIdentifier, false, autKeyId);

        BasicConstraints bc = new BasicConstraints(0);
        certificateGenerator.addExtension(Extension.basicConstraints, false, bc);

        if (null != keyUsage) {
            certificateGenerator.addExtension(Extension.keyUsage, true, keyUsage);
        }

        JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(signatureAlgorithm);
        signerBuilder.setProvider("BC");

        X509CertificateHolder certHolder =
            certificateGenerator.build(signerBuilder.build(issuerPrivateKey));

        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

}
