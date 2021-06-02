package de.kiwiwings.poi.xwpf;

import static org.apache.poi.ooxml.POIXMLTypeLoader.DEFAULT_XML_OPTIONS;
import static org.apache.poi.openxml4j.opc.PackageRelationshipTypes.IMAGE_PART;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.namespace.QName;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.util.Dimension2DDouble;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.draw.SVGImageRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOfficeArtExtension;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOfficeArtExtensionList;

/**
 * Example for adding a SVG image + converted PNG image as a fallback
 * Based on https://stackoverflow.com/questions/67672120/unable-to-insert-emf-into-word-using-python
 */
public class AddSvgToDocument {
    public static void main(String[] args) throws IOException, InvalidFormatException {
        File tmplDocx = new File(args[0]);
        File svgFile = new File(args[1]);
        File outDocx = new File(args[2]);

        try (FileInputStream fis = new FileInputStream(tmplDocx);
             XWPFDocument doc = new XWPFDocument(fis)) {

            SVGImageRenderer rnd = new SVGImageRenderer();
            try (FileInputStream fis2 = new FileInputStream(svgFile)) {
                rnd.loadImage(fis2, PictureData.PictureType.SVG.contentType);
            }

            Rectangle2D nativeDim = rnd.getNativeBounds();
            double widthPx = 500;
            double heightPx = widthPx * nativeDim.getHeight() / nativeDim.getWidth();

            BufferedImage bi = rnd.getImage(new Dimension2DDouble(widthPx, heightPx));
            ByteArrayOutputStream bos = new ByteArrayOutputStream(100_000);
            ImageIO.write(bi, "PNG", bos);

            XWPFRun run = doc.createParagraph().createRun();

            int widthEmu = Units.pixelToEMU((int)widthPx);
            int heightEmu = Units.pixelToEMU((int)heightPx);
            XWPFPicture pic = run.addPicture(new ByteArrayInputStream(bos.toByteArray()), PictureData.PictureType.PNG.ooxmlId, "image.png", widthEmu, heightEmu);
            CTOfficeArtExtensionList extLst = pic.getCTPicture().getBlipFill().getBlip().addNewExtLst();
            addExt(extLst, "{28A0092B-C50C-407E-A947-70E740481C1C}"
                , "http://schemas.microsoft.com/office/drawing/2010/main", "a14:useLocalDpi"
                , "val", "0");

            addExt(extLst, "{96DAC541-7B7A-43D3-8B79-37D633B846F1}"
                , "http://schemas.microsoft.com/office/drawing/2016/SVG/main", "asvg:svgBlip"
                , "r:embed", addSVG(doc, svgFile));

            try (FileOutputStream fos = new FileOutputStream(outDocx)) {
                doc.write(fos);
            }
        }
    }



    private static void addExt(CTOfficeArtExtensionList extLst, String uri, String namespace, String name, String attribute, String value) {
        CTOfficeArtExtension ext = extLst.addNewExt();
        ext.setUri(uri);
        XmlCursor cur = ext.newCursor();
        cur.toEndToken();
        String[] prefixName = name.split(":");
        cur.beginElement(new QName(namespace, prefixName[1], prefixName[0]));
        cur.insertNamespace(prefixName[0], namespace);
        if (attribute.contains(":")) {
            prefixName = attribute.split(":");
            String prefix = prefixName[0];
            String attrNamespace = DEFAULT_XML_OPTIONS
                .getSaveSuggestedPrefixes().entrySet().stream()
                .filter(me -> prefix.equals(me.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
            cur.insertAttributeWithValue(new QName(attrNamespace, prefixName[1], prefix), value);
        } else {
            cur.insertAttributeWithValue(attribute, value);
        }
        cur.dispose();
    }

    private static String addSVG(XWPFDocument doc, File svgFile) throws InvalidFormatException, IOException {
        // SVG is not thoroughly supported as of POI 5.0.0, hence we need to go the long way instead of adding a picture
        OPCPackage pkg = doc.getPackage();
        String svgNameTmpl = "/word/media/image#.svg";
        int svgImageIdx = pkg.getUnusedPartIndex(svgNameTmpl);
        PackagePartName svgPPName = PackagingURIHelper.createPartName(svgNameTmpl.replace("#", Integer.toString(svgImageIdx)));
        PackagePart svgPart = pkg.createPart(svgPPName, PictureData.PictureType.SVG.contentType);

        try (FileInputStream fis = new FileInputStream(svgFile);
             OutputStream os = svgPart.getOutputStream()) {
            IOUtils.copy(fis, os);
        }
        PackageRelationship svgRel = doc.getPackagePart().addRelationship(svgPPName, TargetMode.INTERNAL, IMAGE_PART);
        return svgRel.getId();
    }
}
