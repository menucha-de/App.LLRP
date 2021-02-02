package havis.llrpservice.common.xml;

import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class Xslt {
	private Transformer transformer;

	public static void main(String[] argv) throws Exception {
		if (argv.length != 2) {
			System.err.println("Usage: java Xslt <stylesheetFile> <xmlfile>");
			System.exit(1);
		}
		InputStream xsl = Files.newInputStream(Paths.get(argv[0]));
		InputStream xml = Files.newInputStream(Paths.get(argv[1]));
		Xslt xslt = new Xslt(xsl);
		String result = xslt.transform(xml);
		System.out.println(result);
	}

	public Xslt(InputStream xsl) throws TransformerConfigurationException {
		transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(xsl));
	}

	public String transform(InputStream xml)
			throws ParserConfigurationException, SAXException, IOException, TransformerException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		// parse XML
		Document xmlDocument = factory.newDocumentBuilder().parse(xml);
		// execute XSL transformation
		StreamResult xmlResult = new StreamResult(new StringWriter());
		transformer.transform(new DOMSource(xmlDocument), xmlResult);
		String result = xmlResult.getWriter().toString();
		// remove empty lines
		return result.replaceAll("(?m)^\\s*$[\n\r]{1,}", "");
	}
}
