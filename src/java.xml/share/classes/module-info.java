/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * Defines the Java API for XML Processing (JAXP), the Streaming API for XML (StAX),
 * the Simple API for XML (SAX), and the W3C Document Object Model (DOM) API.
 *
 * <h2 id="ConfigurationFile">JAXP Configuration File</h2>
 * JAXP supports the use of a configuration file for the
 * <a href="#LookupMechanism">Factory Lookup Mechanism</a> and
 * setting properties that have defined corresponding system properties.
 *
 * <h3>Format</h3>
 * The configuration file must be in standard {@link java.util.Properties} format.
 * <p>
 * The keys are the names of the system properties, for example, those listed in
 * column {@code System Property Name} of the table <a href="#Factories">JAXP Factories</a>,
 * or {@code System Property} in the table {@code Catalog Features}
 * of class {@link javax.xml.catalog.CatalogFeatures CatalogFeatures}.
 *
 * <p>
 * The values are those defined in the specific API or process, for example, the
 * fully qualified name of the implementation class for the
 * <a href="#LookupMechanism">Factory Lookup Mechanism</a>, or {@code value} in
 * table {@code Catalog Features} of class
 * {@link javax.xml.catalog.CatalogFeatures CatalogFeatures}.
 *
 * <p>
 * Below are examples on what can be placed in the configuration file:
 * <pre>
 *     {@code javax.xml.parsers.DocumentBuilderFactory=packagename.DocumentBuilderFactoryImpl}
 *     {@code javax.xml.catalog.resolve=strict}
 * </pre>
 *
 *
 * <h3 id="CF_Default">{@code jaxp.properties}</h3>
 * By default, the <a href="#Factories">JAXP Factories</a> will look for a
 * configuration file called {@code jaxp.properties} in the {@code conf} directory
 * of the Java installation and use the entries if any to customize the behavior
 * of the factories.
 *
 * <p>
 * {@code jaxp.properties} will be read only once during the initialization of
 * the JAXP implementation and cached in memory. If the file does not exist when
 * the first attempt is made to read from it, no further attempts are made to check
 * for its existence. It is not possible to change the value of any property after
 * it has been read for the first time.
 *
 *
 * <h3 id="CF_SP">User-defined Configuration File</h3>
 * A user-defined configuration file can be set outside of the JDK by using the
 * system property {@code java.xml.config.file}.
 *
 * <p>
 * When the system property is specified, the configuration file it points to
 * will be read and the property entries in it used to override those in
 * {@code jaxp.properties}. If the system property does not exist when a factory
 * is instantiated, no further attempt will be made to check for its existence.
 * <p>
 * The value of the property shall be a valid file path to a configuration file.
 * If the file path is not absolute, it will be considered relative to the working
 * directory.
 * <p>
 * Unlike other system properties, this property can not be placed in a configuration
 * file.
 *
 * <h2 id="LookupMechanism">JAXP Lookup Mechanism</h2>
 * JAXP defines an ordered lookup procedure to determine the implementation class
 * to load for the JAXP factories. Factories that support the mechanism are listed
 * in the table below along with the method, System Property name, and System
 * Default method to be used in the procedure.
 *
 * <table class="plain" id="Factories">
 * <caption>JAXP Factories</caption>
 * <thead>
 * <tr>
 * <th scope="col">Factory</th>
 * <th scope="col">Method</th>
 * <th scope="col">System Property Name</th>
 * <th scope="col">System Default</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DATA">
 *     {@link javax.xml.datatype.DatatypeFactory DatatypeFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.datatype.DatatypeFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.datatype.DatatypeFactory}</td>
 * <td style="text-align:center">{@link javax.xml.datatype.DatatypeFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOM">
 *     {@link javax.xml.parsers.DocumentBuilderFactory DocumentBuilderFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.parsers.DocumentBuilderFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.parsers.DocumentBuilderFactory}</td>
 * <td style="text-align:center">{@link javax.xml.parsers.DocumentBuilderFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="SAX">
 *     {@link javax.xml.parsers.SAXParserFactory SAXParserFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.parsers.SAXParserFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.parsers.SAXParserFactory}</td>
 * <td style="text-align:center">{@link javax.xml.parsers.SAXParserFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAXEvent">
 *     {@link javax.xml.stream.XMLEventFactory XMLEventFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.stream.XMLEventFactory#newFactory() newFactory()}</td>
 * <td style="text-align:center">{@code javax.xml.stream.XMLEventFactory}</td>
 * <td style="text-align:center">{@link javax.xml.stream.XMLEventFactory#newDefaultFactory() newDefaultFactory()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAXInput">
 *     {@link javax.xml.stream.XMLInputFactory XMLInputFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.stream.XMLInputFactory#newFactory() newFactory()}</td>
 * <td style="text-align:center">{@code javax.xml.stream.XMLInputFactory}</td>
 * <td style="text-align:center">{@link javax.xml.stream.XMLInputFactory#newDefaultFactory() newDefaultFactory()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAXOutput">
 *     {@link javax.xml.stream.XMLOutputFactory XMLOutputFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.stream.XMLOutputFactory#newFactory() newFactory()}</td>
 * <td style="text-align:center">{@code javax.xml.stream.XMLOutputFactory}</td>
 * <td style="text-align:center">{@link javax.xml.stream.XMLOutputFactory#newDefaultFactory() newDefaultFactory()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XSLT">
 *     {@link javax.xml.transform.TransformerFactory TransformerFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.transform.TransformerFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.transform.TransformerFactory}</td>
 * <td style="text-align:center">{@link javax.xml.transform.TransformerFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Validation">
 *     {@link javax.xml.validation.SchemaFactory SchemaFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.validation.SchemaFactory#newInstance(java.lang.String) newInstance(schemaLanguage)}</td>
 * <td style="text-align:center">{@code javax.xml.validation.SchemaFactory:}<i>schemaLanguage</i>[1]</td>
 * <td style="text-align:center">{@link javax.xml.validation.SchemaFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XPath">
 *     {@link javax.xml.xpath.XPathFactory XPathFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.xpath.XPathFactory#newInstance(java.lang.String) newInstance(uri)}</td>
 * <td style="text-align:center">{@link javax.xml.xpath.XPathFactory#DEFAULT_PROPERTY_NAME DEFAULT_PROPERTY_NAME} + ":uri"[2]</td>
 * <td style="text-align:center">{@link javax.xml.xpath.XPathFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <b>[1]</b> where <i>schemaLanguage</i> is the parameter to the
 * {@link javax.xml.validation.SchemaFactory#newInstance(java.lang.String) newInstance(schemaLanguage)}
 * method.
 * <p>
 * <b>[2]</b> where <i>uri</i> is the parameter to the
 * {@link javax.xml.xpath.XPathFactory#newInstance(java.lang.String) newInstance(uri)}
 * method.
 *
 * <h3 id="LookupProcedure">Lookup Procedure</h3>
 * The <a href="#Factories">JAXP Factories</a> follow the procedure described
 * below in order to locate and load the implementation class:
 *
 * <ul>
 * <li>
 * Use the system property as described in column System Property of the table
 * <a href="#Factories">JAXP Factories</a> above;
 * </li>
 * <li>
 * <p>
 * Use the <a href="#ConfigurationFile">Configuration File</a>;
 * </li>
 * <li>
 * <p>
 * Use the service-provider loading facility, defined by the
 * {@link java.util.ServiceLoader} class, to attempt to locate and load an
 * implementation of the service using the {@linkplain
 * java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}:
 * the service-provider loading facility will use the {@linkplain
 * java.lang.Thread#getContextClassLoader() current thread's context class loader}
 * to attempt to load the service. If the context class
 * loader is null, the {@linkplain
 * ClassLoader#getSystemClassLoader() system class loader} will be used.
 *
 * <h3>{@link javax.xml.validation.SchemaFactory SchemaFactory}</h3>
 * In case of the {@link javax.xml.validation.SchemaFactory SchemaFactory},
 * each potential service provider is required to implement the method
 * {@link javax.xml.validation.SchemaFactory#isSchemaLanguageSupported(java.lang.String)
 * isSchemaLanguageSupported(String schemaLanguage)}.
 * The first service provider found that supports the specified schema language
 * is returned.
 *
 * <h3>{@link javax.xml.xpath.XPathFactory XPathFactory}</h3>
 * In case of the {@link javax.xml.xpath.XPathFactory XPathFactory},
 * each potential service provider is required to implement the method
 * {@link javax.xml.xpath.XPathFactory#isObjectModelSupported(String objectModel)
 * isObjectModelSupported(String objectModel)}.
 * The first service provider found that supports the specified object model is
 * returned.
 * </li>
 * <li>
 * <p>
 * Otherwise, the {@code system-default} implementation is returned, which is
 * equivalent to calling the {@code newDefaultInstance() or newDefaultFactory()}
 * method as shown in column System Default of the table
 * <a href="#Factories">JAXP Factories</a> above.
 *
 * <h3>{@link javax.xml.validation.SchemaFactory SchemaFactory}</h3>
 * In case of the {@link javax.xml.validation.SchemaFactory SchemaFactory},
 * there must be a {@linkplain javax.xml.validation.SchemaFactory#newDefaultInstance()
 * platform default} {@code SchemaFactory} for W3C XML Schema.
 *
 * <h3>{@link javax.xml.xpath.XPathFactory XPathFactory}</h3>
 * In case of the {@link javax.xml.xpath.XPathFactory XPathFactory},
 * there must be a
 * {@linkplain javax.xml.xpath.XPathFactory#newDefaultInstance() platform default}
 * {@code XPathFactory} for the W3C DOM, i.e.
 * {@link javax.xml.xpath.XPathFactory#DEFAULT_OBJECT_MODEL_URI DEFAULT_OBJECT_MODEL_URI}.
 * </li>
 * </ul>
 *
 * <h2 id="PP">Property Precedence</h2>
 * Properties in this module can be set in multiple ways, including using
 * the API properties, system properties, <a href="#ConfigurationFile">JAXP Configuration File</a>,
 * the {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FEATURE_SECURE_PROCESSING}
 * (hereafter referred to FSP), and the default values. The order of precedence
 * for the configuration sources is defined as follows, with earlier ones overriding the later:
 *
 * <ul>
 * <li><p>
 *      Properties specified through factories or processors API;
 * </li>
 * <li><p>
 *      Properties set using the corresponding System properties;
 * </li>
 * <li><p>
 *      Properties set in a user-defined <a href="#ConfigurationFile">Configuration File</a>
 * pointed to by the system property {@code java.xml.config.file};
 * </li>
 * <li><p>
 *      Properties set in the default JAXP Configuration File
 * <a href="#CF_Default">{@code jaxp.properties}</a>;
 * </li>
 * <li><p>
 *      Properties' default values initialized during factory or processor creation.
 * Security-related properties will be on their restrictive values when the
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} feature is true.
 * </li>
 * </ul>
 *
 * Using the {@link javax.xml.catalog.CatalogFeatures CatalogFeatures}' RESOLVE
 * property as an example, the followings illustrate how these rules are applied.
 * <ul>
 * <li><p>
 *      Properties specified through factories or processors API have the highest
 * precedence. The following code therefore effectively set the RESOLVE property
 * to {@code strict}, regardless of settings in any other configuration sources.
 * <pre>
 *     {@code
 *     DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 *     dbf.setAttribute(CatalogFeatures.Feature.RESOLVE.getPropertyName(), "strict");
 *     }
 * </pre>
 *
 * </li>
 * <li><p>
 *      If the property is not set on the factory such as in the above code, a
 * system property setting will be in effect.
 * <pre>
 *     {@code
 *     // in the following example, the RESOLVE property is set to 'continue'
 *     // for the entire application
 *     java -Djavax.xml.catalog.resolve=continue myApp
 *
 *     // in the following code snipet, the property is set to 'ignore' for the
 *     // factory that follows
 *     System.setProperty("javax.xml.catalog.resolve", "ignore");
 *     DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 *     ...
 *     System.clearProperty("javax.xml.catalog.resolve");
 *     }
 * </pre>
 * </li>
 * <li><p>
 *      If the property is not set on the factory, nor through its system property,
 * the setting in a configuration file will take effect. The following entry
 * sets the property to '{@code continue}'.
 * <pre>
 *     {@code
 *     javax.xml.catalog.resolve=continue
 *     }
 * </pre>
 * </li>
 * <li><p>
 *     If the property is not set anywhere, it will be resolved to its
 * default value that is '{@code strict}'.
 * </li>
 * </ul>
 *
 * @implNote
 *
 * <h2 id="ConfigurationFile">JAXP Configuration File</h2>
 * The <a href="#ConfigurationFile">JAXP Configuration File</a> defined by the XML
 * processing API may be used to set the JDK implementation specific properties
 * and features. A configuration file may contain any of the properties listed in
 * the table <a href="#Properties">Implementation Specific Properties</a> and
 * <a href="#Features">Features</a>. See also
 * <a href="#PP">Property Precedence</a> for the use of the configuration
 * file in setting properties.
 *
 *
 * <h2>Implementation Specific Features and Properties</h2>
 *
 * In addition to the standard features and properties described within the public
 * APIs of this module, the JDK implementation supports a further number of
 * implementation specific features and properties. This section describes the
 * naming convention, System Properties, precedence order, and processors to which
 * a property applies. A table listing the implementation specific features and
 * properties that the implementation currently supports can be found at the end
 * of this note.
 *
 * <h3 id="NamingConvention">Naming Convention</h3>
 * The names of the features and properties are fully qualified, composed of a
 * prefix and name.
 *
 * <h4>Prefix</h4>
 * The prefix for JDK features and properties, as well as their corresponding
 * System Properties if any, is defined as:
 * <pre>
 *     {@code jdk.xml.}
 * </pre>
 *
 * <h4>Name</h4>
 * A name may consist of one or multiple words that are case-sensitive.
 * All letters of the first word are in lowercase, while the first letter of
 * each subsequent word is capitalized.
 * <p>
 * An example of a property that indicates whether an XML document is standalone
 * would thus have a format:
 * <pre>
 *     {@code jdk.xml.isStandalone}
 * </pre>
 * and a corresponding System Property:
 * <pre>
 *     {@systemProperty jdk.xml.isStandalone}
 * </pre>
 *
 * <h3>System Properties</h3>
 * A property may have a corresponding System Property with the same name.
 * A System Property should be set prior to the creation of a processor and
 * may be cleared afterwards.
 *
 * <h3>Configuration File</h3>
 * A system property can be specified in the <a href="#ConfigurationFile">JAXP Configuration File</a>
 * to set the behavior for the JAXP factories. The format is
 * {@code key=value}, where the key is the property name as listed in the column
 * {@code Full Name} and value in the column {@code Value} in the table
 * <a href="#Properties">Implementation Specific Properties</a> and
 * <a href="#Features">Features</a>. For example:
 * <pre>
 *     {@code jdk.xml.entityExpansionLimit=2000}
 *     {@code jdk.xml.isStandalone=true}
 * </pre>
 *
 * <h3 id="PropPrec">Property Precedence</h3>
 * The JDK implementation specific features and properties follow the same procedure
 * as described in section <a href="#PP">Property Precedence</a> in retrieving
 * property values.
 * Specific to the initialized values, the restrictive values that are set when
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} is true are shown
 * in {@code "Value"}'s subcolumn {@code "Enforced"} in the table
 * <a href="#Features">Implementation Specific Features</a> and
 * <a href="#Properties">Properties</a>.
 *
 * <p>
 * Furthermore, when the Java Security Manager is present, the JDK sets
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} to true and does
 * not allow it to be turned off. The security related properties are therefore
 * set to the {@code "Enforced"} values.
 *
 * <h3 id="Processor">Processor Support</h3>
 * Features and properties may be supported by one or more processors. The
 * following table lists the processors by IDs that can be used for reference.
 *
 * <table class="plain" id="Processors">
 * <caption>Processors</caption>
 * <thead>
 * <tr>
 * <th scope="col">ID</th>
 * <th scope="col">Name</th>
 * <th scope="col">How to set the property</th>
 * <th scope="col">How to set the feature</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOM">DOM</th>
 * <td style="text-align:center">DOM Parser</td>
 * <td>
 * {@code DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();}<br>
 * {@code dbf.setAttribute(name, value);}
 * </td>
 * <td>
 * {@code DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();}<br>
 * {@code dbf.setFeature(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="SAX">SAX</th>
 * <td style="text-align:center">SAX Parser</td>
 * <td>
 * {@code SAXParserFactory spf = SAXParserFactory.newInstance();}<br>
 * {@code SAXParser parser = spf.newSAXParser();}<br>
 * {@code parser.setProperty(name, value);}
 * </td>
 * <td>
 * {@code SAXParserFactory spf = SAXParserFactory.newInstance();}<br>
 * {@code spf.setFeature(name, value);}<br>
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAX">StAX</th>
 * <td style="text-align:center">StAX Parser</td>
 * <td>
 * {@code XMLInputFactory xif = XMLInputFactory.newInstance();}<br>
 * {@code xif.setProperty(name, value);}
 * </td>
 * <td>
 * {@code XMLInputFactory xif = XMLInputFactory.newInstance();}<br>
 * {@code xif.setProperty(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Validation">Validation</th>
 * <td style="text-align:center">XML Validation API</td>
 * <td>
 * {@code SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);}<br>
 * {@code schemaFactory.setProperty(name, value);}
 * </td>
 * <td>
 * {@code SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);}<br>
 * {@code schemaFactory.setFeature(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Transform">Transform</th>
 * <td style="text-align:center">XML Transform API</td>
 * <td>
 * {@code TransformerFactory factory = TransformerFactory.newInstance();}<br>
 * {@code factory.setAttribute(name, value);}
 * </td>
 * <td>
 * {@code TransformerFactory factory = TransformerFactory.newInstance();}<br>
 * {@code factory.setFeature(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XSLTCSerializer">XSLTC Serializer</th>
 * <td style="text-align:center">XSLTC Serializer</td>
 * <td>
 * {@code Transformer transformer = TransformerFactory.newInstance().newTransformer();}<br>
 * {@code transformer.setOutputProperty(name, value);}
 * </td>
 * <td>
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOMLS">DOMLS</th>
 * <td style="text-align:center">DOM Load and Save</td>
 * <td>
 * {@code LSSerializer serializer = domImplementation.createLSSerializer();} <br>
 * {@code serializer.getDomConfig().setParameter(name, value);}
 * </td>
 * <td>
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XPATH">XPath</th>
 * <td style="text-align:center">XPath</td>
 * <td>
 * {@code XPathFactory factory = XPathFactory.newInstance();}<br>
 * {@code factory.setProperty(name, value);}
 * </td>
 * <td>
 * {@code XPathFactory factory = XPathFactory.newInstance();} <br>
 * {@code factory.setFeature(name, value);}
 * </td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <h3>Implementation Specific Features and Properties</h3>
 * The Implementation Specific Features and Properties reflect JDK's choice to
 * manage the limitations on resources while complying with the API specification,
 * or allow applications to alter behaviors beyond those required by the standards.
 * <p>
 * The table below lists the Implementation Specific Properties currently supported
 * by the JDK. More properties may be added in the future if necessary.
 *
 * <table class="striped" id="Properties">
 * <caption>Implementation Specific Properties</caption>
 * <thead>
 * <tr>
 * <th scope="col" rowspan="2">Full Name (<a href="#NamingConvention">prefix + name</a>)
 * <a href="#Note1">[1]</a></th>
 * <th scope="col" rowspan="2">Description</th>
 * <th scope="col" rowspan="2">API Property <a href="#Note2">[2]</a></th>
 * <th scope="col" rowspan="2">System Property <a href="#Note3">[3]</a></th>
 * <th scope="col" rowspan="2">Configuration File <a href="#Note3">[3]</a></th>
 * <th scope="col" colspan="4" style="text-align:center">Value <a href="#Note4">[4]</a></th>
 * <th scope="col" rowspan="2">Security <a href="#Note5">[5]</a></th>
 * <th scope="col" rowspan="2">Supported Processor <a href="#Note6">[6]</a></th>
 * <th scope="col" rowspan="2">Since <a href="#Note7">[7]</a></th>
 * </tr>
 * <tr>
 * <th scope="col">Type</th>
 * <th scope="col">Value</th>
 * <th scope="col">Default</th>
 * <th scope="col">Enforced</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 *
 * <tr>
 * <td id="EELimit">{@systemProperty jdk.xml.entityExpansionLimit}</td>
 * <td>Limits the number of entity expansions.
 * </td>
 * <td style="text-align:center" rowspan="9">yes</td>
 * <td style="text-align:center" rowspan="9">yes</td>
 * <td style="text-align:center" rowspan="9">yes</td>
 * <td style="text-align:center" rowspan="9">Integer</td>
 * <td rowspan="9">
 * A positive integer. A value less than or equal to 0 indicates no limit.
 * If the value is not an integer, a NumberFormatException is thrown.
 * </td>
 * <td style="text-align:center">64000</td>
 * <td style="text-align:center">64000</td>
 * <td style="text-align:center" rowspan="9">Yes</td>
 * <td style="text-align:center" rowspan="9">
 *     <a href="#DOM">DOM</a><br>
 *     <a href="#SAX">SAX</a><br>
 *     <a href="#StAX">StAX</a><br>
 *     <a href="#Validation">Validation</a><br>
 *     <a href="#Transform">Transform</a>
 * </td>
 * <td style="text-align:center" rowspan="9">8</td>
 * </tr>
 * <tr>
 * <td id="EALimit">{@systemProperty jdk.xml.elementAttributeLimit}</td>
 * <td>Limits the number of attributes an element can have.
 * </td>
 * <td style="text-align:center">10000</td>
 * <td style="text-align:center">10000</td>
 * </tr>
 * <tr>
 * <td id="OccurLimit">{@systemProperty jdk.xml.maxOccurLimit}</td>
 * <td>Limits the number of content model nodes that may be created when building
 * a grammar for a W3C XML Schema that contains maxOccurs attributes with values
 * other than "unbounded".
 * </td>
 * <td style="text-align:center">5000</td>
 * <td style="text-align:center">5000</td>
 * </tr>
 * <tr>
 * <td id="SizeLimit">{@systemProperty jdk.xml.totalEntitySizeLimit}</td>
 * <td>Limits the total size of all entities that include general and parameter
 * entities. The size is calculated as an aggregation of all entities.
 * </td>
 * <td style="text-align:center">5x10^7</td>
 * <td style="text-align:center">5x10^7</td>
 * </tr>
 * <tr>
 * <td id="GELimit">{@systemProperty jdk.xml.maxGeneralEntitySizeLimit}</td>
 * <td>Limits the maximum size of any general entities.
 * </td>
 * <td style="text-align:center">0</td>
 * <td style="text-align:center">0</td>
 * </tr>
 * <tr>
 * <td id="PELimit">{@systemProperty jdk.xml.maxParameterEntitySizeLimit}</td>
 * <td>Limits the maximum size of any parameter entities, including the result
 * of nesting multiple parameter entities.
 * </td>
 * <td style="text-align:center">10^6</td>
 * <td style="text-align:center">10^6</td>
 * </tr>
 * <tr>
 * <td id="ERLimit">{@systemProperty jdk.xml.entityReplacementLimit}</td>
 * <td>Limits the total number of nodes in all entity references.
 * </td>
 * <td style="text-align:center">3x10^6</td>
 * <td style="text-align:center">3x10^6</td>
 * </tr>
 * <tr>
 * <td id="ElementDepth">{@systemProperty jdk.xml.maxElementDepth}</td>
 * <td>Limits the maximum element depth.
 * </td>
 * <td style="text-align:center">0</td>
 * <td style="text-align:center">0</td>
 * </tr>
 * <tr>
 * <td id="NameLimit">{@systemProperty jdk.xml.maxXMLNameLimit}</td>
 * <td>Limits the maximum size of XML names, including element name, attribute
 * name and namespace prefix and URI.
 * </td>
 * <td style="text-align:center">1000</td>
 * <td style="text-align:center">1000</td>
 * </tr>
 *
 * <tr>
 * <td id="ISSTANDALONE">{@systemProperty jdk.xml.isStandalone}</td>
 * <td>Indicates that the serializer should treat the output as a
 * standalone document. The property can be used to ensure a newline is written
 * after the XML declaration. Unlike the property
 * {@link org.w3c.dom.ls.LSSerializer#getDomConfig() xml-declaration}, this property
 * does not have an effect on whether an XML declaration should be written out.
 * </td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">boolean</td>
 * <td style="text-align:center">true/false</td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#DOMLS">DOMLS</a></td>
 * <td style="text-align:center">17</td>
 * </tr>
 * <tr>
 * <td id="XSLTCISSTANDALONE">{@systemProperty jdk.xml.xsltcIsStandalone}</td>
 * <td>Indicates that the <a href="#XSLTCSerializer">XSLTC serializer</a> should
 * treat the output as a standalone document. The property can be used to ensure
 * a newline is written after the XML declaration. Unlike the property
 * {@link javax.xml.transform.OutputKeys#OMIT_XML_DECLARATION OMIT_XML_DECLARATION},
 * this property does not have an effect on whether an XML declaration should be
 * written out.
 * <p>
 * This property behaves similar to that for <a href="#DOMLS">DOMLS</a> above,
 * except that it is for the <a href="#XSLTCSerializer">XSLTC Serializer</a>
 * and its value is a String.
 * </td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">String</td>
 * <td style="text-align:center">yes/no</td>
 * <td style="text-align:center">no</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#XSLTCSerializer">XSLTC Serializer</a></td>
 * <td style="text-align:center">17</td>
 * </tr>
 * <tr>
 * <td id="cdataChunkSize">{@systemProperty jdk.xml.cdataChunkSize}</td>
 * <td>Instructs the parser to return the data in a CData section in a single chunk
 * when the property is zero or unspecified, or in multiple chunks when it is greater
 * than zero. The parser shall split the data by linebreaks, and any chunks that are
 * larger than the specified size to ones that are equal to or smaller than the size.
 * </td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">Integer</td>
 * <td>A positive integer. A value less than
 * or equal to 0 indicates that the property is not specified. If the value is not
 * an integer, a NumberFormatException is thrown.</td>
 * <td style="text-align:center">0</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#SAX">SAX</a><br><a href="#StAX">StAX</a></td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="extensionClassLoader">jdk.xml.extensionClassLoader</td>
 * <td>Sets a non-null ClassLoader instance to be used for loading XSLTC java
 * extension functions.
 * </td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">no</td>
 * <td style="text-align:center">no</td>
 * <td style="text-align:center">Object</td>
 * <td>A reference to a ClassLoader object. Null if the value is not specified.</td>
 * <td style="text-align:center">null</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#Transform">Transform</a></td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="xpathExprGrpLimit">jdk.xml.xpathExprGrpLimit</td>
 * <td>Limits the number of groups an XPath expression can contain.
 * </td>
 * <td style="text-align:center" rowspan="2">
 *     <a href="#Transform">Transform</a>:yes<br>
 *     <a href="#XPATH">XPath</a>:no
 * </td>
 * <td style="text-align:center" rowspan="3">yes</td>
 * <td style="text-align:center" rowspan="3">yes</td>
 * <td style="text-align:center" rowspan="3">Integer</td>
 * <td rowspan="3">A positive integer. A value less than or equal to 0 indicates no limit.
 * If the value is not an integer, a NumberFormatException is thrown. </td>
 * <td style="text-align:center">10</td>
 * <td style="text-align:center">10</td>
 * <td style="text-align:center" rowspan="3">Yes</td>
 * <td style="text-align:center" rowspan="2">
 *     <a href="#Transform">Transform</a><br>
 *     <a href="#XPath">XPath</a>
 * </td>
 * <td style="text-align:center" rowspan="3">19</td>
 * </tr>
 * <tr>
 * <td id="xpathExprOpLimit">jdk.xml.xpathExprOpLimit</td>
 * <td>Limits the number of operators an XPath expression can contain.
 * </td>
 * <td style="text-align:center">100</td>
 * <td style="text-align:center">100</td>
 * </tr>
 * <tr>
 * <td id="xpathTotalOpLimit">jdk.xml.xpathTotalOpLimit</td>
 * <td>Limits the total number of XPath operators in an XSL Stylesheet.
 * </td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">10000</td>
 * <td style="text-align:center">10000</td>
 * <td style="text-align:center">
 *     <a href="#Transform">Transform</a><br>
 * </td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * The table below lists the Implementation Specific Features currently supported
 * by the JDK. More features may be added in the future if necessary.
 *
 * <table class="striped" id="Features">
 * <caption>Implementation Specific Features</caption>
 * <thead>
 * <tr>
 * <th scope="col" rowspan="2">Full Name (<a href="#NamingConvention">prefix + name</a>)
 * <a href="#Note1">[1]</a></th>
 * <th scope="col" rowspan="2">Description</th>
 * <th scope="col" rowspan="2">API Property <a href="#Note2">[2]</a></th>
 * <th scope="col" rowspan="2">System Property <a href="#Note3">[3]</a></th>
 * <th scope="col" rowspan="2">Configuration File <a href="#Note3">[3]</a></th>
 * <th scope="col" colspan="4" style="text-align:center">Value <a href="#Note4">[4]</a></th>
 * <th scope="col" rowspan="2">Security <a href="#Note5">[5]</a></th>
 * <th scope="col" rowspan="2">Supported Processor <a href="#Note6">[6]</a></th>
 * <th scope="col" rowspan="2">Since <a href="#Note7">[7]</a></th>
 * </tr>
 * <tr>
 * <th scope="col">Type</th>
 * <th scope="col">Value</th>
 * <th scope="col">Default</th>
 * <th scope="col">Enforced</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td id="ExtFunc">{@systemProperty jdk.xml.enableExtensionFunctions}</td>
 * <td>Determines if XSLT and XPath extension functions are to be allowed.
 * </td>
 * <td style="text-align:center" rowspan="3">yes</td>
 * <td style="text-align:center" rowspan="3">yes</td>
 * <td style="text-align:center" rowspan="3">yes</td>
 * <td style="text-align:center" rowspan="3">Boolean</td>
 * <td>
 * true or false. True indicates that extension functions are allowed; False otherwise.
 * </td>
 * <td style="text-align:center">true</td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">Yes</td>
 * <td style="text-align:center">
 *     <a href="#Transform">Transform</a><br>
 *     <a href="#XPAth">XPath</a>
 * </td>
 * <td style="text-align:center">8</td>
 * </tr>
 * <tr>
 * <td id="ORParser">{@systemProperty jdk.xml.overrideDefaultParser}</td>
 * <td>Enables the use of a 3rd party's parser implementation to override the
 * system-default parser for the JDK's Transform, Validation and XPath implementations.
 * </td>
 * <td>
 * true or false. True enables the use of 3rd party's parser implementations
 * to override the system-default implementation during XML Transform, Validation
 * or XPath operation. False disables the use of 3rd party's parser
 * implementations.
 * </td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">Yes</td>
 * <td style="text-align:center">
 *     <a href="#Transform">Transform</a><br>
 *     <a href="#Validation">Validation</a><br>
 *     <a href="#XPAth">XPath</a>
 * </td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="symbolTable">{@systemProperty jdk.xml.resetSymbolTable}</td>
 * <td>Instructs the parser to reset its internal symbol table during each parse operation.
 * </td>
 * <td>
 * true or false. True indicates that the SymbolTable associated with a parser needs to be
 * reallocated during each parse operation.<br>
 * False indicates that the parser's SymbolTable instance shall be reused
 * during subsequent parse operations.
 * </td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center">
 *     <a href="#SAX">SAX</a>
 * </td>
 * <td style="text-align:center">9</td>
 * </tr>
 * </tbody>
 * </table>
 * <p id="Note1">
 * <b>[1]</b> The full name of a property should be used to set the property.
 * <p id="Note2">
 * <b>[2]</b> A value "yes" indicates that the property can be set through the
 * processor or its factory, "no" otherwise.
 * <p id="Note3">
 * <b>[3]</b> A value "yes" indicates there is a corresponding System Property
 * for the property, "no" otherwise.
 *
 * <p id="Note4">
 * <b>[4]</b> The value must be exactly as listed in this table, case-sensitive.
 * The value of the corresponding System Property is the String representation of
 * the property value. If the type is boolean, the system property is true only
 * if it is "true"; If the type is String, the system property is true only if
 * it is exactly the same string representing the positive value (e.g. "yes" for
 * {@code xsltcIsStandalone}); The system property is false otherwise. If the type
 * is Integer, the value of the System Property is the String representation of
 * the value (e.g. "64000" for {@code entityExpansionLimit}).
 *
 * <p id="Note5">
 * <b>[5]</b> A value "yes" indicates the property is a Security Property. As indicated
 * in the <a href="#PropPrec">Property Precedence</a>, the values listed in the column
 * {@code enforced} will be used to initialize these properties when
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} is true.
 *
 * <p id="Note6">
 * <b>[6]</b> One or more processors that support the property. The values of the
 * field are IDs described in the table <a href="#Processor">Processors</a>.
 * <p id="Note7">
 * <b>[7]</b> Indicates the initial release the property is introduced.
 *
 * <h3>Legacy Property Names (deprecated)</h3>
 * JDK releases prior to JDK 17 support the use of URI style prefix for properties.
 * These legacy property names are <b>deprecated</b> as of JDK 17 and may be removed
 * in future releases. If both new and legacy properties are set, the new property
 * names take precedence regardless of how and where they are set. The overriding order
 * as defined in <a href="#PropPrec">Property Precedence</a> thus becomes:
 *
 * <ul>
 * <li>Value set on factories or processors using new property names.</li>
 * <li>Value set on factories or processors using <b>legacy property names</b>;</li>
 * <li>Value set as System Property;</li>
 * <li>Value set in the configuration file;</li>
 * <li>Value set by FEATURE_SECURE_PROCESSING;</li>
 * <li>The default value;</li>
 * </ul>
 * <p>
 * The following table lists the properties and their corresponding legacy names.
 *
 * <table class="striped" id="LegacyProperties">
 * <caption>Legacy Property Names (deprecated since 17)</caption>
 * <thead>
 * <tr>
 * <th>Property</th>
 * <th>Legacy Property Name(s)</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>{@systemProperty jdk.xml.entityExpansionLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.elementAttributeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxOccurLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxOccurLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.totalEntitySizeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/totalEntitySizeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxGeneralEntitySizeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxGeneralEntitySizeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxParameterEntitySizeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxParameterEntitySizeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.entityReplacementLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/entityReplacementLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxElementDepth}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxElementDepth}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxXMLNameLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxXMLNameLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.isStandalone}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/isStandalone}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.xsltcIsStandalone}</td>
 * <td>{@code http://www.oracle.com/xml/is-standalone}<br>
 * {@code http://www.oracle.com/xml/jaxp/properties/xsltcIsStandalone}</td>
 * </tr>
 * <tr>
 * <td>{@code jdk.xml.extensionClassLoader}</td>
 * <td>{@code jdk.xml.transform.extensionClassLoader}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.enableExtensionFunctions}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions}</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @uses javax.xml.datatype.DatatypeFactory
 * @uses javax.xml.parsers.DocumentBuilderFactory
 * @uses javax.xml.parsers.SAXParserFactory
 * @uses javax.xml.stream.XMLEventFactory
 * @uses javax.xml.stream.XMLInputFactory
 * @uses javax.xml.stream.XMLOutputFactory
 * @uses javax.xml.transform.TransformerFactory
 * @uses javax.xml.validation.SchemaFactory
 * @uses javax.xml.xpath.XPathFactory
 * @uses org.xml.sax.XMLReader
 *
 * @moduleGraph
 * @since 9
 */
module java.xml {
    exports javax.xml;
    exports javax.xml.catalog;
    exports javax.xml.datatype;
    exports javax.xml.namespace;
    exports javax.xml.parsers;
    exports javax.xml.stream;
    exports javax.xml.stream.events;
    exports javax.xml.stream.util;
    exports javax.xml.transform;
    exports javax.xml.transform.dom;
    exports javax.xml.transform.sax;
    exports javax.xml.transform.stax;
    exports javax.xml.transform.stream;
    exports javax.xml.validation;
    exports javax.xml.xpath;
    exports org.w3c.dom;
    exports org.w3c.dom.bootstrap;
    exports org.w3c.dom.events;
    exports org.w3c.dom.ls;
    exports org.w3c.dom.ranges;
    exports org.w3c.dom.traversal;
    exports org.w3c.dom.views;
    exports org.xml.sax;
    exports org.xml.sax.ext;
    exports org.xml.sax.helpers;

    exports com.sun.org.apache.xml.internal.dtm to
        java.xml.crypto;
    exports com.sun.org.apache.xml.internal.utils to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.compiler to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.functions to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.objects to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.res to
        java.xml.crypto;

    uses javax.xml.datatype.DatatypeFactory;
    uses javax.xml.parsers.DocumentBuilderFactory;
    uses javax.xml.parsers.SAXParserFactory;
    uses javax.xml.stream.XMLEventFactory;
    uses javax.xml.stream.XMLInputFactory;
    uses javax.xml.stream.XMLOutputFactory;
    uses javax.xml.transform.TransformerFactory;
    uses javax.xml.validation.SchemaFactory;
    uses javax.xml.xpath.XPathFactory;
    uses org.xml.sax.XMLReader;
}
