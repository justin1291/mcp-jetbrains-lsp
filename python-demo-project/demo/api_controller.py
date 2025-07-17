"""API Controller - Python equivalent of ApiController.java.

This module demonstrates REST API patterns, nested classes,
and various Python web service patterns.
"""

from typing import Dict, Any, Optional, List, Union, TypedDict, Literal
from dataclasses import dataclass, asdict
from enum import Enum
import json
from datetime import datetime
from abc import ABC, abstractmethod

# Constants
STATUS_SUCCESS = "success"
STATUS_ERROR = "error"
API_VERSION = "1.0"


class HttpMethod(Enum):
    """HTTP methods enumeration."""
    GET = "GET"
    POST = "POST"
    PUT = "PUT"
    DELETE = "DELETE"
    PATCH = "PATCH"


class ResponseStatus(Enum):
    """Response status codes."""
    OK = 200
    CREATED = 201
    BAD_REQUEST = 400
    NOT_FOUND = 404
    INTERNAL_ERROR = 500


@dataclass
class ApiResponse:
    """API response wrapper (nested class in Java).
    
    This is a standardized response format for all API endpoints.
    """
    status: str
    message: str
    data: Optional[Any] = None
    timestamp: str = ""
    
    def __post_init__(self):
        """Initialize timestamp if not provided."""
        if not self.timestamp:
            self.timestamp = datetime.utcnow().isoformat()
    
    def to_json(self) -> str:
        """Convert response to JSON."""
        return json.dumps(asdict(self))
    
    @classmethod
    def success(cls, message: str, data: Any = None) -> 'ApiResponse':
        """Create success response."""
        return cls(STATUS_SUCCESS, message, data)
    
    @classmethod
    def error(cls, message: str, data: Any = None) -> 'ApiResponse':
        """Create error response."""
        return cls(STATUS_ERROR, message, data)


# TypedDict for request validation
class CreateUserRequest(TypedDict):
    """Type definition for create user request."""
    name: str
    email: str
    role: Optional[str]


class UpdateUserRequest(TypedDict, total=False):
    """Type definition for update user request."""
    name: str
    email: str
    role: str
    age: int


# Protocol for dependency injection
class UserServiceProtocol(ABC):
    """Protocol defining user service interface."""
    
    @abstractmethod
    def add_user(self, user: Any) -> bool:
        """Add a user."""
        pass
    
    @abstractmethod
    def find_user(self, user_id: str) -> Optional[Any]:
        """Find a user."""
        pass
    
    @abstractmethod
    def remove_user(self, user_id: str) -> bool:
        """Remove a user."""
        pass


