<?xml version="1.0" standalone="yes"?>
<!--This XSLT was automatically generated from a Schematron schema.-->
<axsl:stylesheet xmlns:date="http://exslt.org/dates-and-times" xmlns:dyn="http://exslt.org/dynamic" xmlns:exsl="http://exslt.org/common" xmlns:math="http://exslt.org/math" xmlns:random="http://exslt.org/random" xmlns:regexp="http://exslt.org/regular-expressions" xmlns:set="http://exslt.org/sets" xmlns:str="http://exslt.org/strings" xmlns:axsl="http://www.w3.org/1999/XSL/Transform" xmlns:sch="http://www.ascc.net/xml/schematron" xmlns:iso="http://purl.oclc.org/dsdl/schematron" xmlns:if="urn:ietf:params:xml:ns:yang:ietf-interfaces" xmlns:ip="urn:ietf:params:xml:ns:yang:ietf-ip" xmlns:ianaift="urn:ietf:params:xml:ns:yang:iana-if-type" xmlns:nc="urn:ietf:params:xml:ns:netconf:base:1.0" extension-element-prefixes="date dyn exsl math random regexp set str" version="1.0"><!--Implementers: please note that overriding process-prolog or process-root is 
    the preferred method for meta-stylesheets to use where possible. -->
<axsl:param name="archiveDirParameter"/><axsl:param name="archiveNameParameter"/><axsl:param name="fileNameParameter"/><axsl:param name="fileDirParameter"/>

<!--PHASES-->


<!--PROLOG-->
<axsl:output xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" xmlns:svrl="http://purl.oclc.org/dsdl/svrl" method="xml" omit-xml-declaration="no" standalone="yes" indent="yes"/>

<!--KEYS-->


<!--DEFAULT RULES-->


<!--MODE: SCHEMATRON-SELECT-FULL-PATH-->
<!--This mode can be used to generate an ugly though full XPath for locators-->
<axsl:template match="*" mode="schematron-select-full-path"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:template>

<!--MODE: SCHEMATRON-FULL-PATH-->
<!--This mode can be used to generate an ugly though full XPath for locators-->
<axsl:template match="*" mode="schematron-get-full-path"><axsl:apply-templates select="parent::*" mode="schematron-get-full-path"/><axsl:text>/</axsl:text><axsl:choose><axsl:when test="namespace-uri()=''"><axsl:value-of select="name()"/><axsl:variable name="p_1" select="1+    count(preceding-sibling::*[name()=name(current())])"/><axsl:if test="$p_1&gt;1 or following-sibling::*[name()=name(current())]">[<axsl:value-of select="$p_1"/>]</axsl:if></axsl:when><axsl:otherwise><axsl:text>*[local-name()='</axsl:text><axsl:value-of select="local-name()"/><axsl:text>' and namespace-uri()='</axsl:text><axsl:value-of select="namespace-uri()"/><axsl:text>']</axsl:text><axsl:variable name="p_2" select="1+   count(preceding-sibling::*[local-name()=local-name(current())])"/><axsl:if test="$p_2&gt;1 or following-sibling::*[local-name()=local-name(current())]">[<axsl:value-of select="$p_2"/>]</axsl:if></axsl:otherwise></axsl:choose></axsl:template><axsl:template match="@*" mode="schematron-get-full-path"><axsl:text>/</axsl:text><axsl:choose><axsl:when test="namespace-uri()=''">@<axsl:value-of select="name()"/></axsl:when><axsl:otherwise><axsl:text>@*[local-name()='</axsl:text><axsl:value-of select="local-name()"/><axsl:text>' and namespace-uri()='</axsl:text><axsl:value-of select="namespace-uri()"/><axsl:text>']</axsl:text></axsl:otherwise></axsl:choose></axsl:template>

<!--MODE: SCHEMATRON-FULL-PATH-2-->
<!--This mode can be used to generate prefixed XPath for humans-->
<axsl:template match="node() | @*" mode="schematron-get-full-path-2"><axsl:for-each select="ancestor-or-self::*"><axsl:text>/</axsl:text><axsl:value-of select="name(.)"/><axsl:if test="preceding-sibling::*[name(.)=name(current())]"><axsl:text>[</axsl:text><axsl:value-of select="count(preceding-sibling::*[name(.)=name(current())])+1"/><axsl:text>]</axsl:text></axsl:if></axsl:for-each><axsl:if test="not(self::*)"><axsl:text/>/@<axsl:value-of select="name(.)"/></axsl:if></axsl:template>

