<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="urn:havis:llrp:server:configuration:xsd:1"
	xmlns:c="urn:havis:llrp:server:configuration:xsd:1">
	<xsl:output method="xml" encoding="utf-8" indent="yes" />

	<xsl:template match="//c:systemController/c:host">

		<c:host>havis.util.platform</c:host>

	</xsl:template>

	<xsl:template match="//c:systemController/c:reflection">

		<c:OSGi />

	</xsl:template>

	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>