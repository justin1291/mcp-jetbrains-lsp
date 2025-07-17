/**
 * String utility functions
 * Pure JavaScript implementation for testing JS language features
 */

/**
 * Capitalizes the first letter of a string
 * @param {string} str - The string to capitalize
 * @returns {string} The capitalized string
 */
export function capitalize(str) {
    if (!str || typeof str !== 'string') {
        return '';
    }
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

/**
 * Converts a string to camelCase
 * @param {string} str - The string to convert
 * @returns {string} The camelCase string
 */
export const toCamelCase = (str) => {
    return str
        .replace(/(?:^\w|[A-Z]|\b\w)/g, (word, index) => {
            return index === 0 ? word.toLowerCase() : word.toUpperCase();
        })
        .replace(/\s+/g, '');
};

/**
 * Converts a string to kebab-case
 * @param {string} str - The string to convert
 * @returns {string} The kebab-case string
 */
export const toKebabCase = (str) => {
    return str
        .replace(/([a-z])([A-Z])/g, '$1-$2')
        .replace(/[\s_]+/g, '-')
        .toLowerCase();
};

/**
 * Truncates a string to a specified length
 * @param {string} str - The string to truncate
 * @param {number} maxLength - Maximum length
 * @param {string} suffix - Suffix to add (default: '...')
 * @returns {string} The truncated string
 */
export function truncate(str, maxLength, suffix = '...') {
    if (!str || str.length <= maxLength) {
        return str;
    }
    return str.substring(0, maxLength - suffix.length) + suffix;
}

/**
 * Checks if a string is a valid email
 * @param {string} email - The email to validate
 * @returns {boolean} True if valid email
 */
export const isValidEmail = (email) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
};

/**
 * Generates a random string
 * @param {number} length - Length of the string
 * @param {string} charset - Character set to use
 * @returns {string} Random string
 */
export function generateRandomString(length = 10, charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789') {
    let result = '';
    for (let i = 0; i < length; i++) {
        result += charset.charAt(Math.floor(Math.random() * charset.length));
    }
    return result;
}

/**
 * String formatting utility class
 */
export class StringFormatter {
    constructor(options = {}) {
        this.options = {
            trim: true,
            lowercase: false,
            uppercase: false,
            ...options
        };
    }

    /**
     * Formats a string according to the configured options
     * @param {string} str - String to format
     * @returns {string} Formatted string
     */
    format(str) {
        if (!str) return '';
        
        let result = str;
        
        if (this.options.trim) {
            result = result.trim();
        }
        
        if (this.options.lowercase) {
            result = result.toLowerCase();
        } else if (this.options.uppercase) {
            result = result.toUpperCase();
        }
        
        return result;
    }

    /**
     * Updates the formatter options
     * @param {Object} newOptions - New options to merge
     */
    updateOptions(newOptions) {
        this.options = { ...this.options, ...newOptions };
    }
}

/**
 * Template string processor
 * @param {string} template - Template string with placeholders
 * @param {Object} values - Values to substitute
 * @returns {string} Processed string
 */
export const processTemplate = (template, values) => {
    return template.replace(/\{(\w+)\}/g, (match, key) => {
        return values.hasOwnProperty(key) ? values[key] : match;
    });
};

/**
 * String constants
 */
export const STRING_CONSTANTS = {
    EMPTY: '',
    SPACE: ' ',
    NEWLINE: '\n',
    TAB: '\t',
    ELLIPSIS: '...',
    DASH: '-',
    UNDERSCORE: '_'
};

/**
 * Regular expressions for common patterns
 */
export const REGEX_PATTERNS = {
    EMAIL: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    PHONE: /^\+?[\d\s\-\(\)]+$/,
    URL: /^https?:\/\/.+$/,
    NUMBER: /^\d+$/,
    ALPHANUMERIC: /^[a-zA-Z0-9]+$/,
    WHITESPACE: /\s+/g
};

// Default formatter instance
export const defaultFormatter = new StringFormatter();

/**
 * Utility functions for array of strings
 */
export const StringArrayUtils = {
    /**
     * Joins array of strings with proper formatting
     * @param {string[]} strings - Array of strings
     * @param {string} separator - Separator (default: ', ')
     * @param {string} lastSeparator - Last separator (default: ' and ')
     * @returns {string} Joined string
     */
    joinWithAnd(strings, separator = ', ', lastSeparator = ' and ') {
        if (!strings || strings.length === 0) return '';
        if (strings.length === 1) return strings[0];
        if (strings.length === 2) return strings.join(lastSeparator);
        
        const lastItem = strings.pop();
        return strings.join(separator) + lastSeparator + lastItem;
    },

    /**
     * Filters strings by length
     * @param {string[]} strings - Array of strings
     * @param {number} minLength - Minimum length
     * @param {number} maxLength - Maximum length
     * @returns {string[]} Filtered strings
     */
    filterByLength(strings, minLength = 0, maxLength = Infinity) {
        return strings.filter(str => 
            str.length >= minLength && str.length <= maxLength
        );
    },

    /**
     * Removes duplicates from string array
     * @param {string[]} strings - Array of strings
     * @param {boolean} caseSensitive - Whether comparison is case sensitive
     * @returns {string[]} Array without duplicates
     */
    removeDuplicates(strings, caseSensitive = true) {
        if (!caseSensitive) {
            const seen = new Set();
            return strings.filter(str => {
                const lowerStr = str.toLowerCase();
                if (seen.has(lowerStr)) {
                    return false;
                }
                seen.add(lowerStr);
                return true;
            });
        }
        return [...new Set(strings)];
    }
};
