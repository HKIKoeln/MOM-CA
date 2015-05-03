package org.exist.xquery.modules.fuego;

import fc.xml.diff.Diff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.ValidationReport;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.validation.Shared;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class FuegoDiff extends BasicFunction {

    @SuppressWarnings("unused")
    private final static Logger logger = Logger.getLogger(FuegoDiff.class);
    
    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("diff", FuegoModule.NAMESPACE_URI, FuegoModule.PREFIX),
                    "returns an XML document describing the XML difference.",
                    new SequenceType[] { 
                        new FunctionParameterSequenceType("input1", Type.ELEMENT, Cardinality.EXACTLY_ONE, "base version"),
                        new FunctionParameterSequenceType("input2", Type.ELEMENT, Cardinality.EXACTLY_ONE, "modified version"),
                        new FunctionParameterSequenceType("encoderAlias", Type.STRING, Cardinality.EXACTLY_ONE, "encoder alias"),
                        new FunctionParameterSequenceType("encoderOptions", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "encoder options"),
                        new FunctionParameterSequenceType("emitEmpty", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "whether to emit empty")
                        },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "an XML document describing the XML difference."));

    public FuegoDiff(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        ValueSequence result = new ValueSequence();
        ByteArrayOutputStream diffResult = new ByteArrayOutputStream();
        
        // is argument the empty sequence?
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        String ver0_string = serialize(args[0].iterate());
        String ver1_string = serialize(args[1].iterate());
        
        InputStream ver0 = new ByteArrayInputStream(ver0_string.getBytes());
        InputStream ver1 = new ByteArrayInputStream(ver1_string.getBytes());

        try {
            Diff.diff(ver0, ver1, diffResult);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        result.add(parse(diffResult.toString()).itemAt(0));
        return result;
    }

    private Sequence parse(String xmlContent) throws XPathException {

        if (xmlContent.length() == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }
        StringReader reader = new StringReader(xmlContent);
        ValidationReport report = new ValidationReport();
        SAXAdapter adapter = new SAXAdapter(context);
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(reader);

            XMLReader xr = null;
            if (isCalledAs("parse-html")) {
                try {
                    Class<?> clazz = Class.forName( "org.cyberneko.html.parsers.SAXParser" );
                    xr = (XMLReader) clazz.newInstance();
                    //do not modify the case of elements and attributes
                    xr.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
                    xr.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
                } catch (Exception e) {
                    logger.warn("Could not instantiate neko HTML parser for function util:parse-html, falling back to " +
                            "default XML parser.", e);
                }
            }
            if (xr == null) {
                SAXParser parser = factory.newSAXParser();
                xr = parser.getXMLReader();
            }

            xr.setErrorHandler(report);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);
        } catch (ParserConfigurationException e) {
            throw new XPathException(ErrorCodes.EXXQDY0002, "Error while constructing XML parser: " + e.getMessage());
        } catch (SAXException e) {
            logger.debug("Error while parsing XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(ErrorCodes.EXXQDY0002, "Error while parsing XML: " + e.getMessage());
        }
        
        if (report.isValid())
            return (DocumentImpl) adapter.getDocument();
        else {
            MemTreeBuilder builder = context.getDocumentBuilder();
            NodeImpl result = Shared.writeReport(report, builder);
            throw new XPathException(this, ErrorCodes.EXXQDY0002, report.toString(), result);
        }
    }

    private String serialize(SequenceIterator siNode) throws XPathException
    {
        OutputStream os = new ByteArrayOutputStream();
        Properties outputProperties = new Properties();
        
        // serialize the node set
        SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        try
        {
            String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
            Writer writer = new OutputStreamWriter(os, encoding);
            sax.setOutput(writer, outputProperties);
            Serializer serializer = context.getBroker().getSerializer();
            serializer.reset();
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);

            sax.startDocument();
            
            while(siNode.hasNext())
            {
               NodeValue next = (NodeValue)siNode.nextItem();
               serializer.toSAX(next);    
            }
            
            sax.endDocument();
            writer.close();
        }
        catch(SAXException e)
        {
            throw new XPathException(this, "A problem occurred while serializing the node set: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new XPathException(this, "A problem occurred while serializing the node set: " + e.getMessage(), e);
        }
        finally
        {
            SerializerPool.getInstance().returnObject(sax);
        }
        try
        {
            String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
            return new String(((ByteArrayOutputStream)os).toByteArray(), encoding);
        }
        catch(UnsupportedEncodingException e)
        {
            throw new XPathException(this, "A problem occurred while serializing the node set: " + e.getMessage(), e);
        }
    }
    
}