<!--MODE: GENERATE-ID-FROM-PATH -->
<axsl:template match="/" mode="generate-id-from-path"/><axsl:template match="text()" mode="generate-id-from-path"><axsl:apply-templates select="parent::*" mode="generate-id-from-path"/><axsl:value-of select="concat('.text-', 1+count(preceding-sibling::text()), '-')"/></axsl:template><axsl:template match="comment()" mode="generate-id-from-path"><axsl:apply-templates select="parent::*" mode="generate-id-from-path"/><axsl:value-of select="concat('.comment-', 1+count(preceding-sibling::comment()), '-')"/></axsl:template><axsl:template match="processing-instruction()" mode="generate-id-from-path"><axsl:apply-templates select="parent::*" mode="generate-id-from-path"/><axsl:value-of select="concat('.processing-instruction-', 1+count(preceding-sibling::processing-instruction()), '-')"/></axsl:template><axsl:template match="@*" mode="generate-id-from-path"><axsl:apply-templates select="parent::*" mode="generate-id-from-path"/><axsl:value-of select="concat('.@', name())"/></axsl:template><axsl:template match="*" mode="generate-id-from-path" priority="-0.5"><axsl:apply-templates select="parent::*" mode="generate-id-from-path"/><axsl:text>.</axsl:text><axsl:value-of select="concat('.',name(),'-',1+count(preceding-sibling::*[name()=name(current())]),'-')"/></axsl:template><!--MODE: SCHEMATRON-FULL-PATH-3-->
<!--This mode can be used to generate prefixed XPath for humans 
	(Top-level element has index)-->
<axsl:template match="node() | @*" mode="schematron-get-full-path-3"><axsl:for-each select="ancestor-or-self::*"><axsl:text>/</axsl:text><axsl:value-of select="name(.)"/><axsl:if test="parent::*"><axsl:text>[</axsl:text><axsl:value-of select="count(preceding-sibling::*[name(.)=name(current())])+1"/><axsl:text>]</axsl:text></axsl:if></axsl:for-each><axsl:if test="not(self::*)"><axsl:text/>/@<axsl:value-of select="name(.)"/></axsl:if></axsl:template>

<!--MODE: GENERATE-ID-2 -->
<axsl:template match="/" mode="generate-id-2">U</axsl:template><axsl:template match="*" mode="generate-id-2" priority="2"><axsl:text>U</axsl:text><axsl:number level="multiple" count="*"/></axsl:template><axsl:template match="node()" mode="generate-id-2"><axsl:text>U.</axsl:text><axsl:number level="multiple" count="*"/><axsl:text>n</axsl:text><axsl:number count="node()"/></axsl:template><axsl:template match="@*" mode="generate-id-2"><axsl:text>U.</axsl:text><axsl:number level="multiple" count="*"/><axsl:text>_</axsl:text><axsl:value-of select="string-length(local-name(.))"/><axsl:text>_</axsl:text><axsl:value-of select="translate(name(),':','.')"/></axsl:template><!--Strip characters--><axsl:template match="text()" priority="-1"/>

<!--SCHEMA METADATA-->
<axsl:template match="/"><svrl:schematron-output xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" title="" schemaVersion=""><axsl:comment><axsl:value-of select="$archiveDirParameter"/>   
		 <axsl:value-of select="$archiveNameParameter"/>  
		 <axsl:value-of select="$fileNameParameter"/>  
		 <axsl:value-of select="$fileDirParameter"/></axsl:comment><svrl:ns-prefix-in-attribute-values uri="http://exslt.org/dynamic" prefix="dyn"/><svrl:ns-prefix-in-attribute-values uri="urn:ietf:params:xml:ns:yang:ietf-interfaces" prefix="if"/><svrl:ns-prefix-in-attribute-values uri="urn:ietf:params:xml:ns:yang:ietf-ip" prefix="ip"/><svrl:ns-prefix-in-attribute-values uri="urn:ietf:params:xml:ns:yang:iana-if-type" prefix="ianaift"/><svrl:ns-prefix-in-attribute-values uri="urn:ietf:params:xml:ns:netconf:base:1.0" prefix="nc"/><svrl:active-pattern><axsl:attribute name="id">ietf-interfaces</axsl:attribute><axsl:attribute name="name">ietf-interfaces</axsl:attribute><axsl:apply-templates/></svrl:active-pattern><axsl:apply-templates select="/" mode="M6"/><svrl:active-pattern><axsl:attribute name="id">ietf-ip</axsl:attribute><axsl:attribute name="name">ietf-ip</axsl:attribute><axsl:apply-templates/></svrl:active-pattern><axsl:apply-templates select="/" mode="M7"/><svrl:active-pattern><axsl:attribute name="id">iana-if-type</axsl:attribute><axsl:attribute name="name">iana-if-type</axsl:attribute><axsl:apply-templates/></svrl:active-pattern><axsl:apply-templates select="/" mode="M8"/></svrl:schematron-output></axsl:template>

