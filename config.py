"""
Configuration module for metrics calculation.
Allows injection of custom cost rates and other configurable parameters.
"""

class MetricsConfig:
    """Configuration class for metrics calculation parameters."""

    def __init__(
        self,
        price_per_acu: float = 0.05,
        currency: str = "USD",
        working_hours_per_day: int = 8,
        working_days_per_month: int = 22
    ):
        """
        Initialize metrics configuration.

        Args:
            price_per_acu: Cost per ACU in the specified currency
            currency: Currency code (e.g., 'USD', 'EUR')
            working_hours_per_day: Standard working hours per day
            working_days_per_month: Standard working days per month
        """
        self.price_per_acu = price_per_acu
        self.currency = currency
        self.working_hours_per_day = working_hours_per_day
        self.working_days_per_month = working_days_per_month

    def to_dict(self):
        """Convert configuration to dictionary."""
        return {
            'price_per_acu': self.price_per_acu,
            'currency': self.currency,
            'working_hours_per_day': self.working_hours_per_day,
            'working_days_per_month': self.working_days_per_month
        }
