package de.kiwiwings.poi.dsig;

import java.security.GeneralSecurityException;
import java.util.List;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;

import org.apache.poi.poifs.crypt.dsig.SignatureConfig;
import org.apache.poi.poifs.crypt.dsig.SignatureInfo;

final class SignatureFacetHelper {
    private SignatureFacetHelper() {}

    static Transform newTransform(SignatureInfo signatureInfo, String canonicalizationMethod) throws XMLSignatureException {
        return newTransform(signatureInfo, canonicalizationMethod, null);
    }

    static Transform newTransform(SignatureInfo signatureInfo, String canonicalizationMethod, TransformParameterSpec paramSpec)
        throws XMLSignatureException {
        try {
            return signatureInfo.getSignatureFactory().newTransform(canonicalizationMethod, paramSpec);
        } catch (GeneralSecurityException e) {
            throw new XMLSignatureException("unknown canonicalization method: "+canonicalizationMethod, e);
        }
    }

    static Reference newReference(
        SignatureInfo signatureInfo
        , String uri
        , List<Transform> transforms
        , String type)
        throws XMLSignatureException {
        // the references appear in the package signature or the package object
        // so we can use the default digest algorithm
        SignatureConfig signatureConfig = signatureInfo.getSignatureConfig();
        String digestMethodUri = signatureConfig.getDigestMethodUri();
        XMLSignatureFactory sigFac = signatureInfo.getSignatureFactory();
        DigestMethod digestMethod;
        try {
            digestMethod = sigFac.newDigestMethod(digestMethodUri, null);
        } catch (GeneralSecurityException e) {
            throw new XMLSignatureException("unknown digest method uri: "+digestMethodUri, e);
        }

        return sigFac.newReference(uri, digestMethod, transforms, type, null);
    }
}