<!--SCHEMATRON PATTERNS-->
<axsl:param name="root" select="/nc:data"/>

<!--PATTERN ietf-interfaces-->


	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces/if:interface" priority="1011" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces/if:interface"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::if:interface[if:name=current()/if:name]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::if:interface[if:name=current()/if:name]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "if:name"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces/if:interface/ip:ipv4/ip:address" priority="1010" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces/if:interface/ip:ipv4/ip:address"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if>

		<!--ASSERT -->
<axsl:choose><axsl:when test="ip:prefix-length[not(processing-instruction('dsrl'))] or false()"/><axsl:otherwise><svrl:failed-assert xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="ip:prefix-length[not(processing-instruction('dsrl'))] or false()"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Node(s) from one case of mandatory choice "subnet" must exist</svrl:text></svrl:failed-assert></axsl:otherwise></axsl:choose><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces/if:interface/ip:ipv4/ip:neighbor" priority="1009" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces/if:interface/ip:ipv4/ip:neighbor"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces/if:interface/ip:ipv6/ip:address" priority="1008" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces/if:interface/ip:ipv6/ip:address"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces/if:interface/ip:ipv6/ip:neighbor" priority="1007" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces/if:interface/ip:ipv6/ip:neighbor"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface" priority="1006" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::if:interface[if:name=current()/if:name]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::if:interface[if:name=current()/if:name]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "if:name"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface/if:higher-layer-if" priority="1005" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface/if:higher-layer-if"/>

		<!--REPORT -->
<axsl:if test=". = preceding-sibling::if:higher-layer-if"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test=". = preceding-sibling::if:higher-layer-if"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate leaf-list entry "<axsl:text/><axsl:value-of select="."/><axsl:text/>".</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface/if:lower-layer-if" priority="1004" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface/if:lower-layer-if"/>

		<!--REPORT -->
<axsl:if test=". = preceding-sibling::if:lower-layer-if"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test=". = preceding-sibling::if:lower-layer-if"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate leaf-list entry "<axsl:text/><axsl:value-of select="."/><axsl:text/>".</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface/ip:ipv4/ip:address" priority="1003" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface/ip:ipv4/ip:address"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface/ip:ipv4/ip:neighbor" priority="1002" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface/ip:ipv4/ip:neighbor"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface/ip:ipv6/ip:address" priority="1001" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface/ip:ipv6/ip:address"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:address[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

	<!--RULE -->
<axsl:template match="/nc:data/if:interfaces-state/if:interface/ip:ipv6/ip:neighbor" priority="1000" mode="M6"><svrl:fired-rule xmlns:svrl="http://purl.oclc.org/dsdl/svrl" context="/nc:data/if:interfaces-state/if:interface/ip:ipv6/ip:neighbor"/>

		<!--REPORT -->
<axsl:if test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><svrl:successful-report xmlns:svrl="http://purl.oclc.org/dsdl/svrl" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:schold="http://www.ascc.net/xml/schematron" test="preceding-sibling::ip:neighbor[ip:ip=current()/ip:ip]"><axsl:attribute name="location"><axsl:apply-templates select="." mode="schematron-get-full-path"/></axsl:attribute><svrl:text>Duplicate key "ip:ip"</svrl:text></svrl:successful-report></axsl:if><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template><axsl:template match="text()" priority="-1" mode="M6"/><axsl:template match="@*|node()" priority="-2" mode="M6"><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M6"/></axsl:template>

<!--PATTERN ietf-ip-->
<axsl:template match="text()" priority="-1" mode="M7"/><axsl:template match="@*|node()" priority="-2" mode="M7"><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M7"/></axsl:template>

<!--PATTERN iana-if-type-->
<axsl:template match="text()" priority="-1" mode="M8"/><axsl:template match="@*|node()" priority="-2" mode="M8"><axsl:apply-templates select="*|comment()|processing-instruction()" mode="M8"/></axsl:template></axsl:stylesheet>
