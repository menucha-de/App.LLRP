package havis.llrpservice.common.xml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.InputSource;

public class Xpath {
	private XPathExpression expr;

	public static void main(String[] argv) throws Exception {
		if (argv.length != 2) {
			System.err.println("Usage: java Xpath <expression> <xmlFile>");
			System.exit(1);
		}
		String expr = argv[0];
		InputStream xml = Files.newInputStream(Paths.get(argv[1]));
		Xpath xpath = new Xpath(expr);
		String result = xpath.evaluate(xml);
		System.out.println(result);
	}

	public Xpath(String expr) throws XPathExpressionException {
		this.expr = XPathFactory.newInstance().newXPath().compile(expr);
	}

	public String evaluate(InputStream xml) throws XPathExpressionException {
		return expr.evaluate(new InputSource(xml));
	}
}
