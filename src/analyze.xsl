<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:output method="html"/>
	<xsl:template match="/">

		<table border="1">
			<tr>
				<th colspan="7">
					<xsl:value-of select="//analyze/@name" /> (<xsl:value-of select="//analyze/@duration" /> ms)
					<xsl:for-each select="analyze/property">
						<br/> <i><xsl:value-of select="@name" />=<xsl:value-of select="@value" /></i>
					</xsl:for-each>				
				</th>
			</tr>
					    
			<tr>
				<th rowspan="2">Call name</th>
				<th colspan="6">Statistics</th>
			</tr>
			<tr>
				<th>Count</th>
				<th>Errors</th>
				<th>Failures</th>
				<th>Minimum (ms)</th>
				<th>Maximum (ms)</th>
				<th>Average (ms)</th>
			</tr>
			<xsl:for-each select="analyze/call">
				<tr>
					<td>
						<xsl:value-of select="@name" />
					</td>
					<td>
						<xsl:value-of select="@count" />
					</td>
					<td>
						<xsl:value-of select="@errors" />
					</td>
					<td>
						<xsl:value-of select="@failures" />
					</td>
					<td>
						<xsl:value-of select="@min" />
					</td>
					<td>
						<xsl:value-of select="@max" />
					</td>
					<td>
						<xsl:value-of select="@avg" />
					</td>
				</tr>
			</xsl:for-each>
		</table>

	</xsl:template>
</xsl:stylesheet>