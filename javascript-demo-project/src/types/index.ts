/**
 * Shared type definitions for the application
 */

import { MathOperation } from '../utils/mathUtils';

/**
 * Base interface for all entities
 */
export interface BaseEntity {
    id: string;
    createdAt: Date;
    updatedAt: Date;
}

/**
 * User information
 */
export interface User extends BaseEntity {
    email: string;
    firstName: string;
    lastName: string;
    isActive: boolean;
    preferences?: UserPreferences;
}

/**
 * User preferences configuration
 */
export interface UserPreferences {
    theme: 'light' | 'dark' | 'auto';
    language: string;
    notifications: NotificationSettings;
    calculator: CalculatorPreferences;
}

/**
 * Notification settings
 */
export interface NotificationSettings {
    email: boolean;
    push: boolean;
    sms: boolean;
    frequency: 'immediate' | 'daily' | 'weekly' | 'monthly';
}

/**
 * Calculator-specific preferences
 */
export interface CalculatorPreferences {
    defaultOperation: MathOperation;
    precision: number;
    showHistory: boolean;
    autoSave: boolean;
    scientificNotation: boolean;
}

/**
 * API response wrapper
 */
export interface ApiResponse<T> {
    data: T;
    success: boolean;
    message?: string;
    errors?: string[];
    timestamp: Date;
    requestId: string;
}

/**
 * Pagination information
 */
export interface PaginationInfo {
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
}

/**
 * Paginated response
 */
export interface PaginatedResponse<T> extends ApiResponse<T[]> {
    pagination: PaginationInfo;
}

/**
 * Application state
 */
export interface AppState {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    error: string | null;
    preferences: UserPreferences | null;
}

/**
 * Action types for state management
 */
export enum ActionType {
    SET_USER = 'SET_USER',
    SET_LOADING = 'SET_LOADING',
    SET_ERROR = 'SET_ERROR',
    CLEAR_ERROR = 'CLEAR_ERROR',
    UPDATE_PREFERENCES = 'UPDATE_PREFERENCES',
    LOGOUT = 'LOGOUT'
}

/**
 * Action interface for state management
 */
export interface Action<T = any> {
    type: ActionType;
    payload?: T;
}

/**
 * Event handler types
 */
export type EventHandler<T = Event> = (event: T) => void;
export type AsyncEventHandler<T = Event> = (event: T) => Promise<void>;

/**
 * Generic function types
 */
export type Predicate<T> = (item: T) => boolean;
export type Mapper<T, U> = (item: T) => U;
export type Reducer<T, U> = (accumulator: U, current: T) => U;

/**
 * Utility types
 */
export type Optional<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;
export type RequiredFields<T, K extends keyof T> = T & Required<Pick<T, K>>;

/**
 * Database entity status
 */
export enum EntityStatus {
    ACTIVE = 'active',
    INACTIVE = 'inactive',
    PENDING = 'pending',
    ARCHIVED = 'archived',
    DELETED = 'deleted'
}

/**
 * Log levels for application logging
 */
export enum LogLevel {
    DEBUG = 'debug',
    INFO = 'info',
    WARN = 'warn',
    ERROR = 'error',
    FATAL = 'fatal'
}

/**
 * Configuration for the application
 */
export interface AppConfig {
    apiUrl: string;
    apiKey: string;
    environment: 'development' | 'staging' | 'production';
    features: FeatureFlags;
    logging: LoggingConfig;
}

/**
 * Feature flags configuration
 */
export interface FeatureFlags {
    enableAdvancedCalculator: boolean;
    enableUserProfiles: boolean;
    enableNotifications: boolean;
    enableAnalytics: boolean;
    enableBetaFeatures: boolean;
}

/**
 * Logging configuration
 */
export interface LoggingConfig {
    level: LogLevel;
    enableConsole: boolean;
    enableRemote: boolean;
    remoteEndpoint?: string;
}

/**
 * HTTP methods
 */
export enum HttpMethod {
    GET = 'GET',
    POST = 'POST',
    PUT = 'PUT',
    PATCH = 'PATCH',
    DELETE = 'DELETE'
}

/**
 * API request configuration
 */
export interface ApiRequestConfig {
    method: HttpMethod;
    url: string;
    headers?: Record<string, string>;
    params?: Record<string, any>;
    data?: any;
    timeout?: number;
    retries?: number;
}

/**
 * Validation result
 */
export interface ValidationResult {
    isValid: boolean;
    errors: ValidationError[];
}

/**
 * Validation error
 */
export interface ValidationError {
    field: string;
    message: string;
    code: string;
}

/**
 * Form field definition
 */
export interface FormField<T = any> {
    name: string;
    label: string;
    type: 'text' | 'number' | 'email' | 'password' | 'select' | 'checkbox' | 'textarea';
    value: T;
    required: boolean;
    placeholder?: string;
    options?: Array<{ label: string; value: any }>;
    validation?: Predicate<T>;
    errorMessage?: string;
}

/**
 * Custom error class
 */
export class AppError extends Error {
    constructor(
        message: string,
        public code: string,
        public statusCode: number = 500,
        public details?: any
    ) {
        super(message);
        this.name = 'AppError';
        Object.setPrototypeOf(this, AppError.prototype);
    }
}

// Type guards
export const isUser = (obj: any): obj is User => {
    return obj && typeof obj.email === 'string' && typeof obj.firstName === 'string';
};

export const isApiResponse = <T>(obj: any): obj is ApiResponse<T> => {
    return obj && typeof obj.success === 'boolean' && obj.data !== undefined;
};

// Default values
export const DEFAULT_USER_PREFERENCES: UserPreferences = {
    theme: 'auto',
    language: 'en',
    notifications: {
        email: true,
        push: true,
        sms: false,
        frequency: 'daily'
    },
    calculator: {
        defaultOperation: MathOperation.ADD,
        precision: 2,
        showHistory: true,
        autoSave: false,
        scientificNotation: false
    }
};

export const DEFAULT_PAGINATION: PaginationInfo = {
    page: 1,
    pageSize: 10,
    totalItems: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false
};
