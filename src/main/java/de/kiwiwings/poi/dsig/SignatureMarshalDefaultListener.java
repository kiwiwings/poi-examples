package de.kiwiwings.poi.dsig;

import static org.apache.poi.poifs.crypt.dsig.facets.SignatureFacet.XML_DIGSIG_NS;
import static org.apache.poi.poifs.crypt.dsig.facets.SignatureFacet.XML_NS;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.poi.poifs.crypt.dsig.SignatureInfo;
import org.apache.poi.poifs.crypt.dsig.SignatureMarshalListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;

/**
 * This listener class is used, to modify the to be digested xml document,
 * e.g. to register id attributes or set prefixes for registered namespaces
 */
public class SignatureMarshalDefaultListener implements SignatureMarshalListener {
    private static final String OBJECT_TAG = "Object";
    private final Set<String> IGNORE_NS = new HashSet<>(Arrays.asList(null, XML_NS, XML_DIGSIG_NS));

    @Override
    public void handleElement(SignatureInfo signatureInfo, Document doc, EventTarget target, EventListener parentListener) {
        // see POI #63712 : because of Santuario change r1853805 in XmlSec 2.1.3,
        // we have to deal with the whole document now

        final DocumentTraversal traversal = (DocumentTraversal) doc;
        final Map<String, String> prefixCfg = signatureInfo.getSignatureConfig().getNamespacePrefixes();

        final Map<String, String> prefixUsed = new HashMap<>();

        NodeList nl = doc.getElementsByTagName(OBJECT_TAG);
        final int objLen = nl.getLength();
        for (int i=0; i<objLen; i++) {
            final Element objNode = (Element)nl.item(i);
            NodeList nl2 = objNode.getChildNodes();
            int nl2Len = nl2.getLength();
            for (int j=0; j<nl2Len; j++) {
                Element elNode = (Element)nl2.item(j);
                getAllNamespaces(traversal, elNode, prefixCfg, prefixUsed);
                prefixUsed.forEach((ns, prefix) -> elNode.setAttributeNS(XML_NS, "xmlns:"+prefix, ns));

            }
        }
    }

    private void getAllNamespaces(DocumentTraversal traversal, Element objNode, Map<String, String> prefixCfg, Map<String, String> prefixUsed) {
        prefixUsed.clear();
        final NodeIterator iter = traversal.createNodeIterator(objNode, NodeFilter.SHOW_ELEMENT, null, false);
        try {
            for (Element node; (node = (Element)iter.nextNode()) != null; ) {
                setPrefix(node, prefixCfg, prefixUsed);
                NamedNodeMap nnm = node.getAttributes();
                final int nnmLen = nnm.getLength();
                for (int j=0; j<nnmLen; j++) {
                    setPrefix(nnm.item(j), prefixCfg, prefixUsed);
                }
            }
        } finally {
            iter.detach();
        }
    }

    private void setPrefix(Node node, Map<String,String> prefixCfg, Map<String,String> prefixUsed) {
        String ns = node.getNamespaceURI();
        String prefix = prefixCfg.get(ns);
        if (!IGNORE_NS.contains(prefix)) {
            node.setPrefix(prefix);
            prefixUsed.put(ns, prefix);
        }
    }
}
