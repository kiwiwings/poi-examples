package de.kiwiwings.poi.dsig;

import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignatureException;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.crypt.dsig.SignatureConfig;
import org.apache.poi.poifs.crypt.dsig.SignatureInfo;
import org.apache.poi.poifs.crypt.dsig.facets.KeyInfoSignatureFacet;
import org.apache.poi.poifs.crypt.dsig.facets.Office2010SignatureFacet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bouncycastle.operator.OperatorCreationException;

public class AddSigV1Details {
    public static void main(String[] args) throws IOException, InvalidFormatException, GeneralSecurityException, OperatorCreationException, MarshalException, XMLSignatureException {
        UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet().createRow(0).createCell(0).setCellValue("foobaa");
            wb.write(bos);
        }

        final String storePass = "storePass";
        final String keyAlias = "keyAlias";
        final String keyPass = "keyPass";

        // loading the keystore - pkcs12 is used here, but of course jks & co are also valid
        // the keystore needs to contain a private key and it's certificate having a
        // 'digitalSignature' key usage
        DummyKeystore dk = new DummyKeystore(storePass);
        dk.addEntry(keyAlias, keyPass, 4096, 24);
        dk.save(new File("sigV1.pfx"), storePass);

        // extracting private key and certificate
        PrivateKey key = dk.getKey(keyAlias, keyPass);
        X509Certificate x509 = dk.getFirstX509(keyAlias);

        SignatureConfig signatureConfig = new SignatureConfig();
        signatureConfig.setKey(key);
        signatureConfig.setSigningCertificateChain(singletonList(x509));
        signatureConfig.setDigestAlgo(HashAlgorithm.sha1);
        signatureConfig.setSignatureDescription("Purpose of signing this document");
        signatureConfig.setSignatureFacets(Arrays.asList(
            new OOXML2SignatureFacet(),
            new KeyInfoSignatureFacet(),
            new XAdES2SignatureFacet(),
            new Office2010SignatureFacet()
        ));

        try (OPCPackage opc = OPCPackage.open(bos.toInputStream())) {

            SignatureInfo si = new SignatureInfo();
            si.setOpcPackage(opc);
            si.setSignatureConfig(signatureConfig);

            si.confirmSignature();

            opc.save(new File("sigV1.xlsx"));
        }
    }
}
