"""Service for managing users - Python equivalent of UserService.java.

This module provides user management functionality with various Python-specific
features to test symbol extraction, reference finding, and hover info.
"""

from typing import Optional, Dict, List, Union, Protocol
from dataclasses import dataclass
from enum import Enum
from abc import ABC, abstractmethod
import logging
from deprecated import deprecated

# Module-level constants (equivalent to Java static final fields)
DEFAULT_ROLE = "user"

# Deprecated constant with docstring
MAX_USERS = 1000
"""@deprecated Use MAX_ACTIVE_USERS instead. Will be removed in v3.0."""

# Protected module constant (single underscore)
_CACHE_SIZE = 100

# Private module constant (double underscore)
__SECRET_KEY = "secret123"


class UserRole(Enum):
    """User role enumeration (equivalent to Java enum)."""
    ADMIN = "admin"
    USER = "user"
    GUEST = "guest"


class UserEvent(Enum):
    """Events that can occur in the user service."""
    USER_ADDED = "user_added"
    USER_REMOVED = "user_removed"
    USER_UPDATED = "user_updated"


class UserListener(Protocol):
    """Protocol for user event listeners (like Java interface)."""
    
    def on_user_event(self, event: UserEvent, user: 'User') -> None:
        """Handle user event."""
        ...


@dataclass
class User:
    """User data class (similar to Java record or class with getters/setters)."""
    id: str
    name: str
    email: str
    role: UserRole = UserRole.USER
    created_timestamp: Optional[int] = None
    
    def get_id(self) -> str:
        """Get user ID (Java-style getter)."""
        return self.id
    
    def set_name(self, name: str) -> None:
        """Set user name (Java-style setter)."""
        self.name = name
    
    @property
    def is_admin(self) -> bool:
        """Check if user is admin (property)."""
        return self.role == UserRole.ADMIN
    
    def __str__(self) -> str:
        """String representation (like Java toString)."""
        return f"User(id={self.id}, name={self.name})"
    
    def __eq__(self, other: object) -> bool:
        """Equality comparison (like Java equals)."""
        if not isinstance(other, User):
            return False
        return self.id == other.id
    
    def __hash__(self) -> int:
        """Hash code (like Java hashCode)."""
        return hash(self.id)


class BaseEntity(ABC):
    """Abstract base class (like Java abstract class)."""
    
    def __init__(self):
        self.created_at = None
        self.updated_at = None
    
    @abstractmethod
    def validate(self) -> bool:
        """Abstract validation method."""
        pass
    
    def update_timestamp(self) -> None:
        """Update the modification timestamp."""
        import time
        self.updated_at = time.time()


