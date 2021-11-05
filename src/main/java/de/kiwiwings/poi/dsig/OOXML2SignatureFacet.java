package de.kiwiwings.poi.dsig;

import static de.kiwiwings.poi.dsig.SignatureFacetHelper.newReference;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureProperties;
import javax.xml.crypto.dsig.SignatureProperty;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;

import com.microsoft.schemas.office.x2006.digsig.CTSignatureInfoV1;
import com.microsoft.schemas.office.x2006.digsig.SignatureInfoV1Document;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.crypt.dsig.SignatureConfig;
import org.apache.poi.poifs.crypt.dsig.SignatureInfo;
import org.apache.poi.poifs.crypt.dsig.facets.OOXMLSignatureFacet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OOXML2SignatureFacet extends OOXMLSignatureFacet {

    protected void addSignatureInfo(
        SignatureInfo signatureInfo
        , Document document
        , List<Reference> references
        , List<XMLObject> objects)
        throws XMLSignatureException {
        SignatureConfig signatureConfig = signatureInfo.getSignatureConfig();
        XMLSignatureFactory sigFac = signatureInfo.getSignatureFactory();

        List<XMLStructure> objectContent = new ArrayList<>();

        SignatureInfoV1Document sigV1 = SignatureInfoV1Document.Factory.newInstance();
        CTSignatureInfoV1 ctSigV1 = sigV1.addNewSignatureInfoV1();
        if (signatureConfig.getDigestAlgo() != HashAlgorithm.sha1) {
            ctSigV1.setManifestHashAlgorithm(signatureConfig.getDigestMethodUri());
        }

        String desc = signatureConfig.getSignatureDescription();
        if (desc != null) {
            ctSigV1.setSignatureComments(desc);
        }

        byte[] image = signatureConfig.getSignatureImage();
        if (image == null) {
            ctSigV1.setSignatureType(1);
        } else {
            ctSigV1.setSetupID(signatureConfig.getSignatureImageSetupId().toString());
            ctSigV1.setSignatureImage(image);
            ctSigV1.setSignatureType(2);
        }


        Element n = (Element)document.importNode(ctSigV1.getDomNode(), true);
        n.setAttributeNS(XML_NS, XMLConstants.XMLNS_ATTRIBUTE, MS_DIGSIG_NS);

        List<XMLStructure> signatureInfoContent = new ArrayList<>();
        signatureInfoContent.add(new DOMStructure(n));
        SignatureProperty signatureInfoSignatureProperty = sigFac
            .newSignatureProperty(signatureInfoContent, "#" + signatureConfig.getPackageSignatureId(),
                "idOfficeV1Details");

        List<SignatureProperty> signaturePropertyContent = new ArrayList<>();
        signaturePropertyContent.add(signatureInfoSignatureProperty);
        SignatureProperties signatureProperties = sigFac
            .newSignatureProperties(signaturePropertyContent, null);
        objectContent.add(signatureProperties);

        String objectId = "idOfficeObject";
        objects.add(sigFac.newXMLObject(objectContent, objectId, null, null));

        Reference reference = newReference(signatureInfo, "#" + objectId, null, XML_DIGSIG_NS+"Object");
        references.add(reference);

        Base64.Encoder enc = Base64.getEncoder();
        byte[] imageValid = signatureConfig.getSignatureImageValid();
        if (imageValid != null) {
            objectId = "idValidSigLnImg";
            DOMStructure tn = new DOMStructure(document.createTextNode(enc.encodeToString(imageValid)));
            objects.add(sigFac.newXMLObject(Collections.singletonList(tn), objectId, null, null));

            reference = newReference(signatureInfo, "#" + objectId, null, XML_DIGSIG_NS+"Object");
            references.add(reference);
        }

        byte[] imageInvalid = signatureConfig.getSignatureImageInvalid();
        if (imageInvalid != null) {
            objectId = "idInvalidSigLnImg";
            DOMStructure tn = new DOMStructure(document.createTextNode(enc.encodeToString(imageInvalid)));
            objects.add(sigFac.newXMLObject(Collections.singletonList(tn), objectId, null, null));

            reference = newReference(signatureInfo, "#" + objectId, null, XML_DIGSIG_NS+"Object");
            references.add(reference);
        }
    }
}
