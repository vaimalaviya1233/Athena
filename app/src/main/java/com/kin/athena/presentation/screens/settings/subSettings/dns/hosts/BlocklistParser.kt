/* Copyright (C) 2025 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kin.athena.presentation.screens.settings.subSettings.dns.hosts

import com.kin.athena.core.logging.Logger

/**
 * Represents a parsed blocklist rule with its type and pattern
 */
sealed class BlocklistRule {
    data class PlainDomain(val domain: String) : BlocklistRule()
    data class WildcardDomain(val pattern: String, val regex: Regex) : BlocklistRule()
    data class RegexPattern(val pattern: String, val regex: Regex) : BlocklistRule()
    data class WhitelistDomain(val domain: String) : BlocklistRule()
}

/**
 * Parser for multiple blocklist formats:
 * - Hosts file format (127.0.0.1 example.com, 0.0.0.0 example.com)
 * - ABP format (||example.com^, @@||example.com^)
 * - DNSmasq format (address=/example.com/)
 * - Wildcard domains (*.example.com)
 * - Regex patterns (/pattern/)
 */
object BlocklistParser {
    private const val IPV4_LOOPBACK = "127.0.0.1"
    private const val IPV6_LOOPBACK = "::1"
    private const val NO_ROUTE = "0.0.0.0"

    /**
     * Parse a single line from a blocklist file
     * Returns a BlocklistRule if the line is valid, null otherwise
     */
    fun parseLine(line: String): BlocklistRule? {
        if (line.isEmpty()) {
            return null
        }

        // Fast path: check first char to skip common cases
        val firstChar = line[0]
        if (firstChar == '#' || firstChar == '!' || firstChar == ' ' || firstChar == '\t') {
            return null
        }

        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) {
            return null
        }