class UserService(BaseEntity):
    """Service for managing users.
    
    This service provides methods for user management including
    adding, finding, and removing users. It demonstrates various
    Python features for testing.
    
    @note This is the main service class
    @since 1.0.0
    """
    
    # Class variables (like Java static fields)
    service_name: str = "UserService"
    _instance: Optional['UserService'] = None
    
    def __init__(self, max_users: int = 1000):
        """Initialize the user service.
        
        Args:
            max_users: Maximum number of users allowed
        """
        super().__init__()
        self._users: Dict[str, User] = {}  # Protected field
        self.__listeners: List[UserListener] = []  # Private field
        self.max_users = max_users  # Public field
        self._logger = logging.getLogger(__name__)
    
    @classmethod
    def get_instance(cls) -> 'UserService':
        """Get singleton instance (like Java static method).
        
        Returns:
            The singleton UserService instance
        """
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance
    
    @staticmethod
    def is_valid_user(user: User) -> bool:
        """Static validation method.
        
        Args:
            user: User to validate
            
        Returns:
            True if user is valid
        """
        return bool(user.id and user.name and "@" in user.email)
    
    def add_user(self, user: User) -> bool:
        """Add a new user to the service.
        
        Args:
            user: User object to add
            
        Returns:
            True if user was added successfully
            
        Raises:
            ValueError: If user ID already exists
        """
        if user.id in self._users:
            return False
        if len(self._users) >= self.max_users:
            raise ValueError("Maximum users reached")
        self._users[user.id] = user
        self._notify_listeners(UserEvent.USER_ADDED, user)
        return True
    
    def find_user(self, user_id: str) -> Optional[User]:
        """Find a user by ID.
        
        Args:
            user_id: The user ID to search for
            
        Returns:
            User object if found, None otherwise
        """
        return self._users.get(user_id)
    
    @deprecated(version='2.0', reason='Use find_user instead')
    def get_user(self, user_id: str) -> Optional[User]:
        """Legacy method to get user.
        
        @deprecated Use find_user() instead
        """
        return self.find_user(user_id)
    
    async def find_user_async(self, user_id: str) -> Optional[User]:
        """Async method to find user.
        
        Args:
            user_id: The user ID to search for
            
        Returns:
            User object if found
        """
        # Simulate async operation
        import asyncio
        await asyncio.sleep(0.1)
        return self._users.get(user_id)
    
    def remove_user(self, user_id: str) -> bool:
        """Remove a user by ID.
        
        Args:
            user_id: The user ID to remove
            
        Returns:
            True if user was removed
        """
        if user_id in self._users:
            user = self._users[user_id]
            del self._users[user_id]
            self._notify_listeners(UserEvent.USER_REMOVED, user)
            return True
        return False
    
    @property
    def user_count(self) -> int:
        """Get the number of users (property getter)."""
        return len(self._users)
    
    @user_count.setter
    def user_count(self, value: int) -> None:
        """Property setter (should not be used)."""
        raise AttributeError("Cannot set user_count directly")
    
    def get_all_users(self) -> List[User]:
        """Get all users.
        
        Returns:
            List of all users
        """
        return list(self._users.values())
    
    def find_users_by_role(self, role: UserRole) -> List[User]:
        """Find users by role.
        
        Args:
            role: The role to filter by
            
        Returns:
            List of users with the specified role
        """
        return [u for u in self._users.values() if u.role == role]
    
    def add_listener(self, listener: UserListener) -> None:
        """Add an event listener (package-private in Java).
        
        Args:
            listener: The listener to add
        """
        self.__listeners.append(listener)
    
    def validate(self) -> bool:
        """Validate the service state (implements abstract method).
        
        Returns:
            True if service is valid
        """
        return self.max_users > 0
    
    def _notify_listeners(self, event: UserEvent, user: User) -> None:
        """Notify all listeners of an event (protected method).
        
        Args:
            event: The event that occurred
            user: The user involved in the event
        """
        for listener in self.__listeners:
            try:
                listener.on_user_event(event, user)
            except Exception as e:
                self._logger.error(f"Error notifying listener: {e}")
    
    def __validate_user(self, user: User) -> bool:
        """Private validation method.
        
        Args:
            user: User to validate
            
        Returns:
            True if user is valid
        """
        return self.is_valid_user(user) and user.role != UserRole.GUEST
    
    def __str__(self) -> str:
        """String representation."""
        return f"UserService(users={self.user_count})"
    
    def __repr__(self) -> str:
        """Developer representation."""
        return f"UserService(max_users={self.max_users}, users={self.user_count})"
    
    # Generator method
    def iter_users(self):
        """Iterate over all users (generator)."""
        for user in self._users.values():
            yield user
    
    # Async generator
    async def iter_users_async(self):
        """Async iteration over users."""
        for user in self._users.values():
            await asyncio.sleep(0.01)  # Simulate async work
            yield user
    
    class UserSession:
        """Nested class for user sessions (like Java inner class)."""
        
        def __init__(self, user: User, token: str):
            """Initialize a user session.
            
            Args:
                user: The user for this session
                token: Session token
            """
            self.user = user
            self.token = token
            self._created_at = None
        
        def is_valid(self) -> bool:
            """Check if session is valid.
            
            Returns:
                True if session has a valid token
            """
            return bool(self.token)
        
        def __str__(self) -> str:
            """String representation."""
            return f"Session(user={self.user.id}, valid={self.is_valid()})"


# Module-level functions (no Java equivalent - tests module-level code)
def create_admin_user(name: str, email: str) -> User:
    """Factory function to create an admin user.
    
    Args:
        name: User's name
        email: User's email
        
    Returns:
        A new User with admin role
    """
    return User(
        id=f"admin_{name.lower().replace(' ', '_')}",
        name=name,
        email=email,
        role=UserRole.ADMIN
    )


async def validate_user_async(user: User) -> bool:
    """Async validation of user.
    
    Args:
        user: User to validate
        
    Returns:
        True if user is valid
    """
    import asyncio
    await asyncio.sleep(0.1)
    return UserService.is_valid_user(user)


def _create_test_user() -> User:
    """Protected helper function."""
    return User("test", "Test User", "test@example.com")


def __internal_helper(data: Dict) -> str:
    """Private helper function."""
    return str(data)
