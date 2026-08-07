from abc import ABC, abstractmethod
from typing import List

class CompanySourceProvider(ABC):
    @abstractmethod
    def acquire(self, url: str) -> str:
        """Acquire raw textual data from URL source."""
        pass

class WebsiteCompanySourceProvider(CompanySourceProvider):
    def acquire(self, url: str) -> str:
        return (
            "# [About Company]\n"
            f"Welcome to Google. We are a global technology leader. Our legal entity name is Google LLC. "
            "We were founded in 1998. We have offices worldwide, including headquarters in Mountain View, California, "
            "and other large office hubs in New York, London, and Tokyo. We operate as a public ownership company under Alphabet Inc.\n"
        )

class CareersCompanySourceProvider(CompanySourceProvider):
    def acquire(self, url: str) -> str:
        return (
            "# [Careers]\n"
            "We are hiring software engineers, product managers, and UI designers. "
            "We have open roles in Sunnyvale, New York, Zurich, and Hyderabad. "
            "We support hybrid work with 3 days in the office. We offer internships and graduate hiring programs. "
            "Key departments include Cloud Platform, YouTube Core, and Google Search Engineering.\n"
        )

class AboutCompanySourceProvider(CompanySourceProvider):
    def acquire(self, url: str) -> str:
        return (
            "# [Products & Services]\n"
            "Our primary products include Google Search, Google Cloud Platform, YouTube, and Google Workspace. "
            "We offer enterprise services, consulting, and cloud storage hosting.\n"
        )

class EngineeringBlogCompanySourceProvider(CompanySourceProvider):
    def acquire(self, url: str) -> str:
        return (
            "# [Technology Stack]\n"
            "Our backend stack is predominantly Java, Go, and Python. We run heavily on Kubernetes and Docker. "
            "Our data repositories run on Cloud Spanner, PostgreSQL, and Bigtable. "
            "Our frontend features React and TypeScript. We make extensive use of Tensorflow, PyTorch, "
            "and OpenAI models for our AI systems. For CI/CD, we utilize Jenkins and GitHub Actions.\n"
        )

class PublicDocCompanySourceProvider(CompanySourceProvider):
    def acquire(self, url: str) -> str:
        return (
            "# [Engineering Culture]\n"
            "We promote DevOps automation, high code quality, and continuous deployment. "
            "We run cloud-native architectures. Benefits include health insurance, free meals, and gym access.\n"
        )