        // Fast path: detect format by first character for efficiency
        return when (firstChar) {
            '|' -> parseABPFormat(trimmedLine)
            '@' -> parseABPFormat(trimmedLine)
            'a' -> parseDNSmasqFormat(trimmedLine) ?: parseHostsFormat(trimmedLine)
            '/' -> parseRegexFormat(trimmedLine)
            '0', '1', '2', ':', '*' -> parseHostsFormat(trimmedLine)
            else -> {
                // Check for regex chars for pi-hole format
                if (trimmedLine.contains(Regex("[\\^\\$\\[\\]()\\\\+?|]"))) {
                    parsePiholeRegexFormat(trimmedLine)
                } else if (trimmedLine.contains('*')) {
                    parseWildcardFormat(trimmedLine)
                } else {
                    parseHostsFormat(trimmedLine)
                }
            }
        }
    }

    /**
     * Parse ABP (Adblock Plus) format
     * Examples:
     * - ||example.com^ (block domain)
     * - @@||example.com^ (whitelist domain)
     * - ||ads.example.com^ (block subdomain)
     */
    private fun parseABPFormat(line: String): BlocklistRule? {
        // Whitelist rules start with @@
        if (line.startsWith("@@||") && line.contains("^")) {
            val domain = line.substring(4, line.indexOf("^"))
                .lowercase()
                .trim()

            if (domain.isNotEmpty() && isValidDomain(domain)) {
                return BlocklistRule.WhitelistDomain(domain)
            }
        }

        // Block rules start with ||
        if (line.startsWith("||") && line.contains("^")) {
            val domain = line.substring(2, line.indexOf("^"))
                .lowercase()
                .trim()

            if (domain.isNotEmpty() && isValidDomain(domain)) {
                // Check if it contains wildcards
                if (domain.contains("*")) {
                    val regex = wildcardToRegex(domain)
                    return BlocklistRule.WildcardDomain(domain, regex)
                }
                return BlocklistRule.PlainDomain(domain)
            }
        }

        return null
    }

    /**
     * Parse DNSmasq format
     * Examples:
     * - address=/example.com/
     * - address=/ads.example.com/0.0.0.0
     * - address=/ads.example.com/127.0.0.1
     * - address=/ads.example.com/::
     */
    private fun parseDNSmasqFormat(line: String): BlocklistRule? {
        if (!line.startsWith("address=/")) {
            return null
        }

        // Skip "address=/" (9 characters) and split the rest
        val parts = line.substring(9).split("/", limit = 2)
        if (parts.isEmpty()) {
            return null
        }

        val domain = parts[0].lowercase().trim()

        if (domain.isEmpty()) {
            return null
        }

        val isValid = isValidDomainOrPattern(domain)
        if (!isValid) {
            return null
        }

        // Check if it contains wildcards
        if (domain.contains('*')) {
            val regex = wildcardToRegex(domain)
            return BlocklistRule.WildcardDomain(domain, regex)
        }
        return BlocklistRule.PlainDomain(domain)
    }

    /**
     * Parse regex format
     * Examples:
     * - /ad[s]?\..*\.com/
     * - /^ads?\./
     */
    private fun parseRegexFormat(line: String): BlocklistRule? {
        if (!line.startsWith("/") || !line.endsWith("/")) {
            return null
        }

        val pattern = line.substring(1, line.length - 1)

        if (pattern.isEmpty()) {
            return null
        }

        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            BlocklistRule.RegexPattern(pattern, regex)
        } catch (e: Exception) {
            Logger.error("Invalid regex pattern: $pattern", e)
            null
        }
    }

    /**
     * Parse Pi-hole style regex format (raw regex without delimiters)
     * Examples:
     * - ^ad[s]?[0-9]*\.
     * - (^|\.)ad[s]?\d*\.
     * This must come after other formats to avoid false positives
     */
    private fun parsePiholeRegexFormat(line: String): BlocklistRule? {
        // Fast check: if already validated to have regex chars, skip re-check
        val commentIndex = line.indexOf('#')
        val cleanLine = if (commentIndex != -1) {
            line.substring(0, commentIndex).trim()
        } else {
            line.trim()
        }

        if (cleanLine.isEmpty()) {
            return null
        }

        // Try to compile as regex
        return try {
            val regex = Regex(cleanLine, RegexOption.IGNORE_CASE)
            BlocklistRule.RegexPattern(cleanLine, regex)
        } catch (e: Exception) {
            // Not a valid regex pattern, let other parsers try
            null
        }
    }

    /**
     * Parse wildcard format
     * Examples:
     * - *.doubleclick.net
     * - ads.*.com
     * - *.ads.*
     */
    private fun parseWildcardFormat(line: String): BlocklistRule? {
        val commentIndex = line.indexOf('#')
        val cleanLine = if (commentIndex != -1) {
            line.substring(0, commentIndex).trim().lowercase()
        } else {
            line.trim().lowercase()
        }

        if (cleanLine.isEmpty() || !cleanLine.contains('*')) {
            return null
        }

        if (!isValidDomainWithWildcard(cleanLine)) {
            return null
        }

        val regex = wildcardToRegex(cleanLine)
        return BlocklistRule.WildcardDomain(cleanLine, regex)
    }

    /**
     * Parse traditional hosts file format
     * Examples:
     * - 127.0.0.1 example.com
     * - 0.0.0.0 ads.example.com
     * - ::1 tracker.example.com
     * - example.com (plain domain)
     */
    private fun parseHostsFormat(line: String): BlocklistRule? {
        val commentIndex = line.indexOf('#')
        var cleanLine = if (commentIndex != -1) {
            line.substring(0, commentIndex).trim().lowercase()
        } else {
            line.trim().lowercase()
        }

        if (cleanLine.isEmpty()) {
            return null
        }

        var startOfHost = 0

        // Fast path: check first chars for IP addresses
        if (cleanLine.startsWith("127.0.0.1")) {
            startOfHost = 9 // length of "127.0.0.1"
        } else if (cleanLine.startsWith("0.0.0.0")) {
            startOfHost = 7 // length of "0.0.0.0"
        } else if (cleanLine.startsWith("::1")) {
            startOfHost = 3 // length of "::1"
        }

        if (startOfHost >= cleanLine.length) {
            return null
        }

        // Extract the domain (take first token after IP or entire line)
        val domain = if (startOfHost > 0) {
            cleanLine.substring(startOfHost).trim().split(Regex("\\s+"), 2)[0]
        } else {
            cleanLine.split(Regex("\\s+"), 2)[0]
        }

        if (domain.isEmpty()) {
            return null
        }

        // Validate as a domain - be lenient to accept plain domain lists
        if (!isValidDomainOrPattern(domain)) {
            return null
        }

        return BlocklistRule.PlainDomain(domain)
    }

    /**
     * Convert wildcard pattern to regex
     * Example: *.example.com -> ^.*\.example\.com$
     */
    private fun wildcardToRegex(pattern: String): Regex {
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")

        return Regex("^$regexPattern$", RegexOption.IGNORE_CASE)
    }

    /**
     * Check if a domain is valid (allows wildcards)
     */
    private fun isValidDomainWithWildcard(domain: String): Boolean {
        if (domain.isEmpty()) return false

        // Remove wildcards for validation
        val withoutWildcards = domain.replace("*", "a")
        return isValidDomain(withoutWildcards)
    }

    /**
     * Check if a domain is valid (strict validation for specific formats)
     */
    private fun isValidDomain(domain: String): Boolean {
        if (domain.isEmpty() || domain.length > 253) {
            return false
        }

        // Check for invalid characters
        if (domain.contains(Regex("\\s"))) {
            return false
        }

        // Must contain at least one dot or be localhost
        if (!domain.contains(".") && domain != "localhost") {
            return false
        }

        // Check each label - be more lenient, allow consecutive hyphens
        val labels = domain.split(".")
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) {
                return false
            }

            // Labels can only contain alphanumeric, hyphens, and underscores
            if (!label.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                return false
            }

            // Cannot start or end with hyphen (but allow consecutive hyphens in middle)
            if (label.startsWith("-") || label.endsWith("-")) {
                return false
            }
        }

        return true
    }

    /**
     * Check if a domain or pattern is valid (more lenient for plain domain lists)
     * This allows domains without strict validation for blocklists that may have
     * unusual but valid domain patterns
     */
    private fun isValidDomainOrPattern(domain: String): Boolean {
        val len = domain.length
        if (len == 0 || len > 253) {
            return false
        }

        // Fast check for dots
        val hasDot = domain.indexOf('.') != -1
        if (!hasDot && domain != "localhost") {
            return false
        }

        // Cannot start or end with dot
        val firstChar = domain[0]
        val lastChar = domain[len - 1]
        if (firstChar == '.' || lastChar == '.') {
            return false
        }

        // Fast validation: check for invalid chars
        for (i in 0 until len) {
            val c = domain[i]
            val isValid = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') ||
                          c == '.' || c == '-' || c == '_'
            if (!isValid) {
                return false
            }
            // Check for consecutive dots
            if (c == '.' && i > 0 && domain[i - 1] == '.') {
                return false
            }
        }

        return true
    }
}