class ApiController:
    """REST API controller for user management.
    
    This controller handles HTTP requests and delegates to the user service.
    Demonstrates decorator patterns and API documentation.
    """
    
    def __init__(self, user_service: UserServiceProtocol):
        """Initialize controller with user service.
        
        Args:
            user_service: The user service implementation
        """
        self.user_service = user_service
        self._request_count = 0  # Protected counter
        self.__api_key = "secret"  # Private API key
    
    # Decorator for logging (simulated)
    @staticmethod
    def log_request(func):
        """Decorator to log API requests."""
        def wrapper(self, *args, **kwargs):
            self._request_count += 1
            print(f"API request: {func.__name__}")
            return func(self, *args, **kwargs)
        return wrapper
    
    @log_request
    def create_user(self, request: CreateUserRequest) -> ApiResponse:
        """Create a new user.
        
        POST /api/users
        
        Args:
            request: User creation request data
            
        Returns:
            ApiResponse with created user data
        """
        try:
            # Validate request
            if not request.get('name') or not request.get('email'):
                return ApiResponse.error("Name and email are required")
            
            # Create user (simplified)
            from .user import User
            user = User(
                id=self._generate_user_id(request['name']),
                name=request['name'],
                email=request['email'],
                role=request.get('role', 'user')
            )
            
            if self.user_service.add_user(user):
                return ApiResponse.success("User created", user.to_dict())
            else:
                return ApiResponse.error("User already exists")
                
        except Exception as e:
            return ApiResponse.error(f"Internal error: {str(e)}")
    
    @log_request
    def get_user(self, user_id: str) -> ApiResponse:
        """Get user by ID.
        
        GET /api/users/{user_id}
        
        Args:
            user_id: User ID to retrieve
            
        Returns:
            ApiResponse with user data
        """
        user = self.user_service.find_user(user_id)
        if user:
            return ApiResponse.success("User found", user.to_dict())
        else:
            return ApiResponse.error("User not found")
    
    @log_request
    def update_user(self, user_id: str, request: UpdateUserRequest) -> ApiResponse:
        """Update existing user.
        
        PUT /api/users/{user_id}
        
        Args:
            user_id: User ID to update
            request: Update request data
            
        Returns:
            ApiResponse with updated user data
        """
        user = self.user_service.find_user(user_id)
        if not user:
            return ApiResponse.error("User not found")
        
        # Update fields
        if 'name' in request:
            user.set_name(request['name'])
        if 'email' in request:
            user.set_email(request['email'])
        if 'role' in request:
            user.role = request['role']
        if 'age' in request:
            user.set_age(request['age'])
        
        return ApiResponse.success("User updated", user.to_dict())
    
    @log_request
    def delete_user(self, user_id: str) -> ApiResponse:
        """Delete user by ID.
        
        DELETE /api/users/{user_id}
        
        Args:
            user_id: User ID to delete
            
        Returns:
            ApiResponse with deletion status
        """
        if self.user_service.remove_user(user_id):
            return ApiResponse.success("User deleted")
        else:
            return ApiResponse.error("User not found")
    
    def list_users(self, 
                   role: Optional[str] = None,
                   limit: int = 100,
                   offset: int = 0) -> ApiResponse:
        """List users with optional filtering.
        
        GET /api/users?role={role}&limit={limit}&offset={offset}
        
        Args:
            role: Optional role filter
            limit: Maximum number of results
            offset: Pagination offset
            
        Returns:
            ApiResponse with user list
        """
        # Simplified implementation
        users = self.user_service.get_all_users()
        
        # Filter by role if specified
        if role:
            users = [u for u in users if u.role == role]
        
        # Apply pagination
        paginated = users[offset:offset + limit]
        
        return ApiResponse.success(
            f"Found {len(paginated)} users",
            {
                'users': [u.to_dict() for u in paginated],
                'total': len(users),
                'limit': limit,
                'offset': offset
            }
        )
    
    @property
    def request_count(self) -> int:
        """Get total request count (property)."""
        return self._request_count
    
    @staticmethod
    def validate_api_key(api_key: str) -> bool:
        """Validate API key (static method).
        
        Args:
            api_key: API key to validate
            
        Returns:
            True if valid
        """
        return api_key == "valid-api-key"
    
    def _generate_user_id(self, name: str) -> str:
        """Generate user ID from name (protected method).
        
        Args:
            name: User name
            
        Returns:
            Generated user ID
        """
        import uuid
        return f"user_{uuid.uuid4().hex[:8]}"
    
    def __validate_request(self, request: Dict) -> bool:
        """Validate request data (private method).
        
        Args:
            request: Request dictionary
            
        Returns:
            True if valid
        """
        return bool(request and isinstance(request, dict))
    
    # Nested class for request context
    class RequestContext:
        """Context for API requests (nested class)."""
        
        def __init__(self, method: HttpMethod, path: str, headers: Dict[str, str]):
            """Initialize request context.
            
            Args:
                method: HTTP method
                path: Request path
                headers: Request headers
            """
            self.method = method
            self.path = path
            self.headers = headers
            self.timestamp = datetime.utcnow()
        
        @property
        def is_authenticated(self) -> bool:
            """Check if request is authenticated."""
            return 'Authorization' in self.headers
        
        def get_header(self, name: str) -> Optional[str]:
            """Get header value by name."""
            return self.headers.get(name)


# Module-level helper functions
def create_error_response(message: str, status_code: ResponseStatus) -> Dict[str, Any]:
    """Create standardized error response.
    
    Args:
        message: Error message
        status_code: HTTP status code
        
    Returns:
        Error response dictionary
    """
    return {
        'status': STATUS_ERROR,
        'message': message,
        'code': status_code.value,
        'timestamp': datetime.utcnow().isoformat()
    }


def parse_pagination_params(query_params: Dict[str, str]) -> tuple[int, int]:
    """Parse pagination parameters from query string.
    
    Args:
        query_params: Query parameters dictionary
        
    Returns:
        Tuple of (limit, offset)
    """
    limit = int(query_params.get('limit', '100'))
    offset = int(query_params.get('offset', '0'))
    
    # Validate bounds
    limit = min(max(1, limit), 1000)  # Between 1 and 1000
    offset = max(0, offset)
    
    return limit, offset


# Async version for modern Python web frameworks
async def async_health_check() -> Dict[str, Any]:
    """Async health check endpoint.
    
    Returns:
        Health status dictionary
    """
    import asyncio
    await asyncio.sleep(0.1)  # Simulate async work
    
    return {
        'status': 'healthy',
        'version': API_VERSION,
        'timestamp': datetime.utcnow().isoformat()
    }
