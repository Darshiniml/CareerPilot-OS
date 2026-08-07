from abc import ABC, abstractmethod

class JobSourceProvider(ABC):
    @abstractmethod
    def acquire(self, url_or_text: str) -> str:
        """Acquire raw job description text from target."""
        pass

class TextJobSourceProvider(JobSourceProvider):
    def acquire(self, url_or_text: str) -> str:
        return url_or_text

class WebsiteJobSourceProvider(JobSourceProvider):
    def acquire(self, url_or_text: str) -> str:
        return (
            "# [Overview]\n"
            "We are seeking a Senior Software Engineer to build Java and React web platforms.\n"
            "# [Responsibilities]\n"
            "- Coordinate development of scalable backend web systems.\n"
            "- Mentor junior engineers and perform architectural code reviews.\n"
            "# [Requirements]\n"
            "- BS in Computer Science.\n"
            "- Required Skills: Java (REQUIRED, Backend), React (PREFERRED, Frontend).\n"
            "- Required Experience: 5+ years.\n"
            "# [Benefits]\n"
            "- Hybrid remote policy and free lunch.\n"
        )

class ATSExportJobSourceProvider(JobSourceProvider):
    def acquire(self, url_or_text: str) -> str:
        return (
            "# [Overview]\n"
            "ATS Export - Position: Senior Software Engineer. Salary Range: $150,000 - $200,000.\n"
            "# [Requirements]\n"
            "Required Skills: Java (REQUIRED, Backend), Python (PREFERRED, AI/ML).\n"
        )

class JSONFeedJobSourceProvider(JobSourceProvider):
    def acquire(self, url_or_text: str) -> str:
        return (
            "# [Overview]\n"
            "JSON Feed Ingestion. Title: Java Architect. Company: Netflix.\n"
            "# [Requirements]\n"
            "Required Skills: Java (REQUIRED, Backend), AWS (REQUIRED, Cloud).\n"
        )
