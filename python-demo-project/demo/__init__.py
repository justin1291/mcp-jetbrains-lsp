"""Demo package for testing MCP Language Service Plugin.

This package contains Python code that mirrors the Java demo code,
providing equivalent functionality for testing symbol extraction,
reference finding, and hover information across languages.
"""

__version__ = "1.0.0"
__all__ = [
    "UserService",
    "User", 
    "ApiController",
    "DataProcessor",
]

# Package-level imports for convenience
from .user_service import UserService, create_admin_user
from .user import User
from .api_controller import ApiController
from .data_processor import DataProcessor
