package de.kiwiwings.poi.dsig;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.XMLSignatureException;

import de.kiwiwings.poi.dsig.DummyKeystore.KeyCertPair;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.dsig.SignatureConfig;
import org.apache.poi.poifs.crypt.dsig.SignatureInfo;
import org.apache.poi.poifs.crypt.dsig.facets.KeyInfoSignatureFacet;
import org.apache.poi.poifs.crypt.dsig.facets.OOXMLSignatureFacet;
import org.apache.poi.poifs.crypt.dsig.facets.XAdESSignatureFacet;
import org.apache.poi.poifs.crypt.dsig.facets.XAdESXLSignatureFacet;
import org.apache.poi.poifs.crypt.dsig.services.RevocationData;
import org.apache.poi.poifs.crypt.dsig.services.RevocationDataService;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LocaleUtil;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;

public class TestSigCR {
    private static final String NO_BASE64_LINEBREAKS = "org.apache.xml.security.ignoreLineBreaks";

    public static void main(String[] args) throws IOException, InvalidFormatException, GeneralSecurityException, MarshalException, XMLSignatureException, OCSPException, OperatorCreationException {
        // verify : https://www.centraldirecto.fi.cr/Sitio/FVA_ValidarDocumentoPublico/ValidarDocumentoPublico
        System.setProperty(NO_BASE64_LINEBREAKS, "true");

        UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("cert test");
            doc.write(bos);
        }

        ZipFile certStore = new ZipFile("src/main/resources/CertificadosDePruebaSHA2-PersonaFisica.zip");
        String STORE_PASS = "storePass";
        String KEY_PASS = "keyPass";
        DummyKeystore ks = new DummyKeystore(STORE_PASS);

        certStore.stream().filter(ze -> ze.getName().contains("Cadena de Certificados")).forEach(ze -> {
            try (InputStream is = certStore.getInputStream(ze)) {
                ks.importX509(is);
            } catch (CertificateException | KeyStoreException | IOException ignored) {
            }
        });

        String extCert = "Certificados/V_lidos/Extranjeros/NARCISO CASCANTE PRUEBA (FIRMA).pfx";
        String natCert = "Certificados/V_lidos/Nacionales/ANA ROJAS PRUEBA (FIRMA).pfx";
        try (InputStream is = certStore.getInputStream(certStore.getEntry(natCert))) {
            ks.importKeystore(is, "123", KEY_PASS, (a) -> "123");
        }

        KeyCertPair kcp = ks.getKeyPair(0, KEY_PASS);

        SignatureConfig cfg = new SignatureConfig();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        certStore.stream().filter(ze -> ze.getName().contains("Lista de Revocacion")).forEach(ze -> {
            try (InputStream is = certStore.getInputStream(ze)) {
                byte[] crlBytes = IOUtils.toByteArray(is);
                X509CRL x509CRL = (X509CRL)cf.generateCRL(new ByteArrayInputStream(crlBytes));
                cfg.addCRL(null, x509CRL.getIssuerX500Principal().getName(), crlBytes);
            } catch (CRLException | IOException ignored) {
            }
        });

        certStore.stream().filter(ze -> ze.getName().contains("Cadena de Certificados") || ze.getName().contains("tspcert")).forEach(ze -> {
            try (InputStream is = certStore.getInputStream(ze)) {
                cfg.addCachedCertificate(null, IOUtils.toByteArray(is));
            } catch (IOException | KeyStoreException | CertificateException ignored) {
            }
        });

        Calendar cal = LocaleUtil.getLocaleCalendar(LocaleUtil.TIMEZONE_UTC);
        final OCSPResp ocspResp = ks.createOcspResp(kcp, cal.getTimeInMillis());
        final byte[] ocspRespBytes = ocspResp.getEncoded();

        RevocationDataService rds = (certificateChain) -> {
            RevocationData rd = new RevocationData();
            rd.addOCSP(ocspRespBytes);

            certificateChain.stream().skip(1)
                .map(c -> c.getSubjectX500Principal().getName())
                .flatMap(n -> cfg.getCrlEntries().stream().filter(ce -> n.equals(ce.getCertCN())))
                .map(SignatureConfig.CRLEntry::getCrlBytes)
                .forEach(rd::addCRL);

            return rd;
        };


        cfg.setKey(kcp.getKey());
        cfg.setXadesCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE);
        cfg.setSigningCertificateChain(kcp.getX509Chain());
        cfg.setIncludeEntireCertificateChain(false);
        cfg.setSignatureFacets(Arrays.asList(
            new OOXMLSignatureFacet(),
            new KeyInfoSignatureFacet(),
            new XAdESSignatureFacet(),
            new XAdESXLSignatureFacet()
        ));
        cfg.setTspUrl("http://tsa.sinpe.fi.cr/tsahttp/");
        cfg.setTspOldProtocol(false);
        cfg.setRevocationDataService(rds);
        cfg.setAllowCRLDownload(true);
        cfg.getTspHttpClient().setFollowRedirects(true);
        cfg.getTspHttpClient().setIgnoreHttpsCertificates(true);


        try (OPCPackage pkg = OPCPackage.open(bos.toInputStream());
             FileOutputStream fos = new FileOutputStream("test.docx")) {
            SignatureInfo si = new SignatureInfo();
            si.setSignatureConfig(cfg);
            si.setOpcPackage(pkg);
            si.confirmSignature();
            pkg.save(fos);
        }

        try (ZipFile zf = new ZipFile(new File("test.docx"));
             InputStream is = zf.getInputStream(zf.getEntry("_xmlsignatures/sig1.xml"));
             OutputStream os = new FileOutputStream("tmp/sign/poi-out-sig1.xml")) {
            IOUtils.copy(is, os);
        }
    }
}
