"""User model - Python equivalent of User.java.

This module demonstrates a simple data model with properties,
validation, and Python-specific features.
"""

from typing import Optional, Any
from dataclasses import dataclass, field
from datetime import datetime
import re


class BaseEntity:
    """Base entity class with common fields."""
    
    def __init__(self):
        self.created_at: Optional[datetime] = None
        self.updated_at: Optional[datetime] = None
    
    def update_timestamp(self) -> None:
        """Update the modification timestamp (private in Java)."""
        self.updated_at = datetime.now()


@dataclass
class User(BaseEntity):
    """User entity with validation and Python-specific features.
    
    This class demonstrates various Python patterns including
    properties, validation, and special methods.
    """
    
    # Deprecated constant
    OLD_DEFAULT_ROLE: str = field(default="guest", init=False)
    """@deprecated Use UserRole enum instead."""
    
    # Instance fields
    id: str
    name: str
    email: str
    role: str = "user"
    age: Optional[int] = None
    _internal_id: Optional[str] = None  # Protected field
    __secret: str = field(default="", init=False)  # Private field
    
    def __post_init__(self):
        """Initialize base entity after dataclass init."""
        super().__init__()
        self.created_at = datetime.now()
        if not self._internal_id:
            self._internal_id = f"internal_{self.id}"
    
    # Java-style getters
    def get_id(self) -> str:
        """Get user ID."""
        return self.id
    
    def get_name(self) -> str:
        """Get user name."""
        return self.name
    
    def get_email(self) -> str:
        """Get user email."""
        return self.email
    
    def get_age(self) -> Optional[int]:
        """Get user age."""
        return self.age
    
    # Java-style setters
    def set_id(self, id: str) -> None:
        """Set user ID."""
        self.id = id
        self._internal_id = f"internal_{id}"
    
    def set_name(self, name: str) -> None:
        """Set user name."""
        if not name:
            raise ValueError("Name cannot be empty")
        self.name = name
        self.update_timestamp()
    
    def set_email(self, email: str) -> None:
        """Set user email."""
        if not self._validate_email(email):
            raise ValueError("Invalid email format")
        self.email = email
        self.update_timestamp()
    
    def set_age(self, age: Optional[int]) -> None:
        """Set user age."""
        if age is not None and age < 0:
            raise ValueError("Age cannot be negative")
        self.age = age
    
    # Python properties
    @property
    def is_adult(self) -> bool:
        """Check if user is adult (18+)."""
        return self.age is not None and self.age >= 18
    
    @property
    def display_name(self) -> str:
        """Get display name (computed property)."""
        return f"{self.name} ({self.email})"
    
    @property
    def has_email(self) -> bool:
        """Check if user has valid email."""
        return bool(self.email and "@" in self.email)
    
    # Validation methods
    @staticmethod
    def _validate_email(email: str) -> bool:
        """Validate email format (protected static method)."""
        pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
        return bool(re.match(pattern, email))
    
    @deprecated
    def validate(self) -> bool:
        """Validate user data.
        
        @deprecated Use is_valid property instead
        """
        return self.is_valid
    
    @property 
    def is_valid(self) -> bool:
        """Check if user data is valid."""
        return bool(
            self.id and
            self.name and
            self._validate_email(self.email)
        )
    
    def notify_change(self, field: str, old_value: Any, new_value: Any) -> None:
        """Notify about field change (package-private in Java)."""
        print(f"Field {field} changed from {old_value} to {new_value}")
    
    # Special methods (Java equivalents)
    def __str__(self) -> str:
        """String representation (toString in Java).
        
        Returns:
            A string representation of the user
        """
        return f"User[id={self.id}, name={self.name}, email={self.email}]"
    
    def __repr__(self) -> str:
        """Developer representation."""
        return (f"User(id={self.id!r}, name={self.name!r}, "
                f"email={self.email!r}, role={self.role!r})")
    
    def __eq__(self, other: object) -> bool:
        """Equality comparison (equals in Java).
        
        Args:
            other: Object to compare with
            
        Returns:
            True if objects are equal
        """
        if not isinstance(other, User):
            return False
        return self.id == other.id
    
    def __hash__(self) -> int:
        """Hash code (hashCode in Java).
        
        Returns:
            Hash value for the user
        """
        return hash(self.id)
    
    def __lt__(self, other: 'User') -> bool:
        """Less than comparison (for sorting)."""
        return self.name < other.name
    
    # Python-specific methods
    def to_dict(self) -> dict:
        """Convert to dictionary."""
        return {
            'id': self.id,
            'name': self.name,
            'email': self.email,
            'role': self.role,
            'age': self.age
        }
    
    @classmethod
    def from_dict(cls, data: dict) -> 'User':
        """Create user from dictionary (class method)."""
        return cls(
            id=data['id'],
            name=data['name'],
            email=data['email'],
            role=data.get('role', 'user'),
            age=data.get('age')
        )
    
    def __enter__(self) -> 'User':
        """Context manager entry."""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        """Context manager exit."""
        if exc_type is None:
            self.update_timestamp()


# Module-level helper functions
def create_guest_user(name: str) -> User:
    """Create a guest user with minimal data."""
    return User(
        id=f"guest_{name.lower()}",
        name=name,
        email=f"{name.lower()}@guest.local",
        role="guest"
    )


def validate_user_data(user: User) -> tuple[bool, Optional[str]]:
    """Validate user data and return status with message.
    
    Args:
        user: User to validate
        
    Returns:
        Tuple of (is_valid, error_message)
    """
    if not user.id:
        return False, "User ID is required"
    if not user.name:
        return False, "User name is required"
    if not user.email:
        return False, "User email is required"
    if not User._validate_email(user.email):
        return False, "Invalid email format"
    return True, None


# Private module function
def _generate_user_id(name: str) -> str:
    """Generate user ID from name (private helper)."""
    return name.lower().replace(' ', '_')
