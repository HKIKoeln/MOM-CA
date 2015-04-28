package org.exist.xquery.modules.fuego;

import fc.xml.diff.Diff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FuegoDiff extends BasicFunction {

    @SuppressWarnings("unused")
	private final static Logger logger = Logger.getLogger(FuegoDiff.class);
	
	public final static FunctionSignature signature =
			new FunctionSignature(
					new QName("diff", FuegoModule.NAMESPACE_URI, FuegoModule.PREFIX),
					"returns a string describing the XML difference.",
					new SequenceType[] { 
						new FunctionParameterSequenceType("input1", Type.ELEMENT, Cardinality.EXACTLY_ONE, "base version"),
						new FunctionParameterSequenceType("input2", Type.ELEMENT, Cardinality.EXACTLY_ONE, "modified version")
						},
					new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "a string describing the XML difference."));

	public FuegoDiff(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence) {
		ValueSequence result = new ValueSequence();
		ByteArrayOutputStream diffResult = new ByteArrayOutputStream();
		
		// is argument the empty sequence?
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		String ver0_string = "<test><a/><b/><c/></test>";
		String ver1_string = "<test><b/><c/><d/></test>";
		
		InputStream ver0 = null;
		InputStream ver1 = null;
		ver0 = new ByteArrayInputStream(ver0_string.getBytes());
		ver1 = new ByteArrayInputStream(ver1_string.getBytes());

		try {
			Diff.diff(ver0, ver1, diffResult);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		result.add(new StringValue(diffResult.toString()));
		return result;
	}
}
