/*--

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom2.xpath.jaxen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jaxen.BaseXPath;
import org.jaxen.JaxenException;
import org.jaxen.NamespaceContext;
import org.jaxen.UnresolvableException;
import org.jaxen.VariableContext;
import org.jaxen.XPath;

import org.jdom2.Namespace;
import org.jdom2.filter.Filter;
import org.jdom2.xpath.util.AbstractXPathCompiled;

/**
 * Jaxen specific code for XPath management.
 * 
 * @author Rolf Lear
 * @param <T>
 *        The generic type of returned data.
 */
class JaxenCompiled<T> extends AbstractXPathCompiled<T> implements
		NamespaceContext, VariableContext {

	/**
	 * Same story, need to be able to strip NamespaceContainer instances from
	 * Namespace content.
	 * 
	 * @param o
	 *        A result object which could potentially be a NamespaceContainer
	 * @return The input parameter unless it is a NamespaceContainer in which
	 *         case return the wrapped Namespace
	 */
	private static final Object unWrapNS(Object o) {
		if (o instanceof NamespaceContainer) {
			return ((NamespaceContainer) o).getNamespace();
		}
		return o;
	}

	/**
	 * Same story, need to be able to replace NamespaceContainer instances with
	 * Namespace content.
	 * 
	 * @param results
	 *        A list potentially containing NamespaceContainer instances
	 * @return The parameter list with NamespaceContainer instances replaced by
	 *         the wrapped Namespace instances.
	 */
	private static final List<Object> unWrap(List<?> results) {
		final ArrayList<Object> ret = new ArrayList<Object>(results.size());
		for (Iterator<?> it = results.iterator(); it.hasNext();) {
			ret.add(unWrapNS(it.next()));
		}
		return ret;
	}

	/**
	 * The compiled XPath object to select nodes. This attribute can not be made
	 * final as it needs to be set upon object deserialization.
	 */
	private final XPath xPath;

	/**
	 * The current context for XPath expression evaluation. The navigator is
	 * responsible for exposing JDOM content to Jaxen, including the wrapping of
	 * Namespace instances in NamespaceContainer
	 * <p>
	 * Because of the need to wrap Namespace, we also need to unwrap namespace.
	 * Further, we can't re-use the details from one 'selectNodes' to another
	 * because the Document tree may have been modfied between, and also, we do
	 * not want to be holding on to memory.
	 * <p>
	 * Finally, we want to pre-load the NamespaceContext with the namespaces
	 * that are in scope for the contextNode being searched.
	 * <p>
	 * So, we need to reset the Navigator before and after each use. try{}
	 * finally {} to the rescue.
	 */
	private final JDOM2Navigator navigator = new JDOM2Navigator();

	/**
	 * @param expression The XPath expression
	 * @param filter The coercion filter
	 * @param variables The XPath variable context
	 * @param namespaces The XPath namespace context
	 */
	public JaxenCompiled(String expression, Filter<T> filter,
			Map<String, Object> variables, Namespace[] namespaces) {
		super(expression, filter, variables, namespaces);
		try {
			xPath = new BaseXPath(expression, navigator);
		} catch (JaxenException e) {
			throw new IllegalArgumentException("Unable to compile '" + expression
					+ "'. See Cause.", e);
		}
		xPath.setNamespaceContext(this);
		xPath.setVariableContext(this);
	}

	@Override
	public String translateNamespacePrefixToUri(String prefix) {
		return getNamespace(prefix);
	}
	
	@Override
	public Object getVariableValue(String namespaceURI, String prefix,
			String localName) throws UnresolvableException {
		if (namespaceURI == null) {
			namespaceURI = "";
		}
		if (prefix == null) {
			prefix = "";
		}
		if ("".equals(namespaceURI)) {
			namespaceURI = getNamespace(prefix);
		}
		try {
			return getVariable(namespaceURI, localName);
		} catch (IllegalArgumentException e) {
			throw new UnresolvableException("Unable to resolve variable " + localName + " in namespace '" + namespaceURI + "' to a vaulue.");
		}
	}

	@Override
	protected List<?> evaluateRawAll(Object context) {
		try {
			return unWrap(xPath.selectNodes(context));
		} catch (JaxenException e) {
			throw new IllegalStateException(
					"Unable to evaluate expression. See cause", e);
		}
	}

	@Override
	protected Object evaluateRawFirst(Object context) {
		try {
			return unWrapNS(xPath.selectSingleNode(context));
		} catch (JaxenException e) {
			throw new IllegalStateException(
					"Unable to evaluate expression. See cause", e);
		}
	}

}
